package kr.boothrock.api.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.auth.dto.AccountResponse;
import kr.boothrock.api.auth.dto.LoginRequest;
import kr.boothrock.api.auth.dto.LoginResponse;
import kr.boothrock.api.auth.dto.SignupRequest;
import kr.boothrock.api.auth.dto.SignupResponse;
import kr.boothrock.api.auth.service.AccountService;
import kr.boothrock.api.auth.service.SessionService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Profile("local & !deploy")
@RequestMapping("/api")
@Tag(name = "인증")
public class AuthController {
    private final AccountService accounts;
    private final SessionService sessions;

    public AuthController(AccountService accounts, SessionService sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @GetMapping("/auth/csrf")
    @Operation(summary = "CSRF 토큰 조회", description = "POST/DELETE 요청의 X-CSRF-TOKEN 입력란에 반환된 token을 넣으세요. 로그인 이후에는 다시 조회합니다.")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/auth/login-id-availability")
    @Operation(summary = "아이디 중복 확인")
    public AvailabilityResponse loginIdAvailability(
            @Parameter(example = "festival_admin") @RequestParam String loginId) {
        return new AvailabilityResponse(accounts.isLoginIdAvailable(loginId));
    }

    @PostMapping("/auth/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/auth/csrf 응답의 token")
    @Operation(summary = "회원가입", description = "개인 계정만 생성합니다. 가입 유형은 화면 선택용이며 조직 생성·소속·관리 권한을 부여하지 않습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = SignupRequest.class),
                            examples = {
                                @ExampleObject(name = "주최자 가입", value = """
                                    {
                                      "onboardingType": "ORGANIZER",
                                      "loginId": "festival_admin",
                                      "password": "DevOnly!2026",
                                      "nickname": "축제 운영자",
                                      "phoneNumber": "01000000001",
                                      "email": "organizer.demo@example.com"
                                    }
                                    """),
                                @ExampleObject(name = "부스 운영자 가입", value = """
                                    {
                                      "onboardingType": "OPERATOR",
                                      "loginId": "tteokbokki_owner",
                                      "password": "DevOnly!2026",
                                      "nickname": "달빛 떡볶이",
                                      "phoneNumber": "01000000002",
                                      "email": "operator.test@example.com"
                                    }
                                    """)
                            })))
    public SignupResponse signUp(@Valid @RequestBody SignupRequest body) {
        return accounts.signUp(body);
    }

    @PostMapping("/auth/login")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /api/auth/csrf 응답의 token")
    @Operation(summary = "로그인", description = "먼저 회원가입 예시로 계정을 만든 후 같은 아이디와 비밀번호로 로그인합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = LoginRequest.class),
                            examples = {
                                @ExampleObject(name = "주최자 로그인", value = """
                                    {
                                      "loginId": "festival_admin",
                                      "password": "DevOnly!2026"
                                    }
                                    """),
                                @ExampleObject(name = "부스 운영자 로그인", value = """
                                    {
                                      "loginId": "tteokbokki_owner",
                                      "password": "DevOnly!2026"
                                    }
                                    """)
                            })))
    public LoginResponse login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        return sessions.login(body, request, response);
    }

    @GetMapping("/me")
    @Operation(summary = "로그인한 내 정보 조회")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return accounts.currentAccount(principal.getAccountId());
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "로그아웃")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "로그인 이후 GET /api/auth/csrf에서 새로 받은 token")
    public void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        sessions.logout(request, response, authentication);
    }

    public record CsrfResponse(String headerName, String token) {}
    public record AvailabilityResponse(boolean available) {}
}
