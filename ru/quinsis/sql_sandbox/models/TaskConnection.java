package ru.quinsis.sql_sandbox.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TaskConnection {
    private User user;
    private List<TaskAttempt> attempts;
    private List<TaskComment> comments;
}
