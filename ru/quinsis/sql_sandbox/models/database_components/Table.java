package ru.quinsis.sql_sandbox.models.database_components;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Table {
    private String name;
    private List<Column> columns;
}
