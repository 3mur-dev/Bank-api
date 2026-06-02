package com.omar.bankapi.service;

import com.omar.bankapi.dto.AuthResponseDTO;
import com.omar.bankapi.dto.CreateUserDTO;
import com.omar.bankapi.dto.LoginDTO;
import com.omar.bankapi.dto.UserDTO;
import com.omar.bankapi.exception.LoginRateLimitExceededException;
import com.omar.bankapi.exception.InvalidCredentialsException;
import com.omar.bankapi.mapper.UserMapper;
import com.omar.bankapi.model.Role;
import com.omar.bankapi.model.User;
import com.omar.bankapi.repository.RoleRepository;
import com.omar.bankapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RoleRepository roleRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthResponseDTO login(LoginDTO dto, String clientIp) {

        String username = normalize(dto.username());

        try {
            loginRateLimiter.registerAttempt(username, clientIp);
        } catch (LoginRateLimitExceededException ex) {
            auditService.recordFailureActor(
                    username,
                    "LOGIN",
                    "AUTH",
                    username,
                    ex.getMessage(),
                    null
            );
            throw ex;
        }

        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> {
                    auditService.recordFailureActor(
                            username,
                            "LOGIN",
                            "AUTH",
                            username,
                            "Invalid credentials",
                            null
                    );
                    return new InvalidCredentialsException("Invalid credentials");
                });

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            auditService.recordFailureActor(
                    username,
                    "LOGIN",
                    "AUTH",
                    username,
                    "Invalid credentials",
                    null
            );
            throw new InvalidCredentialsException("Invalid credentials");
        }

        loginRateLimiter.reset(username, clientIp);

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole().getName()
        );

        auditService.recordActor(
                username,
                "LOGIN",
                "AUTH",
                String.valueOf(user.getId()),
                true,
                null,
                null,
                null
        );

        return new AuthResponseDTO(token);
    }

    @Transactional
    public UserDTO register(CreateUserDTO dto) {

        String email = normalize(dto.email());
        String username = normalize(dto.username());

        if (userRepository.existsByUsernameAndDeletedFalse(username)) {
            auditService.recordFailureActor(
                    username,
                    "REGISTER",
                    "USER",
                    username,
                    "Username already exists",
                    null
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        if (userRepository.existsByEmailAndDeletedFalse(email)) {
            auditService.recordFailureActor(
                    username,
                    "REGISTER",
                    "USER",
                    email,
                    "Email already exists",
                    null
            );
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        Role role = roleRepository.findByName("ROLE_USER")
                .or(() -> roleRepository.findByName("USER"))
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role not configured"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(role);
        user.setDeleted(false);
        user.setDeletedAt(null);

        User savedUser = userRepository.save(user);

        accountService.createDefaultAccount(savedUser);

        auditService.recordActor(
                username,
                "REGISTER",
                "USER",
                String.valueOf(savedUser.getId()),
                true,
                null,
                null,
                new UserDTO(
                        savedUser.getId(),
                        savedUser.getUsername(),
                        savedUser.getEmail(),
                        savedUser.getRoleName(),
                        savedUser.isDeleted(),
                        savedUser.getDeletedAt()
                )
        );

        return UserMapper.toDTO(savedUser);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }
}
