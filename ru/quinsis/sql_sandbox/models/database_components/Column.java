package ru.quinsis.sql_sandbox.models.database_components;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Column {
    private String tableName;
    private String name;
    private String dataType;
    private String keyType;
    private String nullType;
    private List<Object> rows;
}
