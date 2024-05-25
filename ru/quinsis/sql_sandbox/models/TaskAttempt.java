package ru.quinsis.sql_sandbox.models;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TaskAttempt {
    private String query;
    private LocalDateTime date;
}
