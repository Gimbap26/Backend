package com.moneyweather.repository;

import com.moneyweather.domain.Enums.TransactionType;
import com.moneyweather.domain.entity.TransactionEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 거래 검색 조건. 목록 조회(페이지)와 합계 계산이 같은 조건을 쓰도록 한 곳에 둔다. */
public final class TransactionSpecs {
    private TransactionSpecs() {
    }

    /** null 인 조건은 적용하지 않는다. 키워드는 상호명에 대소문자 구분 없이 포함되는지 본다. */
    public static Specification<TransactionEntity> search(Long userId, LocalDate from, LocalDate to,
                                                         TransactionType type, Long categoryId, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("userId"), userId));
            where.add(cb.between(root.get("transactionDate"), from, to));
            if (type != null) where.add(cb.equal(root.get("transactionType"), type));
            if (categoryId != null) where.add(cb.equal(root.get("categoryId"), categoryId));
            if (keyword != null && !keyword.isBlank()) {
                where.add(cb.like(cb.lower(root.get("merchant")), "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** 사용자가 입력한 %, _ 가 와일드카드로 해석되지 않도록 한다. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
