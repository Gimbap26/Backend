package com.moneyweather.service;

import com.moneyweather.domain.Enums.TransactionType;
import com.moneyweather.domain.entity.CardEntity;
import com.moneyweather.domain.entity.CategoryEntity;
import com.moneyweather.domain.entity.TransactionEntity;
import com.moneyweather.repository.CardRepository;
import com.moneyweather.repository.CategoryRepository;
import com.moneyweather.repository.TransactionRepository;
import com.moneyweather.repository.TransactionSpecs;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/** 거래 검색·등록·수정·삭제. 계좌 거래의 잔액 반영은 {@link LedgerService}가 맡는다. */
@Service
@Transactional
public class TransactionService {
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final CardRepository cards;
    private final LedgerService ledger;
    private final CurrentUserService currentUser;
    private final EntityManager entityManager;

    public TransactionService(TransactionRepository transactions, CategoryRepository categories, CardRepository cards,
                              LedgerService ledger, CurrentUserService currentUser, EntityManager entityManager) {
        this.transactions = transactions;
        this.categories = categories;
        this.cards = cards;
        this.ledger = ledger;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
    }

    public record TransactionView(Long transactionId, LocalDate date, String merchant, long amount, TransactionType transactionType, String category,
                                  Long categoryId, Long cardId, Long accountId, Long transferAccountId) {}
    public record TransactionMutation(LocalDate transactionDate, String merchant, long amount, TransactionType transactionType,
                                      Long categoryId, Long cardId, Long accountId, Long transferAccountId) {}
    public record TransactionPatch(LocalDate transactionDate, String merchant, Long amount, TransactionType transactionType,
                                   Long categoryId, Long cardId, Long accountId, Long transferAccountId) {}

    /**
     * 거래 검색. 필터와 페이징을 DB 에서 처리해 요청한 페이지만 읽는다. 최신 거래가 먼저 온다.
     * {@code totalAmount}는 현재 페이지가 아니라 조건에 맞는 전체 거래의 합계다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> search(YearMonth month, TransactionType type, Long categoryId, String keyword, int page, int size) {
        Long userId = currentUser.requireId();
        Specification<TransactionEntity> spec = TransactionSpecs.search(userId, month.atDay(1), month.atEndOfMonth(), type, categoryId, keyword);
        Page<TransactionEntity> found = transactions.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id"))));
        Map<Long, CategoryEntity> categoryMap = categories.mapByIdForUser(userId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("month", month.toString());
        body.put("totalAmount", sumAmount(spec));
        body.put("items", found.getContent().stream().map(t -> toView(t, categoryMap)).toList());
        body.put("page", page);
        body.put("size", size);
        body.put("totalElements", found.getTotalElements());
        body.put("totalPages", found.getTotalPages());
        return body;
    }

    private long sumAmount(Specification<TransactionEntity> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<TransactionEntity> root = query.from(TransactionEntity.class);
        query.select(cb.coalesce(cb.sum(root.<Long>get("amount")), 0L)).where(spec.toPredicate(root, query, cb));
        return entityManager.createQuery(query).getSingleResult();
    }

    /** 거래를 기록하고, 계좌 거래라면 즉시 잔액에 반영한다. 카드 거래는 결제일 청구 때 반영된다. */
    public TransactionView create(TransactionMutation request) {
        Long userId = currentUser.requireId();
        ledger.validatePaymentSource(request.transactionType(), request.accountId(), request.cardId(), request.transferAccountId());
        assertReferencesOwned(userId, request.categoryId(), request.cardId(), request.accountId(), request.transferAccountId());
        TransactionEntity saved = transactions.save(new TransactionEntity(userId, request.transactionDate(), request.merchant(),
                request.amount(), request.transactionType(), request.categoryId(), request.cardId(), request.accountId(), request.transferAccountId()));
        ledger.apply(saved);
        return toView(saved, categories.mapByIdForUser(userId));
    }

