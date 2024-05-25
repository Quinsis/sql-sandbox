package ru.quinsis.sql_sandbox.models.database_components;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PostgreSQLTypeMap {
    private static final Map<String, Optional<Integer>> typeMap = new HashMap<>();

    static {
        typeMap.put("timestamp without time zone", Optional.of(Types.TIMESTAMP));
        typeMap.put("date", Optional.of(Types.DATE));
        typeMap.put("time without time zone", Optional.of(Types.TIME));
        typeMap.put("timestamp with time zone", Optional.of(Types.TIMESTAMP_WITH_TIMEZONE));
        typeMap.put("time with time zone", Optional.of(Types.TIME_WITH_TIMEZONE));
    }

    public static Optional<Integer> getSQLType(String postgresType) {
        if (typeMap.containsKey(postgresType)) {
            return typeMap.get(postgresType);
        } else {
            return Optional.empty();
        }
    }

    public static void set(PreparedStatement preparedStatement, int parameterIndex, Object value, String sqlType)
            throws SQLException {
        if (value == null) {
            preparedStatement.setObject(parameterIndex, null);
        } else {
            switch (sqlType) {
                case "time without time zone", "time with time zone":
                    java.sql.Timestamp timestamp = new java.sql.Timestamp(((java.util.Date) value).getTime());
                    preparedStatement.setTimestamp(parameterIndex, timestamp);
                    break;

                case "timestamp without time zone", "timestamp with time zone", "date":
                    java.sql.Date sqlDate = new java.sql.Date(((java.util.Date) value).getTime());
                    preparedStatement.setDate(parameterIndex, sqlDate);
                    break;
                default:
                    throw new SQLException("Unsupported SQL type: " + sqlType);
            }
        }
    }
}

