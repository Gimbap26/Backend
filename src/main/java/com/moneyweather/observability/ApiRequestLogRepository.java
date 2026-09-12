package com.moneyweather.observability;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLogEntity, Long> {
}
