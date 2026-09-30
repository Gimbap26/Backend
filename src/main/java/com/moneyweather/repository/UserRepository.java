package com.moneyweather.repository;

import com.moneyweather.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 가입자 ID. 시드 사용자와 겹치지 않도록 1000번부터 발급하는 시퀀스(V4)에서 받는다. */
    @Query(value = "SELECT nextval('users_id_seq')", nativeQuery = true)
    Long nextUserId();
}
