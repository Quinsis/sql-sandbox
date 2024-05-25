package ru.quinsis.sql_sandbox.models;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class TaskComment {
    private User user;
    private String comment;
    private Date date;
}
