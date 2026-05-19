package com.educate.assistant.config;

import com.educate.assistant.common.JwtUtil;
import com.educate.assistant.common.JwtBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtBlacklistService jwtBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 从请求头中获取token
        String token = getTokenFromRequest(request);
        
        if (StringUtils.hasText(token)) {
            try {
                // 检查黑名单
                if (jwtBlacklistService.isBlacklisted(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                // 验证token
                if (jwtUtil.validateToken(token)) {
                    // 获取用户名
                    String username = jwtUtil.getUsernameFromToken(token);
                    
                    // 获取用户角色
                    String role = null;
                    try {
                        role = jwtUtil.getRoleFromToken(token);
                    } catch (Exception e) {
                        // 如果token中没有角色信息，使用默认角色
                        role = "USER";
                    }
                    
                    // 设置权限列表
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                    if (StringUtils.hasText(role)) {
                        // Spring Security 要求角色以 ROLE_ 开头
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    }
                    
                    // 设置Spring Security上下文
                    UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // Token验证失败，继续执行后续过滤器
                logger.warn("JWT token validation failed: " + e.getMessage());
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        // Check Authorization header first
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        // Fallback: check query parameter (for iframe requests)
        String queryToken = request.getParameter("token");
        if (StringUtils.hasText(queryToken)) {
            return queryToken;
        }
        return null;
    }
}
