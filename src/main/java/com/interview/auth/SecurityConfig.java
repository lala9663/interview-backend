package com.interview.auth;

import java.util.Arrays;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.interview.internal.InternalKeyFilter;

import lombok.RequiredArgsConstructor;

/**
 * 보안 설정.
 * - /api/v1/** : JWT (health, auth/** 는 열려 있음)
 * - /internal/v1/** : X-Internal-Key
 * - ADMIN 전용 엔드포인트는 컨트롤러의 @PreAuthorize("hasRole('ADMIN')") 로 표시한다.
 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppSecurityProperties props;
    private final JwtTokenService tokenService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .cors().and()
                .formLogin().disable()
                .httpBasic().disable()
                .logout().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, e) -> {
                    Object error = request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR);
                    if ("TOKEN_EXPIRED".equals(error)) {
                        SecurityErrorWriter.write(response, 401, "TOKEN_EXPIRED", "토큰이 만료되었습니다.");
                    } else {
                        SecurityErrorWriter.write(response, 401, "UNAUTHORIZED", "로그인이 필요합니다.");
                    }
                })
                .accessDeniedHandler((request, response, e) ->
                        SecurityErrorWriter.write(response, 403, "FORBIDDEN", "권한이 없습니다."))
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .antMatchers("/api/v1/health", "/api/v1/auth/**", "/error").permitAll()
                .antMatchers("/internal/v1/**").hasRole("INTERNAL")
                .antMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll();
        http.addFilterBefore(new InternalKeyFilter(props.getInternalKey()), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(new JwtAuthenticationFilter(tokenService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.getCorsAllowedOrigins());
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setExposedHeaders(Arrays.asList("Content-Disposition"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
