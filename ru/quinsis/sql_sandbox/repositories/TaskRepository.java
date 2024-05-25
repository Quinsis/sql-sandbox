package ru.quinsis.sql_sandbox.repositories;

import ru.quinsis.sql_sandbox.models.Task;
import ru.quinsis.sql_sandbox.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends MongoRepository<Task, String> {
    Optional<List<Task>> findAllByOwner(User owner);
    @Query("{ 'connections.user' : ?0 }")
    Optional<List<Task>> findAllByConnectedUser(User owner);
    Optional<Task> findByCode(String code);
}
