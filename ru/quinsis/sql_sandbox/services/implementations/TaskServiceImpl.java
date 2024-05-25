package ru.quinsis.sql_sandbox.services.implementations;

import ru.quinsis.sql_sandbox.models.Task;
import ru.quinsis.sql_sandbox.models.User;
import ru.quinsis.sql_sandbox.repositories.TaskRepository;
import ru.quinsis.sql_sandbox.services.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {
    private final TaskRepository taskRepository;

    @Override
    public Optional<List<Task>> findAllByOwner(User user) {
        return taskRepository.findAllByOwner(user);
    }

    @Override
    public Optional<List<Task>> findAllByConnectedUser(User user) {
        return taskRepository.findAllByConnectedUser(user);
    }

    @Override
    public void create(Task task) {
        taskRepository.save(task);
    }

    @Override
    public void delete(Task task) {
        taskRepository.delete(task);
    }

    @Override
    public Optional<Task> findByCode(String code) {
        return taskRepository.findByCode(code);
    }
}
