package com.omar.bankapi.service;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.mapper.UserMapper;
import com.omar.bankapi.model.*;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.repository.*;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@AllArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public Page<UserDTO> getAllUsers(Pageable pageable, boolean includeDeleted) {
        requireAdmin();
        return (includeDeleted ? userRepository.findAll(pageable) : userRepository.findAllByDeletedFalse(pageable))
                .map(UserMapper::toDTO);
    }

    public UserDTO getUserById(Long id, boolean includeDeleted) {
        if (includeDeleted) {
            requireAdmin();
        }

        User user = (includeDeleted
                ? userRepository.findById(id)
                : userRepository.findByIdAndDeletedFalse(id))
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireAdminOrSelf(user);
        return UserMapper.toDTO(user);
    }

    @Transactional
    public UserDTO closeUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireAdminOrSelf(user);

        if (user.isDeleted()) {
            return UserMapper.toDTO(user);
        }

        if (accountRepository.existsByUserIdAndActiveTrue(user.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User cannot be closed while any account is still open"
            );
        }

        List<Long> accountIds = accountRepository.findByUserId(user.getId())
                .stream()
                .map(Account::getId)
                .toList();

        if (!accountIds.isEmpty() &&
                accountRepository.existsPendingTransactionsForAccounts(accountIds, TransactionStatus.PENDING)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User cannot be closed while account transactions are pending"
            );
        }

        UserDTO before = UserMapper.toDTO(user);
        Instant deletedAt = Instant.now();
        user.setDeleted(true);
        user.setDeletedAt(deletedAt);
        user.setUsername(anonymizeUsername(user.getId(), deletedAt));
        user.setEmail(anonymizeEmail(user.getId(), deletedAt));

        User saved = userRepository.save(user);

        auditService.record(
                "CLOSE_USER",
                "USER",
                String.valueOf(saved.getId()),
                true,
                null,
                before,
                UserMapper.toDTO(saved)
        );

        return UserMapper.toDTO(saved);
    }

    @Transactional
    public UserDTO updateUser(Long id, UpdateUserDTO dto) {

        User user = userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        requireAdminOrSelf(user);
        String email = dto.email().trim().toLowerCase();
        String username = dto.username().trim().toLowerCase();

        if (userRepository.existsByUsernameAndIdNotAndDeletedFalse(username, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        if (userRepository.existsByEmailAndIdNotAndDeletedFalse(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        user.setUsername(username);
        user.setEmail(email);

        if (dto.roleId() != null) {
            if (!isAdmin(requireAuth())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
            }
            Role role = roleRepository.findById(dto.roleId())
                    .orElseThrow(() ->
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
            user.setRole(role);
        }

        return UserMapper.toDTO(userRepository.save(user));

    }

    private String anonymizeUsername(Long userId, Instant deletedAt) {
        return "closed_user_" + userId + "_" + deletedAt.toEpochMilli();
    }

    private String anonymizeEmail(Long userId, Instant deletedAt) {
        return "closed_user_" + userId + "_" + deletedAt.toEpochMilli() + "@deleted.local";
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
