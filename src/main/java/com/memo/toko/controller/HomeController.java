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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

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
        model.addAttribute("posts", postRepository.findAllByOrderByCreatedAtDesc());
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
            return "redirect:/";
        }

        User user = userService.getUser(id);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/";
        }
        if (content == null || content.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Content cannot be empty");
            return "redirect:/";
        }
        postService.createPost(user, content.trim());
        redirectAttributes.addFlashAttribute("success", "Post created successfully");
        return "redirect:/";
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

    @PostMapping("user/{id}/profile-picture")
    public String uploadProfilePicture(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            RedirectAttributes redirectAttributes,
            HttpSession session
    ) {
        // require login
        Object uid = session.getAttribute("userId");
        if (uid == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in");
            return "redirect:/login";
        }
        long loggedId = (uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue();
        if (loggedId != id) {
            redirectAttributes.addFlashAttribute("error", "You can only edit your own profile");
            return "redirect:/user/" + id;
        }

        User user = userService.getUser(id);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/";
        }

        // Validate file
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file");
            return "redirect:/user/" + id;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            redirectAttributes.addFlashAttribute("error", "Please upload an image file");
            return "redirect:/user/" + id;
        }

        try {
            // Create uploads directory if it doesn't exist
            String uploadsDir = "uploads/profiles";
            File dir = new File(uploadsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Generate filename
            String filename = "user_" + id + "_" + System.currentTimeMillis() + getFileExtension(file.getOriginalFilename());
            String filepath = uploadsDir + File.separator + filename;

            // Save file
            Files.write(Paths.get(filepath), file.getBytes());

            // Delete old profile picture if exists
            if (user.getProfilePicture() != null && !user.getProfilePicture().isEmpty()) {
                try {
                    Files.deleteIfExists(Paths.get(user.getProfilePicture()));
                } catch (Exception e) {
                    System.err.println("Could not delete old profile picture: " + e.getMessage());
                }
            }

            // Update user profile picture path
            user.setProfilePicture("/" + filepath.replace(File.separator, "/"));
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("success", "Profile picture updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not upload file: " + e.getMessage());
        }

        return "redirect:/user/" + id;
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
