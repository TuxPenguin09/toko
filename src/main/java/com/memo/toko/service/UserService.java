package com.memo.toko.service;

import com.memo.toko.model.User;
import com.memo.toko.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepo;

    public User getUser(Long id) {
        return userRepo.findById(id).orElse(null);
    }
}