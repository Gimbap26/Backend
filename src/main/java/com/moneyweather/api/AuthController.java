package com.moneyweather.api;

import com.moneyweather.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "회원가입과 로그인. 발급받은 accessToken을 Authorization: Bearer <토큰> 헤더로 보내면 나머지 API를 쓸 수 있습니다.")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일과 8자 이상의 비밀번호로 가입하고 바로 로그인 토큰을 받습니다. 이미 가입된 이메일이면 409를 반환합니다.")
    public Map<String, Object> signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password(), request.name());
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일과 비밀번호가 맞으면 Bearer 토큰을 발급합니다. 틀리면 무엇이 틀렸는지 구분하지 않고 401을 반환합니다. "
            + "같은 이메일로 15분 안에 5번, 같은 IP에서 20번 넘게 실패하면 잠시 429를 반환합니다.")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.email(), request.password(), http.getRemoteAddr());
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "이 계정으로 발급된 모든 토큰을 무효로 만듭니다(모든 기기에서 로그아웃). 로그인이 필요합니다.")
    public Map<String, Object> logout() {
        return authService.logout();
    }

    @PatchMapping("/password")
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 변경합니다. 다른 기기의 기존 토큰은 무효가 되고, 지금 기기에서 쓸 새 토큰을 돌려줍니다.")
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return authService.changePassword(request.currentPassword(), request.newPassword());
    }

    public record SignupRequest(@NotBlank @Email String email,
                                @NotBlank @Size(min = 8, max = 100) String password,
                                @NotBlank @Size(max = 50) String name) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {}
}
