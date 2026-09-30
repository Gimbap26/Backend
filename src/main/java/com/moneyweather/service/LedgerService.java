package com.moneyweather.service;

import com.moneyweather.domain.Enums.Direction;
import com.moneyweather.domain.Enums.EventStatus;
import com.moneyweather.domain.Enums.TransactionType;
import com.moneyweather.domain.entity.AccountEntity;
import com.moneyweather.domain.entity.FinancialEventEntity;
import com.moneyweather.domain.entity.TransactionEntity;
import com.moneyweather.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 거래와 결제 완료 이벤트가 계좌 잔액에 미치는 영향을 한 곳에서 관리한다.
 *
 * <p>수정·삭제·상태 변경은 모두 "기존 효과를 되돌리고 → 값을 바꾸고 → 새 효과를 적용"하는
 * 같은 순서를 따른다. 효과는 항상 엔티티의 현재 값에서 계산하므로 따로 저장하지 않는다.
 */
@Service
public class LedgerService {
    private final AccountRepository accounts;

    public LedgerService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    // --- 거래 ---------------------------------------------------------------

    /**
     * 거래 한 건이 각 계좌에 주는 잔액 변화.
     * 카드 거래는 비어 있다 — 카드 대금은 결제일 청구 이벤트가 결제 완료될 때 계좌에서 빠진다.
     */
    public Map<Long, Long> effectOf(TransactionEntity tx) {
        Map<Long, Long> effect = new LinkedHashMap<>();
        if (tx.getCardId() != null || tx.getAccountId() == null) return effect;
        long amount = tx.getAmount();
        switch (tx.getTransactionType()) {
            case EXPENSE -> effect.merge(tx.getAccountId(), -amount, Long::sum);
            case INCOME -> effect.merge(tx.getAccountId(), amount, Long::sum);
            case TRANSFER -> {
                effect.merge(tx.getAccountId(), -amount, Long::sum);
                if (tx.getTransferAccountId() != null) {
                    effect.merge(tx.getTransferAccountId(), amount, Long::sum);
                }
            }
        }
        return effect;
    }

    public void apply(TransactionEntity tx) {
        effectOf(tx).forEach((accountId, delta) -> adjust(tx.getUserId(), accountId, delta));
    }

    public void revert(TransactionEntity tx) {
        effectOf(tx).forEach((accountId, delta) -> adjust(tx.getUserId(), accountId, -delta));
    }

    /**
     * 저장 전에 결제 수단 조합이 말이 되는지 확인한다.
     * 계좌와 카드는 둘 중 하나만, 이체는 보내는 계좌와 받는 계좌가 모두 있어야 하고 서로 달라야 한다.
     */
    public void validatePaymentSource(TransactionType type, Long accountId, Long cardId, Long transferAccountId) {
        if (accountId != null && cardId != null) {
            throw badRequest("계좌(accountId)와 카드(cardId)는 동시에 지정할 수 없습니다.");
        }
        if (type == TransactionType.TRANSFER) {
            if (cardId != null) throw badRequest("이체 거래에는 카드를 지정할 수 없습니다.");
            if (accountId == null || transferAccountId == null) {
                throw badRequest("이체 거래는 보내는 계좌(accountId)와 받는 계좌(transferAccountId)가 모두 필요합니다.");
            }
            if (Objects.equals(accountId, transferAccountId)) {
                throw badRequest("보내는 계좌와 받는 계좌가 같습니다.");
            }
        } else if (transferAccountId != null) {
            throw badRequest("받는 계좌(transferAccountId)는 이체 거래에만 지정할 수 있습니다.");
        }
    }

    // --- 예정 이벤트 --------------------------------------------------------

    /** 결제 완료된 이벤트만 잔액에 반영돼 있다. */
    public void apply(FinancialEventEntity event) {
        if (event.getStatus() != EventStatus.PAID) return;
        adjust(event.getUserId(), requirePaymentAccount(event), signed(event));
    }

    public void revert(FinancialEventEntity event) {
        if (event.getStatus() != EventStatus.PAID) return;
        adjust(event.getUserId(), requirePaymentAccount(event), -signed(event));
    }

    private long signed(FinancialEventEntity event) {
        return event.getDirection() == Direction.INFLOW ? event.getAmount() : -event.getAmount();
    }

    private Long requirePaymentAccount(FinancialEventEntity event) {
        if (event.getAccountId() == null) {
            throw badRequest("결제 완료 처리하려면 결제 계좌(accountId)를 지정하세요.");
        }
        return event.getAccountId();
    }

    // --- 공통 ---------------------------------------------------------------

    /** 다른 사용자의 계좌를 쓰지 못하도록 소유권을 확인한 뒤 계좌를 돌려준다. */
    public AccountEntity requireOwnedAccount(Long userId, Long accountId) {
        AccountEntity account = accounts.findById(accountId).orElseThrow(() -> Errors.notFound("Account not found."));
        if (!Objects.equals(account.getUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource owner does not match current user.");
        }
        return account;
    }

    private void adjust(Long userId, Long accountId, long delta) {
        if (delta == 0) return;
        requireOwnedAccount(userId, accountId).adjustBalance(delta);
    }

    private ResponseStatusException badRequest(String message) {
        return Errors.badRequest(message);
    }
}
