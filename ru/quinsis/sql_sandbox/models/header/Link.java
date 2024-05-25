package ru.quinsis.sql_sandbox.models.header;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "header_links")
@Getter
@Setter
public class Link {
    @Id
    private String id;
    @Field(name = "name")
    private String name;
    @Field(name = "idAttr")
    private String idAttr;
    @Field(name = "hint")
    private String hint;
    @Field(name = "context")
    private List<ContextItem> context;
}
