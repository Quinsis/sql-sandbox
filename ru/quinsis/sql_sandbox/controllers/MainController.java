package ru.quinsis.sql_sandbox.controllers;

import ru.quinsis.sql_sandbox.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
public class MainController {
    private final UserService userService;

    @GetMapping("/")
    public ModelAndView index(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("user", userService.findByLogin(principal.getName()).get());
        }
        return new ModelAndView("index");
    }
}
