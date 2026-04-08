package com.omar.bankapi.service;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.*;
import com.omar.bankapi.repository.*;
import com.omar.bankapi.util.AccountNumberGenerator;
import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
public class UserService {

    private static final int ACCOUNT_NUMBER_RETRY_LIMIT = 5;

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final JwtService jwtService;

    // Get all users
    public List<UserDTO> getAllUsers() {
        requireAdmin();
        return userRepository.findAll()
                .stream()
                .map(this::mapToUserDTO)
                .collect(Collectors.toList());
    }

    // Get user by ID
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireAdminOrSelf(user);
        return mapToUserDTO(user);
    }

    // Create user
    @Transactional
    public UserDTO register(CreateUserDTO dto) {

        String email = dto.getEmail().trim().toLowerCase();
        String username = dto.getUsername().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        Role role = roleRepository.findByName("ROLE_USER")
                .or(() -> roleRepository.findByName("USER"))
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Default role not configured"));

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(role);

        User savedUser = userRepository.save(user);

        createDefaultAccount(savedUser);

        return mapToUserDTO(savedUser);
    }

    @Transactional
    public void deleteUserById(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireAdminOrSelf(user);

        List<Account> accounts = accountRepository.findByUserId(user.getId());
        if (!accounts.isEmpty()) {
            List<Long> ids = accounts.stream().map(Account::getId).toList();
            transactionRepository.deleteBySenderIdInOrReceiverIdIn(ids, ids);
            accountRepository.deleteAllById(ids);

        }


        userRepository.delete(user);
    }

    @Transactional
    public UserDTO updateUser(Long id, UpdateUserDTO dto) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireAdminOrSelf(user);
        String email = dto.getEmail().trim().toLowerCase();
        String username = dto.getUsername().trim().toLowerCase();

        if (userRepository.existsByUsernameAndIdNot(username, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        user.setUsername(username);
        user.setEmail(email);

        if (dto.getRoleId() != null) {
            if (!isAdmin(requireAuth())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
            }
            Role role = roleRepository.findById(dto.getRoleId())
                    .orElseThrow(() ->
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
            user.setRole(role);
        }

        return mapToUserDTO(userRepository.save(user));

    }
    private void createDefaultAccount(User savedUser) {
        int attempts = 0;

        while (attempts < ACCOUNT_NUMBER_RETRY_LIMIT) {
            attempts++;

            Account account = new Account();
            account.setAccountNumber(AccountNumberGenerator.generate());
            account.setBalance(BigDecimal.ZERO);
            account.setUser(savedUser);
            account.setType(AccountType.CHECKING);

            try {
                accountRepository.save(account);
                return;
            } catch (DataIntegrityViolationException ex) {
                if (attempts >= ACCOUNT_NUMBER_RETRY_LIMIT) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Failed to generate a unique account number");
                }
            }
        }
    }

    // Map entity -> DTO
    private UserDTO mapToUserDTO(User user) {

        UserDTO dto = new UserDTO();

        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole().getName());

        return dto;
    }

    public Map<String, String> login(LoginDTO dto) {

        String username = dto.getUsername() == null ? null : dto.getUsername().trim().toLowerCase();

        User user = userRepository.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole().getName()
        );

        return Map.of("token", token);
    }

    private Authentication requireAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return auth;
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private void requireAdmin() {
        if (!isAdmin(requireAuth())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private void requireAdminOrSelf(User user) {
        Authentication auth = requireAuth();
        if (isAdmin(auth)) {
            return;
        }
        if (user == null || !user.getUsername().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}