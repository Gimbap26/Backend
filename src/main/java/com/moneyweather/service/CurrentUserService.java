package com.moneyweather.service;

import com.moneyweather.domain.Enums.UserStatus;
import com.moneyweather.domain.entity.UserEntity;
import com.moneyweather.repository.UserRepository;
import com.moneyweather.security.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** 요청을 보낸 사용자를 찾고, 리소스가 그 사용자 것인지 확인한다. */
@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    public UserEntity require() {
        Long userId = UserContext.currentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        UserEntity user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is inactive.");
        }
        return user;
    }

    public Long requireId() {
        return require().getId();
    }

    public void assertOwner(Long ownerUserId, Long currentUserId) {
        if (!Objects.equals(ownerUserId, currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Resource owner does not match current user.");
        }
    }
}
