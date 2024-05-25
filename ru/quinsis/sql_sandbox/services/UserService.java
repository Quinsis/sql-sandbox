package ru.quinsis.sql_sandbox.services;

import ru.quinsis.sql_sandbox.models.User;

import java.util.Optional;

public interface UserService {
    Optional<User> findById(String id);
    Optional<User> findByLogin(String login);
    String create(User user);
    void save(User user);
}
