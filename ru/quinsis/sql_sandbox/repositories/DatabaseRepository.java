package ru.quinsis.sql_sandbox.repositories;

import ru.quinsis.sql_sandbox.models.Database;
import ru.quinsis.sql_sandbox.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseRepository extends MongoRepository<Database, String> {
    Optional<Database> findById(String id);
    Optional<List<Database>> findAllByUserDatabaseSettings_StakeholdersContaining(User stakeholder);
    Optional<Database> findByCode(String code);
}
