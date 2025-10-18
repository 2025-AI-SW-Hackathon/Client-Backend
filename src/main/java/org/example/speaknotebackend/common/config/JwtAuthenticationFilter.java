package org.example.speaknotebackend.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.example.speaknotebackend.utils.JwtService;
import org.example.speaknotebackend.domain.user.UserService;
import org.example.speaknotebackend.entity.User;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization 헤더의 JWT를 검증하고 SecurityContext에 인증 정보를 채우는 필터.
 * 세션을 사용하지 않는 Stateless 환경에서 작동합니다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserService userService;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService ) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.replaceFirst("^Bearer( )*", "");
            try {
                var claims = jwtService.parseClaims(token);
                Long userId = claims.get("userId", Long.class);

                // DB에서 최소 정보 로드
                User user = userService.findActiveById(userId);

                // 모든 사용자를 ROLE_USER로 간주 (임시)
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                var principal = new UserDetailsImpl(user.getId(), user.getEmail(), user.getName(), authorities);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

                System.out.println("✅ [JwtFilter] 인증 객체 등록 완료: " + principal.getUsername());
                System.out.println("🔓 [JwtFilter] 권한 목록: " + principal.getAuthorities());
            } catch (Exception ignored) {
                // 토큰 문제는 이후 EntryPoint/ExceptionTranslationFilter에서 처리
            }
        }

        filterChain.doFilter(request, response);
    }
}


