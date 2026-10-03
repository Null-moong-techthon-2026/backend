package kr.boothrock.api.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import kr.boothrock.api.auth.AccountPrincipal;
import kr.boothrock.api.auth.dto.LoginRequest;
import kr.boothrock.api.auth.dto.LoginResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final LogoutHandler logoutHandler;

    public SessionService(AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository, CsrfTokenRepository csrfRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.sessionStrategy = new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfRepository)));
        var securityLogout = new SecurityContextLogoutHandler();
        securityLogout.setSecurityContextRepository(contextRepository);
        this.logoutHandler = new CompositeLogoutHandler(new CsrfLogoutHandler(csrfRepository),
                securityLogout, new CookieClearingLogoutHandler("JSESSIONID"));
    }

    public LoginResponse login(LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.loginId(), body.password()));
        sessionStrategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        return new LoginResponse(principal.getAccountId(), principal.getNickname());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
    }
}
