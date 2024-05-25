package ru.quinsis.sql_sandbox.models;

import ru.quinsis.sql_sandbox.models.database_components.Column;
import ru.quinsis.sql_sandbox.models.database_components.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Set;

@Document(collection = "databases")
@Getter
@Setter
public class Database {
    @Id
    private String id;
    @Field(name = "code")
    private String code;
    @Field(name = "name")
    private String name;
    @Field(name = "userDatabaseSettings")
    private UserDatabaseSettings userDatabaseSettings;
    @Field(name = "tables")
    private List<Table> tables;
    @Field(name = "keys")
    private Set<Key> keys;

    @Getter
    @Setter
    public static class Key {
        @Field(name = "column")
        private Column column;
        @Field(name = "referencedTable")
        private String referencedTable;
        @Field(name = "referencedColumn")
        private String referencedColumn;
    }
}
