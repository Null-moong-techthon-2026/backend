package kr.boothrock.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Environment environment,
            SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfRepository,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled) throws Exception {
        boolean local = environment.acceptsProfiles(Profiles.of("local & !deploy"));
        if (local) http.cors(Customizer.withDefaults());
        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers(HttpMethod.GET,
                    "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness",
                    "/api/system/status").permitAll();
            if (local) {
                authorize.requestMatchers(HttpMethod.GET, "/api/auth/csrf",
                        "/api/auth/login-id-availability", "/api/dev/auth-data").permitAll();
                authorize.requestMatchers(HttpMethod.POST, "/api/auth/login",
                        "/api/auth/sign-up").permitAll();
                authorize.requestMatchers(HttpMethod.DELETE, "/api/dev/auth-data").permitAll();
                authorize.requestMatchers(HttpMethod.GET, "/api/me").authenticated();
                authorize.requestMatchers(HttpMethod.POST, "/api/auth/logout").authenticated();
                authorize.requestMatchers(HttpMethod.GET, "/api/public/**", "/api/media-assets/*/content").permitAll();
                authorize.requestMatchers(HttpMethod.POST, "/api/application-invitations/resolve").permitAll();
                authorize.requestMatchers("/api/events/**", "/api/organizations/**", "/api/me/**").authenticated();
                authorize.requestMatchers(HttpMethod.POST, "/api/dev/organizations").authenticated();
            }
            if (docsEnabled) {
                authorize.requestMatchers(HttpMethod.GET,
                        "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                        "/swagger-ui.html", "/swagger-ui/**").permitAll();
            }
            // Open business routes only after their authentication and ownership checks exist.
            authorize.anyRequest().denyAll();
        });
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository));
        http.securityContext(context -> context.securityContextRepository(contextRepository));
        http.formLogin(login -> login.disable());
        http.httpBasic(basic -> basic.disable());
        http.logout(logout -> logout.disable());
        http.requestCache(cache -> cache.disable());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }
}
