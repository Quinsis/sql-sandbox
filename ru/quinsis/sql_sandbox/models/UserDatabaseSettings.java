package ru.quinsis.sql_sandbox.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class UserDatabaseSettings {
    private User owner;
    private List<User> stakeholders;
    private Map<String, Boolean> stakeholderFavoriteDatabases;
    private Map<String, Boolean> stakeholderSessionIsActive;
    private Map<String, List<String>> userDatabasePermission;
}
