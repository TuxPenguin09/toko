package com.memo.toko.controller;

import com.memo.toko.model.User;
import com.memo.toko.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping
public class AuthController {

    private final UserService userService;

    @Autowired
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    public String showRegister() {
        return "register";
    }

    @PostMapping("/register")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) String confirm,
                             RedirectAttributes redirectAttributes,
                             HttpSession session) {
        try {
            if (confirm == null || !confirm.equals(password)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match");
                return "redirect:/register";
            }
            User u = userService.registerUser(username, password);
            session.setAttribute("userId", u.getId());
            redirectAttributes.addFlashAttribute("success", "Registered and logged in");
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          RedirectAttributes redirectAttributes,
                          HttpSession session) {
        var opt = userService.getByUsername(username);
        if (opt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Invalid credentials");
            return "redirect:/login";
        }
        User u = opt.get();
        if (!userService.verifyPassword(u, password)) {
            redirectAttributes.addFlashAttribute("error", "Invalid credentials");
            return "redirect:/login";
        }
        session.setAttribute("userId", u.getId());
        redirectAttributes.addFlashAttribute("success", "Logged in");
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String doLogout(HttpSession session, RedirectAttributes redirectAttributes) {
        session.invalidate();
        redirectAttributes.addFlashAttribute("success", "Logged out");
        return "redirect:/";
    }
}

