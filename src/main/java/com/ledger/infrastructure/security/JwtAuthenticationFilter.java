package com.ledger.infrastructure.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwtService, RestAuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.startsWith("Bearer ")) {
            entryPoint.commence(request, response, new org.springframework.security.authentication.BadCredentialsException("Bearer token required"));
            return;
        }
        try {
            String subject = jwtService.subject(header.substring(7));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    subject, null, List.of(new SimpleGrantedAuthority("ROLE_API")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new org.springframework.security.authentication.BadCredentialsException("Invalid token"));
        }
    }
}
