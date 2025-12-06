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
    private PostRepository postRepository;

    public List<Post> getUserPosts(Long userId) {
        return postRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Post> getAllPostsDesc() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    public void createPost(User user, String content) {
        this.createPost(user, content, null);
    }

    public void createPost(User user, String content, Long mediaId) {
        Post post = new Post();
        post.setUser(user);
        post.setContent(content);
        if (mediaId != null) {
            post.setMediaId(mediaId);
        }
        postRepository.save(post);
    }

    public void deletePost(Long postId) {
        postRepository.deleteById(postId);
    }

    public Post getPostById(Long postId) {
        return postRepository.findById(postId).orElse(null);
    }
}