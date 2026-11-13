package com.interview.auth;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

/**
 * /api/v1/** 요청의 Authorization: Bearer 토큰을 검증해 SecurityContext 에 {@link CurrentUser} 를 넣는다.
 * 토큰이 만료·위조면 요청 속성(ATTR_ERROR)만 남기고, 실제 401 응답은 SecurityConfig 의 entry point 가 쓴다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_ERROR = "auth.error";

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                CurrentUser user = tokenService.parseAccessToken(header.substring(7).trim());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (ExpiredJwtException e) {
                request.setAttribute(ATTR_ERROR, "TOKEN_EXPIRED");
            } catch (JwtException e) {
                request.setAttribute(ATTR_ERROR, "UNAUTHORIZED");
            } catch (IllegalArgumentException e) {
                request.setAttribute(ATTR_ERROR, "UNAUTHORIZED");
            }
        }
        chain.doFilter(request, response);
    }
}
