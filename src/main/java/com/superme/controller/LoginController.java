package com.superme.controller;

import com.superme.admin.dto.AdminLoginResponse;
import com.superme.admin.service.AuthService;
import com.superme.dto.AuthenticationRequest;
import com.superme.dto.LoginResponse;
import com.superme.exception.BusinessException;
import com.superme.exception.UnauthorizedActionException;
import com.superme.mapper.UserMapper;
import com.superme.model.User;
import com.superme.service.UserService;
import com.superme.util.UserJwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class LoginController {

    private final UserService userService;
    private final AuthService adminAuthService;

    public LoginController(UserService userService, AuthService adminAuthService) {
        this.userService = userService;
        this.adminAuthService = adminAuthService;
    }

    /**
     * Unified login endpoint that routes to the correct DB (admin vs user) based on the identifier.
     *
     * If the identifier exists in both DBs, we fail fast and ask the client to call the explicit endpoint:
     * - /admin/auth/login (admin)
     * - /auth/user/login  (user)
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthenticationRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException("Password cannot be empty");
        }

        String email = trimToNull(request.getEmail());
        String phone = trimToNull(request.getPhone());

        boolean adminMatch = false;
        boolean userMatch = false;

        if (email != null) {
            adminMatch = adminAuthService.existsByEmail(email);
            userMatch = userService.existsByEmail(email);
        }

        if (phone != null) {
            adminMatch = adminMatch || adminAuthService.existsByPhone(phone);
            userMatch = userMatch || userService.existsByPhone(phone);
        }

        if (adminMatch && userMatch) {
            throw new BusinessException(
                    "Identifier matches both Admin and User. Use /admin/auth/login for admin or /auth/user/login for user."
            );
        }



        if (adminMatch) {
            AdminLoginResponse response = (email != null)
                    ? adminAuthService.login(email, request.getPassword())
                    : adminAuthService.loginByPhone(phone, request.getPassword());
            return ResponseEntity.ok(response);
        }

        User user = userService.login(email, phone, request.getPassword())
                .orElseThrow(() -> new UnauthorizedActionException("Invalid credentials."));

        String token = UserJwtUtil.generateToken(user);
        return ResponseEntity.ok(new LoginResponse(token, UserMapper.toDto(user)));
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
