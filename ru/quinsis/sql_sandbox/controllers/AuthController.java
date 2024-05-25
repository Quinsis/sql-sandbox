package ru.quinsis.sql_sandbox.controllers;

import ru.quinsis.sql_sandbox.configs.JwtTokenUtil;
import ru.quinsis.sql_sandbox.models.response.ApiResponse;
import ru.quinsis.sql_sandbox.models.User;
import ru.quinsis.sql_sandbox.services.UserService;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/signup")
    public ResponseEntity<Map<String, ApiResponse<Object>>> signup(@RequestBody User user) {
        String result = userService.create(user);
        if (!result.equals("Регистрация успешно завершена.")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.UNAUTHORIZED)
                    .data(result)
                    .timestamp(LocalDateTime.now())
                    .build()));
        }

        JSONObject data = new JSONObject();
        data.put("token", JwtTokenUtil.generateToken(userService.findByLogin(user.getLogin()).get().getId()));
        data.put("message", "Регистрация успешно завершена.");

        return ResponseEntity.ok(Map.of(
                "success", ApiResponse.builder()
                        .status(HttpStatus.OK)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, ApiResponse<Object>>> login(@RequestBody User user) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword()));
            JSONObject data = new JSONObject();
            data.put("token", JwtTokenUtil.generateToken(userService.findByLogin(user.getLogin()).get().getId()));
            return ResponseEntity.ok(Map.of(
                    "success", ApiResponse.builder()
                            .status(HttpStatus.OK)
                            .data(data)
                            .timestamp(LocalDateTime.now())
                            .build())
            );
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ApiResponse.builder()
                            .status(HttpStatus.UNAUTHORIZED)
                            .data("Неверный логин или пароль.")
                            .timestamp(LocalDateTime.now())
                            .build()));
        }
    }
}
