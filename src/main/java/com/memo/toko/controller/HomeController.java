package com.memo.toko.controller;

import com.memo.toko.model.Post;
import com.memo.toko.model.User;
import com.memo.toko.repository.PostRepository;
import com.memo.toko.repository.UserRepository;
import com.memo.toko.service.PostService;
import com.memo.toko.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;
    private final PostService postService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Autowired
    public HomeController(UserService userService,
                          PostService postService,
                          PostRepository postRepository,
                          UserRepository userRepository) {
        this.userService = userService;
        this.postService = postService;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @ModelAttribute("currentUser")
    public User currentUser(HttpSession session) {
        Object uid = session.getAttribute("userId");
        if (uid instanceof Long) {
            return userService.getUser((Long) uid);
        }
        if (uid instanceof Integer) {
            // sometimes session stores Integer from form hidden value; handle gracefully
            return userService.getUser(((Integer) uid).longValue());
        }
        return null;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("posts", postRepository.findAll());
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("newPost", new Post());
        return "index";
    }

    @GetMapping("user/{id}")
    public String userPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getUser(id);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/";
        }
        model.addAttribute("user", user);
        model.addAttribute("posts", postService.getUserPosts(id));
        return "user"; // Thymeleaf template
    }

    @PostMapping("user/{id}/post")
    public String createPost(
            @PathVariable Long id,
            @RequestParam String content,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        // require login
        Object uid = session.getAttribute("userId");
        if (uid == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to post");
            return "redirect:/login";
        }
        long loggedId = (uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue();
        if (loggedId != id) {
            redirectAttributes.addFlashAttribute("error", "You can only post as yourself");
            return "redirect:/user/" + id;
        }

        User user = userService.getUser(id);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/";
        }
        if (content == null || content.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Content cannot be empty");
            return "redirect:/user/" + id;
        }
        postService.createPost(user, content.trim());
        return "redirect:/user/" + id;
    }

    @PostMapping("post/{postId}/delete")
    public String deletePost(
            @PathVariable Long postId,
            @RequestParam Long userId,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        // require login and ownership
        Object uid = session.getAttribute("userId");
        if (uid == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to delete posts");
            return "redirect:/login";
        }
        long loggedId = (uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue();
        if (loggedId != userId) {
            redirectAttributes.addFlashAttribute("error", "Not authorized to delete this post");
            return "redirect:/user/" + userId;
        }

        try {
            postService.deletePost(postId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not delete post");
        }
        return "redirect:/user/" + userId;
    }
}
