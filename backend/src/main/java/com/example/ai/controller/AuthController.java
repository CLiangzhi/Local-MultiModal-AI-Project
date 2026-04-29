package com.example.ai.controller;

import com.example.ai.entity.UserEntity;
import com.example.ai.repository.UserRepository;
import com.example.ai.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BCryptPasswordEncoder encoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        UserEntity user = userRepository.findByUsername(username);
        // 核对密码 (这里用了 BCrypt 加密匹配)
        if (user != null && encoder.matches(password, user.getPasswordHash())) {
            String token = jwtUtil.generateToken(user.getId());
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "username", user.getUsername()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("message", "用户名或密码错误"));
    }
}