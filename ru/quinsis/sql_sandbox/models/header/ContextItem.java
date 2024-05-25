package ru.quinsis.sql_sandbox.models.header;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

@Setter
@Getter
public class ContextItem {
    @Field(name = "id")
    private String id;
    @Field(name = "name")
    private String name;
    @Field(name = "link")
    private Link link;
}
