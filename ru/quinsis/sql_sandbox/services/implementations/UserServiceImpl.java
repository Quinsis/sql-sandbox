package ru.quinsis.sql_sandbox.services.implementations;

import ru.quinsis.sql_sandbox.models.User;
import ru.quinsis.sql_sandbox.repositories.UserRepository;
import ru.quinsis.sql_sandbox.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> findByLogin(String login) {
        return userRepository.findByLogin(login);
    }

    @Override
    public String create(User user) {
        return userRepository.findByLogin(user.getLogin().toLowerCase())
                .map(_ -> "Логин уже используется.")
                .orElseGet(() -> {
                    user.setLogin(user.getLogin().toLowerCase());
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
                    userRepository.save(user);
                    return "Регистрация успешно завершена.";
                });
    }

    @Override
    public void save(User user) {
        userRepository.save(user);
    }
}
