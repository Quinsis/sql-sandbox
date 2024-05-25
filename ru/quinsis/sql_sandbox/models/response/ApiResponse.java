package ru.quinsis.sql_sandbox.models.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class ApiResponse<T> {
    private HttpStatus status;
    private T data;
    private LocalDateTime timestamp;
}
