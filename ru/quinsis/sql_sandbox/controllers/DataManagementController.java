package ru.quinsis.sql_sandbox.controllers;

import ru.quinsis.sql_sandbox.configs.JwtAuthenticationFilter;
import ru.quinsis.sql_sandbox.models.*;
import ru.quinsis.sql_sandbox.models.response.ApiResponse;
import com.example.sql_sandbox_2_0.models.*;
import ru.quinsis.sql_sandbox.services.DatabaseService;
import ru.quinsis.sql_sandbox.services.TaskService;
import ru.quinsis.sql_sandbox.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DataManagementController {
    private final UserService userService;
    private final DatabaseService databaseService;
    private final TaskService taskService;

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        ResponseEntity<Map<String, ApiResponse<Object>>> res = validate(request);
        if (res.getBody().containsKey("success")) {
            ApiResponse apiResponse = res.getBody().get("success");
            String token = apiResponse.getData().toString();
            String id = JwtAuthenticationFilter.getIdFromJWT(token);
            Optional<User> user = userService.findById(id);

            if (user.isPresent()) {
                databaseService.findAllByStakeholder(user.get()).map(databases -> {
                    for (Database database : databases) {
                        database.getUserDatabaseSettings().getStakeholderSessionIsActive().put(user.get().getId(), false);
                        databaseService.create(database);
                        if (database.getUserDatabaseSettings().getStakeholderSessionIsActive().values().stream()
                                .noneMatch(Boolean::booleanValue)) {
                            databaseService.unload(database);
                        }
                    }
                    return null;
                });
            }
        }

        Arrays.stream(request.getCookies())
                .filter(c -> Objects.equals(c.getName(), "access_token"))
                .findFirst().ifPresent(c -> {
                    c.setMaxAge(0);
                    c.setValue("");
                    c.setPath("/");
                    response.addCookie(c);
                });
        new SecurityContextLogoutHandler()
                .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }

    @PostMapping("/validate")
    public ResponseEntity<Map<String, ApiResponse<Object>>> validate(HttpServletRequest request) {
        String token;
        if (request.getHeader("Authorization") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.UNAUTHORIZED)
                    .data("Необходимо авторизоваться.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!JwtAuthenticationFilter.validateToken(token = JwtAuthenticationFilter.getJwtFromRequest(request))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.UNAUTHORIZED)
                    .data("Необходимо авторизоваться.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(token)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/databases")
    public ResponseEntity<Map<String, ApiResponse<Object>>> databases(HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);

        return userService.findById(id).map(user ->
                databaseService.findAllByStakeholder(user)
                        .map(databases -> {
                            Collections.reverse(databases);
                            databases = databases.stream()
                                    .sorted(Comparator.comparing(db -> db.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.getId()) ? 0 : 1))
                                    .collect(Collectors.toList());

                            JSONObject data = new JSONObject();
                            JSONArray databaseList = new JSONArray();

                            for (Database database : databases) {
                                JSONObject databaseObject = new JSONObject();
                                databaseObject.put("code", database.getCode());
                                databaseObject.put("name", database.getName());
                                databaseObject.put("isFavorite", database.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.getId()));
                                databaseObject.put("usersCount", database.getUserDatabaseSettings().getStakeholders().size());
                                databaseList.add(databaseObject);
                            }
                            data.put("databases", databaseList);

                            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                                    .status(HttpStatus.OK)
                                    .data(data)
                                    .timestamp(LocalDateTime.now())
                                    .build()));
                        })
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", ApiResponse.builder()
                                        .status(HttpStatus.NOT_FOUND)
                                        .data("Список баз данных пуст.")
                                        .timestamp(LocalDateTime.now())
                                        .build())))
        ).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ApiResponse.builder()
                        .status(HttpStatus.NOT_FOUND)
                        .data("Пользователь не найден.")
                        .timestamp(LocalDateTime.now())
                        .build())));
    }

    @PostMapping("/create/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> createDatabase(HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);

        if (user.isPresent()) {
            Database database = new Database();
            database.setName("Без названия");
            UserDatabaseSettings userDatabaseSettings = new UserDatabaseSettings();
            userDatabaseSettings.setOwner(user.get());
            List<User> stakeholders = new ArrayList<>();
            stakeholders.add(user.get());
            userDatabaseSettings.setUserDatabasePermission(new HashMap<>());
            List<String> permissions = new ArrayList<>();
            permissions.add("ALL");
            userDatabaseSettings.getUserDatabasePermission().put(user.get().getId(), permissions);
            userDatabaseSettings.setStakeholders(stakeholders);
            userDatabaseSettings.setStakeholderFavoriteDatabases(new HashMap<>());
            userDatabaseSettings.getStakeholderFavoriteDatabases().put(user.get().getId(), false);
            userDatabaseSettings.setStakeholderSessionIsActive(new HashMap<>());
            userDatabaseSettings.getStakeholderSessionIsActive().put(user.get().getId(), false);
            database.setUserDatabaseSettings(userDatabaseSettings);
            database.setCode(UUID.randomUUID().toString());
            database.setTables(new ArrayList<>());
            database.setKeys(new HashSet<>());
            databaseService.create(database);

            List<Database> databases = databaseService.findAllByStakeholder(user.get()).get();

            Collections.reverse(databases);
            databases = databases.stream()
                    .sorted(Comparator.comparing(db -> db.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()) ? 0 : 1))
                    .collect(Collectors.toList());

            JSONObject data = new JSONObject();
            JSONArray databaseList = new JSONArray();

            for (Database d : databases) {
                JSONObject databaseObject = new JSONObject();
                databaseObject.put("code", d.getCode());
                databaseObject.put("name", d.getName());
                databaseObject.put("isFavorite", d.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()));
                databaseObject.put("usersCount", d.getUserDatabaseSettings().getStakeholders().size());
                databaseList.add(databaseObject);
            }
            data.put("databases", databaseList);

            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Пользователь не найден.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/import/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> importDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();

        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Вы уже владеете этой базой данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        List<String> permissions = new ArrayList<>();
        permissions.add("SELECT");
        database.get().getUserDatabaseSettings().getUserDatabasePermission().put(user.get().getId(), permissions);
        database.get().getUserDatabaseSettings().getStakeholderFavoriteDatabases().put(user.get().getId(), false);
        database.get().getUserDatabaseSettings().getStakeholderSessionIsActive().put(user.get().getId(), false);
        database.get().getUserDatabaseSettings().getStakeholders().add(user.get());
        databaseService.create(database.get());

        List<Database> databases = databaseService.findAllByStakeholder(user.get()).get();

        Collections.reverse(databases);
        databases = databases.stream()
                .sorted(Comparator.comparing(db -> db.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()) ? 0 : 1))
                .collect(Collectors.toList());

        JSONObject data = new JSONObject();
        JSONArray databaseList = new JSONArray();

        for (Database d : databases) {
            JSONObject databaseObject = new JSONObject();
            databaseObject.put("code", d.getCode());
            databaseObject.put("name", d.getName());
            databaseObject.put("isFavorite", d.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()));
            databaseObject.put("usersCount", d.getUserDatabaseSettings().getStakeholders().size());
            databaseList.add(databaseObject);
        }
        data.put("databases", databaseList);

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/toggle-favorite/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> toggleFavoriteDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();

        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("База данных не найдена.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("У вас нет доступа к этой базе данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        boolean isFavorite = database.get()
                .getUserDatabaseSettings()
                .getStakeholderFavoriteDatabases()
                .getOrDefault(user.get().getId(), false);

        database.get().getUserDatabaseSettings().getStakeholderFavoriteDatabases().put(user.get().getId(), !isFavorite);
        databaseService.create(database.get());

        return userService.findById(id).map(user1 ->
                databaseService.findAllByStakeholder(user1)
                        .map(databases -> {
                            Collections.reverse(databases);
                            databases = databases.stream()
                                    .sorted(Comparator.comparing(db -> db.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()) ? 0 : 1))
                                    .collect(Collectors.toList());

                            JSONObject data = new JSONObject();
                            JSONArray databaseList = new JSONArray();

                            for (Database d : databases) {
                                JSONObject databaseObject = new JSONObject();
                                databaseObject.put("code", d.getCode());
                                databaseObject.put("name", d.getName());
                                databaseObject.put("isFavorite", d.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()));
                                databaseObject.put("usersCount", d.getUserDatabaseSettings().getStakeholders().size());
                                databaseList.add(databaseObject);
                            }
                            data.put("databases", databaseList);

                            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                                    .status(HttpStatus.OK)
                                    .data(data)
                                    .timestamp(LocalDateTime.now())
                                    .build()));
                        })
                        .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", ApiResponse.builder()
                                        .status(HttpStatus.NOT_FOUND)
                                        .data("Список баз данных пуст.")
                                        .timestamp(LocalDateTime.now())
                                        .build())))
        ).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ApiResponse.builder()
                        .status(HttpStatus.NOT_FOUND)
                        .data("Пользователь не найден.")
                        .timestamp(LocalDateTime.now())
                        .build())));
    }

    @PostMapping("/delete/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> deleteDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("У вас нет доступа к этой базе данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            databaseService.unload(database.get());
            if (Objects.equals(database.get().getUserDatabaseSettings().getOwner().getId(), user.get().getId())) {
                databaseService.delete(database.get());
            } else {
                database.get().getUserDatabaseSettings().getStakeholders().removeIf(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()));
                database.get().getUserDatabaseSettings().getStakeholderFavoriteDatabases().remove(user.get().getId());
                database.get().getUserDatabaseSettings().getStakeholderSessionIsActive().remove(user.get().getId());
                databaseService.create(database.get());
            }

            List<Database> databases = databaseService.findAllByStakeholder(user.get()).get();

            Collections.reverse(databases);
            databases = databases.stream()
                    .sorted(Comparator.comparing(db -> db.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()) ? 0 : 1))
                    .collect(Collectors.toList());

            JSONObject data = new JSONObject();
            JSONArray databaseList = new JSONArray();

            for (Database d : databases) {
                JSONObject databaseObject = new JSONObject();
                databaseObject.put("code", d.getCode());
                databaseObject.put("name", d.getName());
                databaseObject.put("isFavorite", d.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()));
                databaseObject.put("usersCount", d.getUserDatabaseSettings().getStakeholders().size());
                databaseList.add(databaseObject);
            }
            data.put("databases", databaseList);

            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/rename/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> renameDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        String newDbName = body.get("name").toString();
        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(database.get().getUserDatabaseSettings().getOwner().getId(), user.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("Вы не можете переименовать эту базу данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            database.get().setName(newDbName);
            databaseService.create(database.get());

            List<Database> databases = databaseService.findAllByStakeholder(user.get()).get();

            Collections.reverse(databases);
            databases = databases.stream()
                    .sorted(Comparator.comparing(db -> db.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()) ? 0 : 1))
                    .collect(Collectors.toList());

            JSONObject data = new JSONObject();
            JSONArray databaseList = new JSONArray();

            for (Database d : databases) {
                JSONObject databaseObject = new JSONObject();
                databaseObject.put("code", d.getCode());
                databaseObject.put("name", d.getName());
                databaseObject.put("isFavorite", d.getUserDatabaseSettings().getStakeholderFavoriteDatabases().get(user.get().getId()));
                databaseObject.put("usersCount", d.getUserDatabaseSettings().getStakeholders().size());
                databaseList.add(databaseObject);
            }
            data.put("databases", databaseList);

            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/onload/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> onloadDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("У вас нет доступа к этой базе данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data(database.get().getTables())
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/unload/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> unloadDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("У вас нет доступа к этой базе данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data("База данных отключена.")
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/query/database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> queryDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);

        if (!body.containsKey("code") || body.get("code") == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("База данных не выбрана.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
        String code = body.get("code").toString();
        String query = body.get("query").toString();

        Optional<Database> database = databaseService.findByCode(code);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("У вас нет доступа к этой базе данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        JSONObject data = new JSONObject();
        data.put("result", databaseService.queryDatabase(database.get(), user.get(), query));
        data.put("tables", database.get().getTables());

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/tasks")
    public ResponseEntity<Map<String, ApiResponse<Object>>> tasks(HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);

        if (user.isPresent()) {
            Optional<List<Task>> tasks = taskService.findAllByOwner(user.get());
            if (tasks.isPresent()) {
                Collections.reverse(tasks.get());

                JSONObject data = new JSONObject();
                JSONArray tasksList = new JSONArray();

                for (Task task : tasks.get()) {
                    JSONObject taskObject = new JSONObject();
                    taskObject.put("code", task.getCode());
                    taskObject.put("title", task.getTitle());
                    taskObject.put("description", task.getDescription());

                    // База данных
                    if (task.getDatabase() != null) {
                        if (databaseService.findByCode(task.getDatabase().getCode()).isPresent()) {
                            JSONObject databaseObject = new JSONObject();
                            databaseObject.put("code", task.getDatabase().getCode());
                            databaseObject.put("tables", databaseService.findByCode(task.getDatabase().getCode()).get().getTables());
                            taskObject.put("database", databaseObject);
                        } else {
                            taskObject.put("database", null);
                        }
                    } else {
                        taskObject.put("database", null);
                    }

                    // Подключения
                    JSONArray taskConnections = new JSONArray();
                    for (TaskConnection connection : task.getConnections()) {
                        JSONObject taskConnectionObject = new JSONObject();
                        taskConnectionObject.put("user", connection.getUser().getLogin());

                        List<TaskAttempt> reverse = connection.getAttempts();
                        Collections.reverse(reverse);

                        JSONArray attempts = new JSONArray();
                        for (TaskAttempt attempt : reverse) {
                            JSONObject attemptObject = new JSONObject();
                            attemptObject.put("date", attempt.getDate());
                            attemptObject.put("query", attempt.getQuery());
                            attempts.add(attemptObject);
                        }
                        taskConnectionObject.put("attempts", attempts);

                        JSONArray comments = new JSONArray();
                        for (TaskComment comment : connection.getComments()) {
                            JSONObject commentObject = new JSONObject();
                            commentObject.put("user", comment.getUser().getLogin());
                            commentObject.put("comment", comment.getComment());
                            commentObject.put("date", comment.getDate());
                            comments.add(commentObject);
                        }
                        taskConnectionObject.put("comments", comments);
                        taskConnections.add(taskConnectionObject);
                    }
                    taskObject.put("taskConnections", taskConnections);

                    tasksList.add(taskObject);
                }
                data.put("myTasks", tasksList);

                List<Task> connectedTasks = taskService.findAllByConnectedUser(user.get()).get();
                Collections.reverse(connectedTasks);
                JSONArray connectedTasksList = new JSONArray();

                for (Task connectedTask : connectedTasks) {
                    JSONObject connectedTaskItem = new JSONObject();
                    connectedTaskItem.put("code", connectedTask.getCode());
                    connectedTaskItem.put("title", connectedTask.getTitle());
                    connectedTaskItem.put("description", connectedTask.getDescription());

                    // База данных
                    if (connectedTask.getDatabase() != null) {
                        if (databaseService.findByCode(connectedTask.getDatabase().getCode()).isPresent()) {
                            JSONObject databaseObject = new JSONObject();
                            databaseObject.put("code", connectedTask.getDatabase().getCode());
                            databaseObject.put("tables", databaseService.findByCode(connectedTask.getDatabase().getCode()).get().getTables());
                            connectedTaskItem.put("database", databaseObject);
                        } else {
                            connectedTaskItem.put("database", null);
                        }
                    } else {
                        connectedTaskItem.put("database", null);
                    }

                    // Находим нужное подключение для истории попыток
                    Optional<TaskConnection> requiredConnection = connectedTask.getConnections()
                            .stream()
                            .filter(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))
                            .findFirst();

                    if (requiredConnection.isPresent()) {
                        JSONArray attemptsObject = new JSONArray();
                        List<TaskAttempt> reverse = requiredConnection.get().getAttempts();
                        Collections.reverse(reverse);
                        for (TaskAttempt taskAttempt : reverse) {
                            JSONObject attempt = new JSONObject();
                            attempt.put("query", taskAttempt.getQuery());
                            attempt.put("date", taskAttempt.getDate());
                            attemptsObject.add(attempt);
                        }
                        connectedTaskItem.put("attempts", attemptsObject);

                        // Сообщения
                        JSONArray commentsObject = new JSONArray();
                        for (TaskComment taskComment : requiredConnection.get().getComments()) {
                            JSONObject comment = new JSONObject();
                            comment.put("user", taskComment.getUser().getLogin());
                            comment.put("message", taskComment.getComment());
                            comment.put("date", taskComment.getDate());
                            commentsObject.add(comment);
                        }
                        connectedTaskItem.put("comments", commentsObject);
                    }

                    if (connectedTask.getDatabase() != null) {
                        if (databaseService.findByCode(connectedTask.getDatabase().getCode()).isPresent()) {
                            JSONObject databaseObject = new JSONObject();
                            databaseObject.put("code", connectedTask.getDatabase().getCode());
                            databaseObject.put("tables", databaseService.findByCode(connectedTask.getDatabase().getCode()).get().getTables());
                            connectedTaskItem.put("database", databaseObject);
                        } else {
                            connectedTaskItem.put("database", null);
                        }
                    } else {
                        connectedTaskItem.put("database", null);
                    }

                    connectedTasksList.add(connectedTaskItem);
                }
                data.put("connectedTasks", connectedTasksList);
                return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                        .status(HttpStatus.OK)
                        .data(data)
                        .timestamp(LocalDateTime.now())
                        .build()));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", ApiResponse.builder()
                                .status(HttpStatus.NOT_FOUND)
                                .data("Список ваших заданий пуст.")
                                .timestamp(LocalDateTime.now())
                                .build()));
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ApiResponse.builder()
                            .status(HttpStatus.NOT_FOUND)
                            .data("Пользователь не найден.")
                            .timestamp(LocalDateTime.now())
                            .build()));
        }
    }

    @PostMapping("/create/task")
    public ResponseEntity<Map<String, ApiResponse<Object>>> createTask(HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);

        if (user.isPresent()) {
            Task task = new Task();
            task.setTitle("Без названия");
            task.setCode(UUID.randomUUID().toString());
            task.setOwner(user.get());
            task.setDescription("Без описания");
            task.setConnections(new ArrayList<>());
            task.setDatabase(null);
            taskService.create(task);

            List<Task> tasks = taskService.findAllByOwner(user.get()).get();

            Collections.reverse(tasks);

            JSONObject data = new JSONObject();
            JSONArray tasksList = new JSONArray();

            for (Task task1 : tasks) {
                JSONObject taskObject = new JSONObject();
                taskObject.put("code", task1.getCode());
                taskObject.put("title", task1.getTitle());
                taskObject.put("description", task1.getDescription());
                taskObject.put("database", null);

                // Подключения
                JSONArray taskConnections = new JSONArray();
                for (TaskConnection connection : task.getConnections()) {
                    JSONObject taskConnectionObject = new JSONObject();
                    taskConnectionObject.put("user", connection.getUser());

                    JSONArray attempts = new JSONArray();
                    for (TaskAttempt attempt : connection.getAttempts()) {
                        JSONObject attemptObject = new JSONObject();
                        attemptObject.put("date", attempt.getQuery());
                        attemptObject.put("query", attempt.getQuery());
                        attempts.add(attemptObject);
                    }
                    taskConnectionObject.put("attempts", attempts);

                    JSONArray comments = new JSONArray();
                    for (TaskComment comment : connection.getComments()) {
                        JSONObject commentObject = new JSONObject();
                        commentObject.put("user", comment.getUser().getLogin());
                        commentObject.put("comment", comment.getComment());
                        commentObject.put("date", comment.getDate());
                        comments.add(commentObject);
                    }
                    taskConnectionObject.put("comments", comments);
                    taskConnections.add(taskConnectionObject);
                }
                taskObject.put("taskConnections", taskConnections);

                tasksList.add(taskObject);
            }
            data.put("myTasks", tasksList);

            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Пользователь не найден.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/delete/task")
    public ResponseEntity<Map<String, ApiResponse<Object>>> deleteTask(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        Optional<Task> task = taskService.findByCode(code);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(task.get().getOwner().getId(), user.get().getId()) && !task.get().getConnections().stream().anyMatch(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))) {
            System.out.println(!Objects.equals(task.get().getOwner().getId(), user.get().getId()));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("У вас нет доступа к этому заданию.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            if (Objects.equals(task.get().getOwner().getId(), user.get().getId())) {
                taskService.delete(task.get());
            } else {
                task.get().getConnections().removeIf(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()));
                taskService.create(task.get());
            }

            List<Task> myTasks = taskService.findAllByOwner(user.get()).get();
            List<Task> connectedTasks = taskService.findAllByConnectedUser(user.get()).get();

            Collections.reverse(myTasks);
            Collections.reverse(connectedTasks);

            JSONObject data = new JSONObject();
            JSONArray myTasksList = new JSONArray();
            JSONArray connectedTasksList = new JSONArray();

            for (Task myTaskItem : myTasks) {
                JSONObject taskObject = new JSONObject();
                taskObject.put("code", myTaskItem.getCode());
                taskObject.put("title", myTaskItem.getTitle());
                taskObject.put("description", myTaskItem.getDescription());

                // База данных
                if (myTaskItem.getDatabase() != null) {
                    if (databaseService.findByCode(myTaskItem.getDatabase().getCode()).isPresent()) {
                        JSONObject databaseObject = new JSONObject();
                        databaseObject.put("code", myTaskItem.getDatabase().getCode());
                        databaseObject.put("tables", databaseService.findByCode(myTaskItem.getDatabase().getCode()).get().getTables());
                        taskObject.put("database", databaseObject);
                    } else {
                        taskObject.put("database", null);
                    }
                } else {
                    taskObject.put("database", null);
                }

                // Подключения
                JSONArray taskConnections = new JSONArray();
                for (TaskConnection connection : task.get().getConnections()) {
                    JSONObject taskConnectionObject = new JSONObject();
                    taskConnectionObject.put("user", connection.getUser());

                    List<TaskAttempt> reverse = connection.getAttempts();
                    Collections.reverse(reverse);

                    JSONArray attempts = new JSONArray();
                    for (TaskAttempt attempt : reverse) {
                        JSONObject attemptObject = new JSONObject();
                        attemptObject.put("date", attempt.getQuery());
                        attemptObject.put("query", attempt.getQuery());
                        attempts.add(attemptObject);
                    }
                    taskConnectionObject.put("attempts", attempts);

                    JSONArray comments = new JSONArray();
                    for (TaskComment comment : connection.getComments()) {
                        JSONObject commentObject = new JSONObject();
                        commentObject.put("user", comment.getUser().getLogin());
                        commentObject.put("comment", comment.getComment());
                        commentObject.put("date", comment.getDate());
                        comments.add(commentObject);
                    }
                    taskConnectionObject.put("comments", comments);
                    taskConnections.add(taskConnectionObject);
                }
                taskObject.put("taskConnections", taskConnections);

                myTasksList.add(taskObject);
            }
            data.put("myTasks", myTasksList);

            for (Task connectedTaskItem : connectedTasks) {
                JSONObject taskObject = new JSONObject();
                taskObject.put("code", connectedTaskItem.getCode());
                taskObject.put("title", connectedTaskItem.getTitle());
                taskObject.put("description", connectedTaskItem.getDescription());

                // База данных
                if (connectedTaskItem.getDatabase() != null) {
                    if (databaseService.findByCode(connectedTaskItem.getDatabase().getCode()).isPresent()) {
                        JSONObject databaseObject = new JSONObject();
                        databaseObject.put("code", connectedTaskItem.getDatabase().getCode());
                        databaseObject.put("tables", databaseService.findByCode(connectedTaskItem.getDatabase().getCode()).get().getTables());
                        taskObject.put("database", databaseObject);
                    } else {
                        taskObject.put("database", null);
                    }
                } else {
                    taskObject.put("database", null);
                }

                // Находим нужное подключение для истории попыток
                Optional<TaskConnection> requiredConnection = connectedTaskItem.getConnections()
                        .stream()
                        .filter(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))
                        .findFirst();

                if (requiredConnection.isPresent()) {
                    JSONArray attemptsObject = new JSONArray();

                    List<TaskAttempt> reverse =  requiredConnection.get().getAttempts();
                    Collections.reverse(reverse);

                    for (TaskAttempt taskAttempt : reverse) {
                        JSONObject attempt = new JSONObject();
                        attempt.put("query", taskAttempt.getQuery());
                        attempt.put("date", taskAttempt.getDate());
                        attemptsObject.add(attempt);
                    }
                    taskObject.put("attempts", attemptsObject);

                    // Сообщения
                    JSONArray commentsObject = new JSONArray();
                    for (TaskComment taskComment : requiredConnection.get().getComments()) {
                        JSONObject comment = new JSONObject();
                        comment.put("user", taskComment.getUser().getLogin());
                        comment.put("message", taskComment.getComment());
                        comment.put("date", taskComment.getDate());
                        commentsObject.add(comment);
                    }
                    taskObject.put("comments", commentsObject);
                }

                connectedTasksList.add(taskObject);
            }
            data.put("connectedTasks", connectedTasksList);

            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/rename/task")
    public ResponseEntity<Map<String, ApiResponse<Object>>> renameTask(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        String newTaskName = body.get("name").toString();
        Optional<Task> task = taskService.findByCode(code);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(task.get().getOwner().getId(), user.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("Вы не можете переименовать это задание.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else {
            task.get().setTitle(newTaskName);
            taskService.create(task.get());
            List<Task> tasks = taskService.findAllByOwner(user.get()).get();
            Collections.reverse(tasks);
            JSONObject data = new JSONObject();
            JSONArray tasksList = new JSONArray();
            for (Task task1 : tasks) {
                JSONObject taskObject = new JSONObject();
                taskObject.put("code", task1.getCode());
                taskObject.put("title", task1.getTitle());
                taskObject.put("description", task1.getDescription());

                // База данных
                if (task1.getDatabase() != null) {
                    if (databaseService.findByCode(task1.getDatabase().getCode()).isPresent()) {
                        JSONObject databaseObject = new JSONObject();
                        databaseObject.put("code", task1.getDatabase().getCode());
                        databaseObject.put("tables", databaseService.findByCode(task1.getDatabase().getCode()).get().getTables());
                        taskObject.put("database", databaseObject);
                    } else {
                        taskObject.put("database", null);
                    }
                } else {
                    taskObject.put("database", null);
                }

                // Подключения
                JSONArray taskConnections = new JSONArray();
                for (TaskConnection connection : task.get().getConnections()) {
                    JSONObject taskConnectionObject = new JSONObject();
                    taskConnectionObject.put("user", connection.getUser());

                    List<TaskAttempt> reverse =  connection.getAttempts();
                    Collections.reverse(reverse);

                    JSONArray attempts = new JSONArray();
                    for (TaskAttempt attempt : reverse) {
                        JSONObject attemptObject = new JSONObject();
                        attemptObject.put("date", attempt.getQuery());
                        attemptObject.put("query", attempt.getQuery());
                        attempts.add(attemptObject);
                    }
                    taskConnectionObject.put("attempts", attempts);

                    JSONArray comments = new JSONArray();
                    for (TaskComment comment : connection.getComments()) {
                        JSONObject commentObject = new JSONObject();
                        commentObject.put("user", comment.getUser().getLogin());
                        commentObject.put("comment", comment.getComment());
                        commentObject.put("date", comment.getDate());
                        comments.add(commentObject);
                    }
                    taskConnectionObject.put("comments", comments);
                    taskConnections.add(taskConnectionObject);
                }
                taskObject.put("taskConnections", taskConnections);

                tasksList.add(taskObject);
            }
            data.put("myTasks", tasksList);

            return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                    .status(HttpStatus.OK)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
    }

    @PostMapping("/import/task")
    public ResponseEntity<Map<String, ApiResponse<Object>>> importTask(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();

        Optional<Task> task = taskService.findByCode(code);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (Objects.equals(task.get().getOwner().getId(), user.get().getId()) || task.get().getConnections().stream().anyMatch(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("У вас уже есть это задание.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (task.get().getDatabase() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Задание недоступно. Отсутствует база данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        TaskConnection taskConnection = new TaskConnection();
        taskConnection.setUser(user.get());
        taskConnection.setComments(new ArrayList<>());
        taskConnection.setAttempts(new ArrayList<>());
        task.get().getConnections().add(taskConnection);
        taskService.create(task.get());

        Database database = task.get().getDatabase();
        if (task.get().getDatabase() != null) {
            if (database.getUserDatabaseSettings().getStakeholders().stream().noneMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
                database.getUserDatabaseSettings().getStakeholders()
                        .add(user.get());
            }
            database.getUserDatabaseSettings().getStakeholderFavoriteDatabases()
                    .putIfAbsent(user.get().getId(), false);
            database.getUserDatabaseSettings().getStakeholderSessionIsActive()
                    .putIfAbsent(user.get().getId(), false);
            databaseService.create(database);
        }

        List<Task> connectedTasks = taskService.findAllByConnectedUser(user.get()).get();

        Collections.reverse(connectedTasks);

        JSONObject data = new JSONObject();
        JSONArray connectedTasksList = new JSONArray();

        for (Task connectedTask : connectedTasks) {
            JSONObject connectedTaskItem = new JSONObject();
            connectedTaskItem.put("code", connectedTask.getCode());
            connectedTaskItem.put("title", connectedTask.getTitle());
            connectedTaskItem.put("description", connectedTask.getDescription());

            // База данных
            if (databaseService.findByCode(connectedTask.getDatabase().getCode()).isPresent()) {
                JSONObject databaseObject = new JSONObject();
                databaseObject.put("code", connectedTask.getDatabase().getCode());
                databaseObject.put("tables", databaseService.findByCode(connectedTask.getDatabase().getCode()).get().getTables());
                connectedTaskItem.put("database", databaseObject);
            } else {
                connectedTaskItem.put("database", null);
            }

            // Находим нужное подключение для истории попыток
            Optional<TaskConnection> requiredConnection = connectedTask.getConnections()
                    .stream()
                    .filter(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))
                    .findFirst();

            if (requiredConnection.isPresent()) {
                JSONArray attemptsObject = new JSONArray();

                List<TaskAttempt> reverse = requiredConnection.get().getAttempts();
                Collections.reverse(reverse);

                for (TaskAttempt taskAttempt : reverse) {
                    JSONObject attempt = new JSONObject();
                    attempt.put("query", taskAttempt.getQuery());
                    attempt.put("date", taskAttempt.getDate());
                    attemptsObject.add(attempt);
                }
                connectedTaskItem.put("attempts", attemptsObject);

                // Сообщения
                JSONArray commentsObject = new JSONArray();
                for (TaskComment taskComment : requiredConnection.get().getComments()) {
                    JSONObject comment = new JSONObject();
                    comment.put("user", taskComment.getUser().getLogin());
                    comment.put("message", taskComment.getComment());
                    comment.put("date", taskComment.getDate());
                    commentsObject.add(comment);
                }
                connectedTaskItem.put("comments", commentsObject);
            }

            connectedTasksList.add(connectedTaskItem);
        }
        data.put("connectedTasks", connectedTasksList);

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/task-connect-database")
    public ResponseEntity<Map<String, ApiResponse<Object>>> taskConnectDatabase(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String databaseCode = body.get("databaseCode").toString();
        String taskCode = body.get("taskCode").toString();

        Optional<Database> database = databaseService.findByCode(databaseCode);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Код базы данных недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(database.get().getUserDatabaseSettings().getOwner().getId(), user.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("Вы не можете подключить чужую базу данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        Optional<Task> task = taskService.findByCode(taskCode);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Код задания недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(task.get().getOwner().getId(), user.get().getId()) && !task.get().getConnections().stream().anyMatch(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("У вас нет доступа к этому заданию.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        for (TaskConnection connection : task.get().getConnections()) {
            User connectionUser = connection.getUser();

            if (database.get().getUserDatabaseSettings().getStakeholders().stream().noneMatch(stakeholder -> Objects.equals(stakeholder.getId(), connectionUser.getId()))) {
                database.get().getUserDatabaseSettings().getStakeholders()
                        .add(connectionUser);
            }
            database.get().getUserDatabaseSettings().getStakeholderFavoriteDatabases()
                    .putIfAbsent(connectionUser.getId(), false);
            database.get().getUserDatabaseSettings().getStakeholderSessionIsActive()
                    .putIfAbsent(connectionUser.getId(), false);
            databaseService.create(database.get());
        }

        task.get().setDatabase(database.get());
        taskService.create(task.get());

        JSONObject data = new JSONObject();

        JSONObject databaseObject = new JSONObject();
        databaseObject.put("code", database.get().getCode());
        databaseObject.put("tables", databaseService.findByCode(database.get().getCode()).get().getTables());
        data.put("database", databaseObject);

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/save/task")
    public ResponseEntity<Map<String, ApiResponse<Object>>> saveTask(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }

        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        Optional<User> user = userService.findById(id);
        String code = body.get("code").toString();
        String description = body.get("description").toString();

        Optional<Task> task = taskService.findByCode(code);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .data("Код недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(task.get().getOwner().getId(), user.get().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("Вы не можете изменить это задание.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        task.get().setDescription(description);
        taskService.create(task.get());

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data("Задание сохранено.")
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }

    @PostMapping("/query/task")
    public ResponseEntity<Map<String, ApiResponse<Object>>> queryTask(@RequestBody JSONObject body, HttpServletRequest request) {
        ResponseEntity<Map<String, ApiResponse<Object>>> response = validate(request);
        if (response.getBody().containsKey("error")) {
            return response;
        }
        ApiResponse apiResponse = response.getBody().get("success");
        String token = apiResponse.getData().toString();
        String id = JwtAuthenticationFilter.getIdFromJWT(token);
        String query = body.get("query").toString();
        Optional<User> user = userService.findById(id);

        if (!body.containsKey("taskCode") || body.get("taskCode") == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Задание не выбрано.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
        String taskCode = body.get("taskCode").toString();
        Optional<Task> task = taskService.findByCode(taskCode);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код задания недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!Objects.equals(task.get().getOwner().getId(), user.get().getId()) && !task.get().getConnections().stream().anyMatch(connection -> Objects.equals(connection.getUser().getId(), user.get().getId()))) {
            System.out.println(!Objects.equals(task.get().getOwner().getId(), user.get().getId()));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .data("У вас нет доступа к этому заданию.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        if (!body.containsKey("databaseCode") || body.get("databaseCode") == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("База данных не выбрана.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }
        String databaseCode = body.get("databaseCode").toString();
        Optional<Database> database = databaseService.findByCode(databaseCode);
        if (database.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("Код базы данных недействителен.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        } else if (!database.get().getUserDatabaseSettings().getStakeholders().stream().anyMatch(stakeholder -> Objects.equals(stakeholder.getId(), user.get().getId()))) {
            return ResponseEntity.badRequest().body(Map.of("error", ApiResponse.builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .data("У вас нет доступа к этой базе данных.")
                    .timestamp(LocalDateTime.now())
                    .build()
            ));
        }

        // Делаем запрос с откатом
        JSONObject data = new JSONObject();
        data.put("taskCode", taskCode);
        data.put("result", databaseService.queryTask(database.get(), task.get(), user.get(), query));
        data.put("tables", database.get().getTables());

        // Добавляем в задание попытку
        task.get().getConnections().stream().filter(connection -> Objects.equals(connection.getUser().getId(), user.get().getId())).forEach(connection -> {
            TaskAttempt taskAttempt = new TaskAttempt();
            taskAttempt.setQuery(query);
            taskAttempt.setDate(LocalDateTime.now());
            connection.getAttempts().add(taskAttempt);
            taskService.create(task.get());

            List<TaskAttempt> reverse = connection.getAttempts();
            Collections.reverse(reverse);

            JSONArray attempts = new JSONArray();
            for (TaskAttempt attempt : reverse) {
                JSONObject attemptObject = new JSONObject();
                attemptObject.put("date", attempt.getDate());
                attemptObject.put("query", attempt.getQuery());
                attempts.add(attemptObject);
            }
            data.put("attempts", attempts);
        });

        return ResponseEntity.ok(Map.of("success", ApiResponse.builder()
                .status(HttpStatus.OK)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build()
        ));
    }
}
