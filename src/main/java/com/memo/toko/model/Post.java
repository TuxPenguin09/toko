package com.memo.toko.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime createdAt;

    private Long mediaId;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getMediaId() {
        return mediaId;
    }

    public User getUser() {
        return user;
    }

    // Setters
    public void setContent(String content) {
        this.content = content;
    }

    public void setMediaId(Long mediaId) {
        this.mediaId = mediaId;
    }

    public void setUser(User user) {
        this.user = user;
    }
}