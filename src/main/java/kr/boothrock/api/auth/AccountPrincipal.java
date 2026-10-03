package kr.boothrock.api.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public final class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;

    private final UUID accountId;
    private final String nickname;

    public AccountPrincipal(UUID accountId, String loginId, String passwordHash, String nickname) {
        super(loginId, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.accountId = accountId;
        this.nickname = nickname;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getNickname() {
        return nickname;
    }
}
