package kr.boothrock.api.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import kr.boothrock.api.auth.dto.AccountResponse;
import kr.boothrock.api.auth.dto.SignupRequest;
import kr.boothrock.api.auth.dto.SignupResponse;
import kr.boothrock.api.auth.entity.AccountEntity;
import kr.boothrock.api.auth.entity.LocalCredentialEntity;
import kr.boothrock.api.auth.repository.AccountRepository;
import kr.boothrock.api.auth.repository.LocalCredentialRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder encoder;

    public AccountService(AccountRepository accounts, LocalCredentialRepository credentials,
            PasswordEncoder encoder) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public boolean isLoginIdAvailable(String loginId) {
        if (loginId == null || !loginId.matches("[a-z0-9_]{4,30}")) {
            throw new AuthInputException("VALIDATION_ERROR", "Check the login ID format.");
        }
        return !credentials.existsByLoginId(loginId);
    }

    @Transactional
    public SignupResponse signUp(SignupRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuthInputException("VALIDATION_ERROR", "Password is too long.");
        }
        if (credentials.existsByLoginId(request.loginId())) {
            throw new AuthInputException("LOGIN_ID_TAKEN", "Login ID is already used.");
        }

        AccountEntity account = accounts.saveAndFlush(new AccountEntity(
                request.nickname().trim(), request.phoneNumber(), request.email().trim()));
        credentials.saveAndFlush(new LocalCredentialEntity(account.getId(), request.loginId(),
                encoder.encode(request.password())));

        return new SignupResponse(account.getId());
    }

    @Transactional(readOnly = true)
    public AccountResponse currentAccount(UUID accountId) {
        AccountEntity account = accounts.findById(accountId)
                .filter(value -> "ACTIVE".equals(value.getAccountStatus()))
                .orElseThrow(() -> new AuthInputException("ACCOUNT_UNAVAILABLE", "Account is unavailable."));
        return new AccountResponse(account.getId(), account.getNickname(),
                account.getPhoneNumber(), account.getEmail());
    }
}
