package ru.quinsis.sql_sandbox.repositories;

import ru.quinsis.sql_sandbox.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findById(String id);
    Optional<User> findByLogin(String login);
}
