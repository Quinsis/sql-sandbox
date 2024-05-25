package ru.quinsis.sql_sandbox.services.implementations;

import ru.quinsis.sql_sandbox.models.Database;
import ru.quinsis.sql_sandbox.models.Task;
import ru.quinsis.sql_sandbox.models.User;
import ru.quinsis.sql_sandbox.models.database_components.Column;
import ru.quinsis.sql_sandbox.models.database_components.PostgreSQLTypeMap;
import ru.quinsis.sql_sandbox.models.database_components.Table;
import ru.quinsis.sql_sandbox.repositories.DatabaseRepository;
import ru.quinsis.sql_sandbox.services.DatabaseService;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONArray;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class DatabaseServiceImpl implements DatabaseService {
    private final DatabaseRepository databaseRepository;
    private final DataSource dataSource;
    private final Lock lockPool = new ReentrantLock();
    private final Map<String, Lock> clientLocks = new HashMap<>();

    @Override
    public Optional<List<Database>> findAllByStakeholder(User stakeholder) {
        return databaseRepository.findAllByUserDatabaseSettings_StakeholdersContaining(stakeholder);
    }

    @Override
    public Optional<Database> findByCode(String code) {
        return databaseRepository.findByCode(code);
    }

    @Override
    public Database create(Database database) {
        return databaseRepository.save(database);
    }

    @Override
    public void delete(Database database) {
        databaseRepository.delete(database);
    }

    @Override
    @Transactional
    public boolean databaseExistsInPostgres(Database database) {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = 'schema_" + database.getId() + "')");
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public void migrateToPostgres(@org.jetbrains.annotations.NotNull Database database) {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                String schemaName = "schema_" + database.getId();

                // Проверка существования схемы
                ResultSet schemaResultSet = connection.getMetaData().getSchemas(null, null);
                while (schemaResultSet.next()) {
                    if (schemaResultSet.getString(1).equals(schemaName)) {
                        return;
                    }
                }

                String createSchemaQuery = "CREATE SCHEMA IF NOT EXISTS " + schemaName;
                statement.executeUpdate(createSchemaQuery);
                statement.execute("SET search_path TO schema_" + database.getId());
                for (Table table : database.getTables()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS " + table.getName() + "();");
                    for (Column column : table.getColumns()) {
                        String query = "ALTER TABLE " + table.getName() + " ADD COLUMN IF NOT EXISTS " +
                                column.getName() + " " +
                                column.getDataType() + " " +
                                (column.getNullType().equals("not null") ? "not null" : "");
                        statement.executeUpdate(query);
                    }

                    // Добавление значений в таблицу
                    String insertQuery = "INSERT INTO " + table.getName() + " (";
                    for (int i = 0; i < table.getColumns().size(); i++) {
                        insertQuery += table.getColumns().get(i).getName();
                        if (i < table.getColumns().size() - 1) {
                            insertQuery += ", ";
                        }
                    }

                    insertQuery += ") VALUES ";

                    if (table.getColumns().size() != 0) {
                        if (table.getColumns().get(0).getRows().size() != 0) {
                            for (int i = 0; i < table.getColumns().get(0).getRows().size(); i++) {
                                insertQuery += "(";
                                for (int j = 0; j < table.getColumns().size(); j++) {
                                    insertQuery += "?";
                                    if (j < table.getColumns().size() - 1) {
                                        insertQuery += ", ";
                                    }
                                }
                                insertQuery += ")";
                                if (i < table.getColumns().get(0).getRows().size() - 1) {
                                    insertQuery += ", ";
                                }
                            }
                        }
                    }

                    PreparedStatement preparedStatement = connection.prepareStatement(insertQuery);

                    if (table.getColumns().size() != 0) {
                        if (table.getColumns().get(0).getRows().size() != 0) {
                            for (int i = 0; i < table.getColumns().get(0).getRows().size(); i++) {
                                for (int j = 0; j < table.getColumns().size(); j++) {
                                    String dataType = table.getColumns().get(j).getDataType();
                                    int index = (j + 1) + (i * table.getColumns().size());
                                    Object value = table.getColumns().get(j).getRows().get(i);

                                    if (PostgreSQLTypeMap.getSQLType(dataType).isPresent()) {
                                        PostgreSQLTypeMap.set(preparedStatement, index, value, dataType);
                                    } else {
                                        preparedStatement.setObject(index, value);
                                    }
                                }
                            }
                            preparedStatement.executeUpdate();
                        }
                    }
                }

                if (database.getKeys() != null) {
                    // Установка PK ключей
                    for (Database.Key key : database.getKeys()) {
                        if (key.getColumn().getTableName().equals(key.getReferencedTable())) {
                            statement.execute("ALTER TABLE " + key.getColumn().getTableName() +
                                    " ADD CONSTRAINT pk_" + key.getColumn().getTableName() + "_" + key.getColumn().getName() +
                                    " PRIMARY KEY (" + key.getColumn().getName() + ");");
                        }
                    }
                    // Установка FK ключей
                    for (Database.Key key : database.getKeys()) {
                        if (!key.getColumn().getTableName().equals(key.getReferencedTable())) {
                            statement.execute("ALTER TABLE " + key.getColumn().getTableName() +
                                    " ADD CONSTRAINT FK_" + key.getReferencedTable() + "_" + key.getColumn().getTableName() +
                                    "_" + key.getColumn().getName() + " FOREIGN KEY (" + key.getColumn().getName() + ") " +
                                    "REFERENCES " + key.getReferencedTable() + "(" + key.getReferencedColumn() + ");");
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public void migrateToMongo(Database database) {
        List<Table> tables = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET search_path TO schema_" + database.getId());
                try (ResultSet resultSet = statement.executeQuery("select table_name from information_schema.tables where table_schema = 'schema_" + database.getId() + "'")) {
                    while (resultSet.next()) {
                        Table newTable = new Table();
                        newTable.setName(resultSet.getString("table_name"));
                        newTable.setColumns(new ArrayList<>());

                        try (Statement statement1 = connection.createStatement()) {
                            try (ResultSet columnRows = statement1.executeQuery("SELECT" +
                                    "    column_name," +
                                    "    data_type," +
                                    "    is_nullable," +
                                    "    CASE" +
                                    "        WHEN column_name IN (" +
                                    "            SELECT kcu.column_name" +
                                    "            FROM information_schema.key_column_usage kcu" +
                                    "            JOIN information_schema.table_constraints tc" +
                                    "            ON kcu.constraint_name = tc.constraint_name" +
                                    "            WHERE kcu.table_schema = 'schema_" + database.getId() + "'" +
                                    "                AND kcu.table_name = '" + newTable.getName() + "'" +
                                    "                AND tc.constraint_type = 'PRIMARY KEY'" +
                                    "        ) THEN 'YES'" +
                                    "        ELSE 'NO'" +
                                    "    END AS is_primary_key " +
                                    "FROM information_schema.columns " +
                                    "WHERE table_schema = 'schema_" + database.getId() + "'" +
                                    "    AND table_name = '" + newTable.getName() + "'")) {
                                while (columnRows.next()) {
                                    Column column = new Column();
                                    column.setTableName(newTable.getName());
                                    column.setName(columnRows.getObject(1).toString());
                                    column.setDataType(columnRows.getObject(2).toString());
                                    column.setNullType(columnRows.getObject(3).toString().equals("YES") ? "null" : "not null");
                                    column.setRows(new ArrayList<>());
                                    column.setKeyType("");
                                    newTable.getColumns().add(column);
                                }
                            }
                        }

                        try (Statement statement1 = connection.createStatement()) {
                            try (ResultSet valueRow = statement1.executeQuery("select * from " + newTable.getName())) {
                                while (valueRow.next()) {
                                    for (int i = 1; i <= valueRow.getMetaData().getColumnCount(); i++) {
                                        for (Column column : newTable.getColumns()) {
                                            if (column.getName().equals(valueRow.getMetaData().getColumnName(i))) {
                                                column.getRows().add(valueRow.getObject(column.getName()));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        tables.add(newTable);
                    }
                }
            }
            try (Statement statement = connection.createStatement()) {
                Set<Database.Key> keys = new HashSet<>();
                String query = "SELECT DISTINCT \n" +
                        "    tc.table_name, \n" +
                        "    kcu.column_name, \n" +
                        "    tc.constraint_name,\n" +
                        "    tc.constraint_type,\n" +
                        "    rcu.table_name AS referenced_table_name,\n" +
                        "    rcu.column_name AS referenced_column_name\n" +
                        "FROM \n" +
                        "    information_schema.table_constraints AS tc\n" +
                        "    JOIN information_schema.key_column_usage AS kcu\n" +
                        "        ON tc.constraint_name = kcu.constraint_name\n" +
                        "    LEFT JOIN information_schema.constraint_column_usage AS rcu\n" +
                        "        ON tc.constraint_name = rcu.constraint_name\n" +
                        "WHERE \n" +
                        "    tc.table_schema = 'schema_" + database.getId() + "'\n" +
                        "    AND (tc.constraint_type = 'PRIMARY KEY' OR tc.constraint_type = 'FOREIGN KEY');\n";

                try (ResultSet keyRow = statement.executeQuery(query)) {
                    int i = 0;
                    while (keyRow.next()) {
                        String tableName = keyRow.getString("table_name");
                        String columnName = keyRow.getString("column_name");
                        String referencedTableName = keyRow.getString("referenced_table_name");
                        String referencedColumnName = keyRow.getString("referenced_column_name");

                        for (Table table : tables) {
                            if (table.getName().equals(tableName)) {
                                for (Column column : table.getColumns()) {
                                    if (column.getName().equals(columnName)) {
                                        if (referencedTableName.equals(table.getName())) {
                                            column.setKeyType("PK");
                                        } else {
                                            column.setKeyType("FK");
                                        }
                                        Database.Key key = new Database.Key();
                                        key.setColumn(column);
                                        key.setReferencedTable(keyRow.getString("referenced_table_name"));
                                        key.setReferencedColumn(keyRow.getString("referenced_column_name"));
                                        keys.add(key);
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                database.setKeys(keys);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        database.setTables(tables);
        databaseRepository.save(database);
    }

    @Override
    @Transactional
    public void unload(Database database) {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                String schemaName = "schema_" + database.getId();
                String dropSchemaQuery = "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE";
                statement.executeUpdate(dropSchemaQuery);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public JSONArray queryDatabase(Database database, User user, String query) {
        Lock lock = getClientLock(user.getId());
        if (lock.tryLock()) {
            try {
                migrateToPostgres(database);
                if (query.toLowerCase().startsWith("select")) {
                    query = "select json_agg(data) from (" + query + ") as data";
                }
                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement()) {
                        String schemaName = "schema_" + database.getId();
                        String roleName = "app_user";

                        // Изменить владельца схемы
                        statement.execute("ALTER SCHEMA " + schemaName + " OWNER TO " + roleName + ";");

                        // Предоставить привилегии USAGE и ALL на схему
                        statement.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + roleName + ";");
                        statement.execute("GRANT ALL ON SCHEMA " + schemaName + " TO " + roleName + ";");

                        try (Statement statement1 = connection.createStatement()) {
                            // Получить список таблиц в схеме
                            try (ResultSet tablesResult = statement1.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schemaName + "' AND table_type = 'BASE TABLE';")) {
                                // Сделать пользователя владельцем каждой таблицы
                                while (tablesResult.next()) {
                                    String tableName = tablesResult.getString("table_name");
                                    statement.execute("ALTER TABLE " + schemaName + "." + tableName + " OWNER TO " + roleName + ";");
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                JSONArray result = new JSONArray();
                try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "app_user", "SperfF3d3dskako!")) {
                    try (Statement userStatement = connection.createStatement()) {
                        userStatement.execute("SET search_path TO schema_" + database.getId());
                        try (ResultSet queryResult = userStatement.executeQuery(query)) {
                            while (queryResult.next()) {
                                result.add(queryResult.getString(1));
                            }
                        }
                    }
                } catch (SQLException e) {
                    result.add(e.getMessage());
                }

                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER SCHEMA schema_" + database.getId() + " OWNER TO postgres;");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                migrateToMongo(database);
                unload(database);
                return result;
            } finally {
                lock.unlock();
            }
        } else {
            JSONArray result = new JSONArray();
            result.add("Дождитесь выполнения предыдущего запроса.");
            return result;
        }
    }

    @Override
    @Transactional
    public JSONArray queryTask(Database database, Task task, User user, String query) {
        Lock lock = getClientLock(user.getId());
        if (lock.tryLock()) {
            try {
                migrateToPostgres(database);
                if (query.toLowerCase().startsWith("select")) {
                    query = "select json_agg(data) from (" + query + ") as data";
                }
                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement()) {
                        String schemaName = "schema_" + database.getId();
                        String roleName = "app_user";

                        // Изменить владельца схемы
                        statement.execute("ALTER SCHEMA " + schemaName + " OWNER TO " + roleName + ";");

                        // Предоставить привилегии USAGE и ALL на схему
                        statement.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + roleName + ";");
                        statement.execute("GRANT ALL ON SCHEMA " + schemaName + " TO " + roleName + ";");

                        try (Statement statement1 = connection.createStatement()) {
                            // Получить список таблиц в схеме
                            try (ResultSet tablesResult = statement1.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schemaName + "' AND table_type = 'BASE TABLE';")) {
                                // Сделать пользователя владельцем каждой таблицы
                                while (tablesResult.next()) {
                                    String tableName = tablesResult.getString("table_name");
                                    statement.execute("ALTER TABLE " + schemaName + "." + tableName + " OWNER TO " + roleName + ";");
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                JSONArray result = new JSONArray();
                try (Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "app_user", "SperfF3d3dskako!")) {
                    connection.setAutoCommit(false);
                    Savepoint savepoint = connection.setSavepoint();

                    try (Statement userStatement = connection.createStatement()) {
                        userStatement.execute("SET search_path TO schema_" + database.getId());
                        try (ResultSet queryResult = userStatement.executeQuery(query)) {
                            while (queryResult.next()) {
                                result.add(queryResult.getString(1));
                            }
                        }
                    }

                    connection.rollback(savepoint);
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    result.add(e.getMessage());
                }

                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER SCHEMA schema_" + database.getId() + " OWNER TO postgres;");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                unload(database);
                return result;
            } finally {
                lock.unlock();
            }
        } else {
            JSONArray result = new JSONArray();
            result.add("Дождитесь выполнения предыдущего запроса.");
            return result;
        }
    }

    private Lock getClientLock(String clientId) {
        Lock lock;
        lockPool.lock();
        try {
            lock = clientLocks.computeIfAbsent(clientId, k -> new ReentrantLock());
        } finally {
            lockPool.unlock();
        }
        return lock;
    }
}
