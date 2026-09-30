package com.moneyweather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 인증은 JWT 필터가 직접 처리하므로, Spring Security 가 기본으로 만드는 메모리 사용자(와 로그에 찍는 임시 비밀번호)는 끈다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MoneyWeatherApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MoneyWeatherApiApplication.class, args);
    }
}
