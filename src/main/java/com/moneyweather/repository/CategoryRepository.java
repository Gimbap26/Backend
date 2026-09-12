package com.moneyweather.repository;

import com.moneyweather.domain.Enums.CategoryType;
import com.moneyweather.domain.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
    List<CategoryEntity> findByUserId(Long userId);
    List<CategoryEntity> findByUserIdAndCategoryType(Long userId, CategoryType categoryType);
}
