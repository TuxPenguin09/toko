package com.memo.toko.service;

import com.memo.toko.model.User;
import com.memo.toko.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepo;

    public User getUser(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    public Optional<User> getByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    public User registerUser(String username, String rawPassword) throws IllegalArgumentException {
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("Username required");
        if (rawPassword == null || rawPassword.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters");
        if (userRepo.findByUsername(username).isPresent()) throw new IllegalArgumentException("Username already taken");

        String salt = generateSalt();
        String hash = hashPassword(rawPassword, salt);

        User u = new User();
        u.setUsername(username);
        u.setSalt(salt);
        u.setPasswordHash(hash);
        return userRepo.save(u);
    }

    public boolean verifyPassword(User user, String rawPassword) {
        if (user == null || rawPassword == null) return false;
        String salt = user.getSalt();
        String expected = user.getPasswordHash();
        String actual = hashPassword(rawPassword, salt);
        return slowEquals(expected, actual);
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String saltBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 100_000, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean slowEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aa = a.getBytes();
        byte[] bb = b.getBytes();
        int diff = aa.length ^ bb.length;
        for (int i = 0; i < aa.length && i < bb.length; i++) {
            diff |= aa[i] ^ bb[i];
        }
        return diff == 0;
    }
}