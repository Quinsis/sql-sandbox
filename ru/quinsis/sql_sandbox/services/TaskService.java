package ru.quinsis.sql_sandbox.services;

import ru.quinsis.sql_sandbox.models.Task;
import ru.quinsis.sql_sandbox.models.User;

import java.util.List;
import java.util.Optional;

public interface TaskService {
    Optional<List<Task>> findAllByOwner(User user);
    Optional<List<Task>> findAllByConnectedUser(User user);
    void create(Task task);
    void delete(Task task);
    Optional<Task> findByCode(String code);
}
