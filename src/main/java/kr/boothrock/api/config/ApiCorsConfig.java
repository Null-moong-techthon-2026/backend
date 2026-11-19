package kr.boothrock.api.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@Profile("(local | staging) & !deploy")
public class ApiCorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${boothrock.frontend.origin}") String primary,
            @Value("${boothrock.frontend.additional-origins:}") String additional) {
        var origins=new LinkedHashSet<String>();add(origins,primary);
        for(String origin:additional.split(","))if(!origin.isBlank())add(origins,origin);
        CorsConfiguration config=new CorsConfiguration();
        config.setAllowedOrigins(new ArrayList<>(origins));
        config.setAllowedMethods(java.util.List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("Accept","Content-Type","X-CSRF-TOKEN"));
        config.setAllowCredentials(true);config.setMaxAge(3600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",config);return source;
    }
    private static void add(LinkedHashSet<String> origins,String value) {
        String origin=value.trim();
        try {
            URI uri=URI.create(origin);
            if(!(uri.getScheme().equals("http")||uri.getScheme().equals("https"))||uri.getHost()==null||uri.getHost().contains("*")
                ||uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null
                ||!uri.getRawPath().isEmpty()||uri.getPort()==0||uri.getPort()>65535)throw new IllegalArgumentException();
        }catch(RuntimeException e){throw new IllegalArgumentException("Frontend origins must be explicit http(s) origins without path or wildcard");}
        origins.add(origin);
    }
}
