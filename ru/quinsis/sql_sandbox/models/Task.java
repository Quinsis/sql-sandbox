package ru.quinsis.sql_sandbox.models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "tasks")
@Getter
@Setter
public class Task {
    @Id
    private String id;
    @Field(name = "owner")
    private User owner;
    @Field(name = "database")
    private Database database;
    @Field(name = "title")
    private String title;
    @Field(name = "description")
    private String description;
    @Field(name = "code")
    private String code;
    @Field(name = "connections")
    private List<TaskConnection> connections;
}
