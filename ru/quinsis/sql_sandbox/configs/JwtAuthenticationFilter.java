package ru.quinsis.sql_sandbox.configs;

import ru.quinsis.sql_sandbox.models.User;
import ru.quinsis.sql_sandbox.repositories.UserRepository;
import ru.quinsis.sql_sandbox.services.implementations.UserDetailsServiceImpl;
import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final UserRepository userRepository;

    @Bean
    public UserDetailsServiceImpl userDetailsService() {
        return new UserDetailsServiceImpl(userRepository);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);
            if (StringUtils.hasText(jwt) && validateToken(jwt)) {
                String id = getIdFromJWT(jwt);
                Optional<User> userOptional = userRepository.findById(id);
                if (userOptional.isPresent()) {
                    UserDetails userDetails = userDetailsService().loadUserByUsername(userOptional.get().getLogin());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    clearExpiredTokenCookie(request, response);
                }
            } else {
                clearExpiredTokenCookie(request, response);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private void clearExpiredTokenCookie(HttpServletRequest request, HttpServletResponse response) {
        Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(cookie -> cookie.getName().equals("access_token"))
                .filter(cookie -> !validateToken(cookie.getValue()))
                .forEach(cookie -> {
                    cookie.setValue("");
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    response.addCookie(cookie);
                });
    }

    public static boolean validateToken(String authToken) {
        try {
            Jwts.parser().setSigningKey(JwtTokenUtil.getSecret().getBytes()).parseClaimsJws(authToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getIdFromJWT(String token) {
        Jws<Claims> claimsJws = Jwts.parser()
                .setSigningKey(JwtTokenUtil.getSecret().getBytes())
                .parseClaimsJws(token);

        return claimsJws.getBody().getSubject();
    }

    public static String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

}