    /**
     * 부분 수정. 결제 수단은 계좌와 카드 중 하나이므로 한쪽을 지정하면 다른 쪽은 자동으로 비운다.
     * 이체가 아닌 유형으로 바꾸면 받는 계좌도 비운다. 잔액은 이전 효과를 되돌린 뒤 새 효과를 적용한다.
     */
    public TransactionView update(long transactionId, TransactionPatch request) {
        Long userId = currentUser.requireId();
        TransactionEntity tx = requireOwned(userId, transactionId);

        TransactionType type = request.transactionType() != null ? request.transactionType() : tx.getTransactionType();
        Long cardId = tx.getCardId();
        Long accountId = tx.getAccountId();
        if (request.cardId() != null) { cardId = request.cardId(); accountId = null; }
        if (request.accountId() != null) { accountId = request.accountId(); cardId = null; }
        Long transferAccountId = request.transferAccountId() != null ? request.transferAccountId() : tx.getTransferAccountId();
        if (type != TransactionType.TRANSFER) transferAccountId = null;
        Long categoryId = request.categoryId() != null ? request.categoryId() : tx.getCategoryId();

        ledger.validatePaymentSource(type, accountId, cardId, transferAccountId);
        assertReferencesOwned(userId, categoryId, cardId, accountId, transferAccountId);

        ledger.revert(tx);
        tx.replace(
                request.transactionDate() != null ? request.transactionDate() : tx.getTransactionDate(),
                request.merchant() != null ? request.merchant() : tx.getMerchant(),
                request.amount() != null ? request.amount() : tx.getAmount(),
                type, categoryId, cardId, accountId, transferAccountId);
        ledger.apply(tx);
        return toView(tx, categories.mapByIdForUser(userId));
    }

    public Map<String, Object> delete(long transactionId) {
        TransactionEntity tx = requireOwned(currentUser.requireId(), transactionId);
        ledger.revert(tx);
        transactions.delete(tx);
        return Map.of("transactionId", transactionId, "deletedAt", LocalDateTime.now());
    }

    public Map<String, Object> changeCategory(long transactionId, long categoryId) {
        Long userId = currentUser.requireId();
        TransactionEntity tx = requireOwned(userId, transactionId);
        CategoryEntity category = requireOwnedCategory(userId, categoryId);
        tx.changeCategory(categoryId);
        return Map.of("transactionId", transactionId, "categoryId", categoryId, "categoryName", category.getName(), "updatedAt", LocalDateTime.now());
    }

    private TransactionEntity requireOwned(Long userId, long transactionId) {
        TransactionEntity tx = transactions.findById(transactionId).orElseThrow(() -> Errors.notFound("Transaction not found."));
        currentUser.assertOwner(tx.getUserId(), userId);
        return tx;
    }

    private CategoryEntity requireOwnedCategory(Long userId, long categoryId) {
        CategoryEntity category = categories.findById(categoryId).orElseThrow(() -> Errors.notFound("Category not found."));
        currentUser.assertOwner(category.getUserId(), userId);
        return category;
    }

    private void assertReferencesOwned(Long userId, Long categoryId, Long cardId, Long accountId, Long transferAccountId) {
        if (categoryId != null) requireOwnedCategory(userId, categoryId);
        if (cardId != null) {
            CardEntity card = cards.findById(cardId).orElseThrow(() -> Errors.notFound("Card not found."));
            currentUser.assertOwner(card.getUserId(), userId);
        }
        if (accountId != null) ledger.requireOwnedAccount(userId, accountId);
        if (transferAccountId != null) ledger.requireOwnedAccount(userId, transferAccountId);
    }

    private TransactionView toView(TransactionEntity tx, Map<Long, CategoryEntity> categoryMap) {
        return new TransactionView(tx.getId(), tx.getTransactionDate(), tx.getMerchant(), tx.getAmount(), tx.getTransactionType(),
                CategoryRepository.nameOf(categoryMap, tx.getCategoryId()),
                tx.getCategoryId(), tx.getCardId(), tx.getAccountId(), tx.getTransferAccountId());
    }
}
