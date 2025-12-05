package com.memo.toko.service;

import com.memo.toko.model.Post;
import com.memo.toko.model.User;
import com.memo.toko.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {
    @Autowired
    private PostRepository postRepo;

    public List<Post> getUserPosts(Long userId) {
        return postRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Post> getAllPostsDesc() {
        return postRepo.findAllByOrderByCreatedAtDesc();
    }

    public void createPost(User user, String content) {
        createPost(user, content, null);
    }

    public void createPost(User user, String content, Long mediaId) {
        Post p = new Post();
        p.setUser(user);
        p.setContent(content);
        if (mediaId != null) p.setMediaId(mediaId);
        postRepo.save(p);
    }

    public void deletePost(Long postId) {
        postRepo.deleteById(postId);
    }
}
