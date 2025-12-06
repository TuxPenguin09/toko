package com.memo.toko.controller;

import org.springframework.beans.factory.annotation.Value;
import com.memo.toko.model.Post;
import com.memo.toko.model.User;
import com.memo.toko.model.Like;
import com.memo.toko.repository.LikeRepository;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;
    private final PostService postService;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final String mediaBaseUrl;

    @Autowired
    public HomeController(UserService userService,
                          PostService postService,
                          UserRepository userRepository,
                          LikeRepository likeRepository,
                          @Value("${media.service.base-url:http://localhost:8092}") String mediaBaseUrl) {
        this.userService = userService;
        this.postService = postService;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.mediaBaseUrl = (mediaBaseUrl == null ? "http://localhost:8092" : mediaBaseUrl.trim());
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

    @ModelAttribute("mediaBaseUrl")
    public String mediaBaseUrl() {
        return this.mediaBaseUrl;
    }

    @GetMapping
    public String index(Model model, HttpSession session) {
        List<Post> posts = postService.getAllPostsDesc();
        model.addAttribute("posts", posts);
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("newPost", new Post());
        model.addAttribute("mediaMap", mediaForPosts(posts));

        model.addAttribute("likeCounts", getLikeCounts(posts));

        // Resolve profile pictures for users referenced on this page
        var usersOnPage = posts.stream().map(Post::getUser).filter(Objects::nonNull).collect(Collectors.toSet());
        Object uid = session.getAttribute("userId");
        if (uid != null) usersOnPage.add(userService.getUser((uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue()));
        model.addAttribute("profilePictureMap", resolveProfilePictureForUsers(usersOnPage));

        if (uid != null) {
            Long userId = (uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue();
            model.addAttribute("likedPosts", getLikedPostIds(userId, posts));
        } else {
            model.addAttribute("likedPosts", new HashSet<Long>()); // Empty set for non-logged in
        }

        return "index";
    }

    @GetMapping("user/{id}")
    public String userPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes, HttpSession session) {
        User user = userService.getUser(id);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/";
        }
        model.addAttribute("user", user);
        List<Post> posts = postService.getUserPosts(id);
        model.addAttribute("posts", posts);
        model.addAttribute("mediaMap", mediaForPosts(posts));

        // ALWAYS add likeCounts to the model
        model.addAttribute("likeCounts", getLikeCounts(posts));

        // Add likedPosts only if logged in, otherwise empty set
        Object uid = session.getAttribute("userId");
        if (uid != null) {
            Long userId = (uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue();
            model.addAttribute("likedPosts", getLikedPostIds(userId, posts));
        } else {
            model.addAttribute("likedPosts", new HashSet<Long>()); // Empty set for non-logged in
        }

        // Resolve profile pictures for the profile and its posts
        var usersOnPage = posts.stream().map(Post::getUser).filter(Objects::nonNull).collect(Collectors.toSet());
        usersOnPage.add(user);
        model.addAttribute("profilePictureMap", resolveProfilePictureForUsers(usersOnPage));

        return "user"; // Thymeleaf template
    }

    @PostMapping("user/{id}/post")
    public String createPost(
            @PathVariable Long id,
            @RequestParam String content,
            @RequestParam(name = "file", required = false) MultipartFile file,
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

        Long mediaId = null;
        if (file != null && !file.isEmpty()) {
            try {
                RestTemplate rest = new RestTemplate();
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                ByteArrayResource contents = new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                };
                body.add("file", contents);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
                ResponseEntity<java.util.Map> resp = rest.postForEntity(mediaBaseUrl + "/api/media/upload", requestEntity, java.util.Map.class);
                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    Object idObj = resp.getBody().get("id");
                    if (idObj instanceof Number) mediaId = ((Number) idObj).longValue();
                    else if (idObj != null) mediaId = Long.valueOf(idObj.toString());
                }
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Failed to upload media: " + e.getMessage());
                return "redirect:/user/" + id;
            }
        }
        postService.createPost(user, content.trim(), mediaId);
        return "redirect:/user/" + id;
    }

    private Map<Long, Map<String, Object>> mediaForPosts(List<Post> posts) {
        var ids = posts.stream()
                .map(Post::getMediaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) return java.util.Collections.emptyMap();

        RestTemplate rest = new RestTemplate();
        Map<Long, Map<String, Object>> result = new java.util.HashMap<>();
        for (Long id : ids) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> dto = rest.getForObject(mediaBaseUrl + "/api/media/" + id, Map.class);
                if (dto != null) {
                    Object urlObj = dto.get("url");
                    if (urlObj instanceof String) {
                        String url = (String) urlObj;
                        if (url.startsWith("/")) {
                            String base = mediaBaseUrl.endsWith("/") ? mediaBaseUrl.substring(0, mediaBaseUrl.length()-1) : mediaBaseUrl;
                            dto.put("url", base + url);
                        }
                    }
                    result.put(id, dto);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
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

    @PostMapping("/post/{postId}/like")
    public String likePost(@PathVariable Long postId,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        Object uid = session.getAttribute("userId");
        if (uid == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in to like posts");
            return "redirect:/login";
        }

        Long userId = (uid instanceof Long) ? (Long) uid : ((Integer) uid).longValue();

        Optional<Like> existingLike = likeRepository.findByUserIdAndPostId(userId, postId);

        if (existingLike.isPresent()) {
            likeRepository.delete(existingLike.get());
        } else {
            User user = userService.getUser(userId);
            Post post = postService.getPostById(postId);
            if (post != null) {
                Like like = new Like();
                like.setUser(user);
                like.setPost(post);
                likeRepository.save(like);
            }
        }

        return "redirect:/";
    }

        private Map<Long, Integer> getLikeCounts(List<Post> posts) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Post post : posts) {
            counts.put(post.getId(), likeRepository.countByPostId(post.getId()));
        }
        return counts;
    }

    private Set<Long> getLikedPostIds(Long userId, List<Post> posts) {
        Set<Long> likedIds = new HashSet<>();
        for (Post post : posts) {
            if (likeRepository.existsByUserIdAndPostId(userId, post.getId())) {
                likedIds.add(post.getId());
            }
        }
        return likedIds;
    }

    private Map<Long, String> resolveProfilePictureForUsers(Set<User> users) {
        Map<Long, String> map = new HashMap<>();
        if (users == null || users.isEmpty()) return map;
        RestTemplate rest = new RestTemplate();
        for (User u : users) {
            if (u == null || u.getId() == null) continue;
            String pp = u.getProfilePicture();
            if (pp == null || pp.isBlank()) continue;
            String resolved = null;
            try {
                if (pp.startsWith("http://") || pp.startsWith("https://")) {
                    resolved = pp;
                } else if (pp.startsWith("/")) {
                    String base = mediaBaseUrl.endsWith("/") ? mediaBaseUrl.substring(0, mediaBaseUrl.length()-1) : mediaBaseUrl;
                    resolved = base + pp;
                } else {
                    // maybe the stored value is a media id
                    try {
                        Long mid = Long.parseLong(pp);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> dto = rest.getForObject(mediaBaseUrl + "/api/media/" + mid, Map.class);
                        if (dto != null) {
                            Object urlObj = dto.get("url");
                            if (urlObj instanceof String) {
                                String url = (String) urlObj;
                                if (url.startsWith("/")) {
                                    String base = mediaBaseUrl.endsWith("/") ? mediaBaseUrl.substring(0, mediaBaseUrl.length()-1) : mediaBaseUrl;
                                    url = base + url;
                                }
                                resolved = url;
                            }
                        }
                    } catch (NumberFormatException nfe) {
                        // not a number, give up
                    }
                }
            } catch (Exception ignored) {
            }
            if (resolved != null) map.put(u.getId(), resolved);
        }
        return map;
    }

    /**
     * Associate a previously uploaded media item (on the media service) with the user's profile.
     * This endpoint does NOT accept files; the client should upload directly to the media service
     * and then call this endpoint with the returned mediaId. toko will then fetch the media
     * info and store the resolved absolute URL on the User.profilePicture field.
     */
    @PostMapping("user/{id}/profile-picture")
    public String setProfilePictureFromMedia(
            @PathVariable Long id,
            @RequestParam(name = "mediaId", required = true) Long mediaId,
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

        try {
            RestTemplate rest = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> dto = rest.getForObject(mediaBaseUrl + "/api/media/" + mediaId, Map.class);
            if (dto != null) {
                Object urlObj = dto.get("url");
                if (urlObj instanceof String) {
                    String url = (String) urlObj;
                    if (url.startsWith("/")) {
                        String base = mediaBaseUrl.endsWith("/") ? mediaBaseUrl.substring(0, mediaBaseUrl.length()-1) : mediaBaseUrl;
                        url = base + url;
                    }
                    user.setProfilePicture(url);
                    userRepository.save(user);
                    redirectAttributes.addFlashAttribute("success", "Profile picture updated successfully");
                    return "redirect:/user/" + id;
                }
            }
            redirectAttributes.addFlashAttribute("error", "Media not found");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not set profile picture: " + e.getMessage());
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
