package com.memo.toko.controller;

import com.memo.toko.model.User;
import com.memo.toko.service.PostService;
import com.memo.toko.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

public class HomeController {

    @Autowired
    UserService userService;

    @Autowired
    PostService postService;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/user/{id}")
    public String userPage(@PathVariable Long id, Model model) {
        User user = userService.getUser(id);
        model.addAttribute("user", user);
        model.addAttribute("posts", postService.getUserPosts(id));
        return "user"; // Thymeleaf template
    }

    @PostMapping("/user/{id}/post")
    public String createPost(
            @PathVariable Long id,
            @RequestParam String content
    ) {
        User user = userService.getUser(id);
        postService.createPost(user, content);
        return "redirect:/user/" + id;
    }

    @PostMapping("/post/{postId}/delete")
    public String deletePost(
            @PathVariable Long postId,
            @RequestParam Long userId
    ) {
        postService.deletePost(postId);
        return "redirect:/user/" + userId;
    }
}
