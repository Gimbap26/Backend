package com.moneyweather.repository;

import com.moneyweather.domain.Enums.CategoryType;
import com.moneyweather.domain.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    List<CategoryEntity> findByUserId(Long userId);
    List<CategoryEntity> findByUserIdAndCategoryType(Long userId, CategoryType categoryType);
    boolean existsByUserIdAndName(Long userId, String name);

    /** 사용자의 카테고리를 ID 로 찾을 수 있게 모은다. 거래에 카테고리 이름을 붙일 때 쓴다. */
    default Map<Long, CategoryEntity> mapByIdForUser(Long userId) {
        return findByUserId(userId).stream().collect(Collectors.toMap(CategoryEntity::getId, Function.identity()));
    }

    /** 거래의 카테고리 이름. 카테고리가 없거나 지워졌으면 '미분류'. */
    static String nameOf(Map<Long, CategoryEntity> categories, Long categoryId) {
        return Optional.ofNullable(categories.get(categoryId)).map(CategoryEntity::getName).orElse("미분류");
    }
}
