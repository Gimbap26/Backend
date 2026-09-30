package com.moneyweather.domain;

import com.moneyweather.domain.Enums.CategoryType;
import com.moneyweather.domain.entity.CategoryEntity;

import java.util.List;

/** 가입하거나 시드를 만들 때 넣는 기본 카테고리. 기본 카테고리는 삭제할 수 없다. */
public final class DefaultCategories {
    private DefaultCategories() {
    }

    public static List<CategoryEntity> forUser(Long userId) {
        return List.of(
                new CategoryEntity(userId, "식비/카페", CategoryType.EXPENSE, true),
                new CategoryEntity(userId, "교통", CategoryType.EXPENSE, true),
                new CategoryEntity(userId, "급여", CategoryType.INCOME, true),
                new CategoryEntity(userId, "구독", CategoryType.EXPENSE, true)
        );
    }
}
