package ru.quinsis.sql_sandbox.services;

import ru.quinsis.sql_sandbox.models.Database;
import ru.quinsis.sql_sandbox.models.Task;
import ru.quinsis.sql_sandbox.models.User;
import net.minidev.json.JSONArray;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DatabaseService {
    Optional<List<Database>> findAllByStakeholder(User stakeholder);
    Optional<Database> findByCode(String code);
    Database create(Database database);
    void delete(Database database);
    @Transactional
    boolean databaseExistsInPostgres(Database database);
    @Transactional
    void migrateToPostgres(Database database);
    void migrateToMongo(Database database);
    void unload(Database database);
    JSONArray queryDatabase(Database database, User user, String query);
    JSONArray queryTask(Database database, Task task, User user, String query);
}
