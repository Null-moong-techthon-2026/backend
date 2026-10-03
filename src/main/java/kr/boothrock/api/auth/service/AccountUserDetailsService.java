package kr.boothrock.api.auth.service;

import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.auth.entity.AccountEntity;
import kr.boothrock.api.auth.entity.LocalCredentialEntity;
import kr.boothrock.api.auth.repository.AccountRepository;
import kr.boothrock.api.auth.repository.LocalCredentialRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {
    private final LocalCredentialRepository credentials;
    private final AccountRepository accounts;

    public AccountUserDetailsService(LocalCredentialRepository credentials, AccountRepository accounts) {
        this.credentials = credentials;
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String loginId) {
        LocalCredentialEntity credential = credentials.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        AccountEntity account = accounts.findById(credential.getAccountId())
                .filter(value -> "ACTIVE".equals(value.getAccountStatus()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return new AccountPrincipal(account.getId(), credential.getLoginId(),
                credential.getPasswordHash(), account.getNickname());
    }
}
