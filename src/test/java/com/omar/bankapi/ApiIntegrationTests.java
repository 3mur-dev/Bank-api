package com.omar.bankapi;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.AuditEvent;
import com.omar.bankapi.model.Role;
import com.omar.bankapi.model.User;
import com.omar.bankapi.exception.InvalidCredentialsException;
import com.omar.bankapi.exception.LoginRateLimitExceededException;
import com.omar.bankapi.model.enums.TransactionStatus;
import com.omar.bankapi.service.AuthService;
import com.omar.bankapi.service.LoginRateLimiter;
import com.omar.bankapi.repository.AuditEventRepository;
import com.omar.bankapi.repository.AccountRepository;
import com.omar.bankapi.repository.RoleRepository;
import com.omar.bankapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiIntegrationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void ensureRoles() {
        ensureRole("ROLE_USER");
        ensureRole("ROLE_ADMIN");
    }

    @Test
    void userCanRegisterLoginAndDeposit() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "user" + suffix;
        String email = "user" + suffix + "@example.com";
        String password = "password123";

        CreateUserDTO registerDto = new CreateUserDTO(username, email, password);

        ResponseEntity<UserDTO> registerResponse =
                restTemplate.postForEntity("/api/auth/register", registerDto, UserDTO.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();

        LoginDTO loginDTO = new LoginDTO(username, password);

        ResponseEntity<AuthResponseDTO> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, AuthResponseDTO.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        String token = Optional.ofNullable(loginResponse.getBody())
                .map(AuthResponseDTO::token)
                .orElse(null);
        assertThat(token).isNotBlank();

        Long userId = registerResponse.getBody().id();
        List<com.omar.bankapi.model.Account> accounts = accountRepository.findByUserId(userId);
        assertThat(accounts).isNotEmpty();

        Long accountId = accounts.get(0).getId();

        TransactionAmountDTO depositDto = new TransactionAmountDTO(new BigDecimal("100.00"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<TransactionAmountDTO> request = new HttpEntity<>(depositDto, headers);

        ResponseEntity<TransactionDTO> depositResponse =
                restTemplate.postForEntity("/api/accounts/" + accountId + "/deposit", request, TransactionDTO.class);

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(depositResponse.getBody()).isNotNull();

        ResponseEntity<AccountDTO> accountResponse = restTemplate.exchange(
                "/api/accounts/" + accountId,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                AccountDTO.class
        );
        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accountResponse.getBody()).isNotNull();
        assertThat(accountResponse.getBody().balance()).isNotNull();
        assertThat(accountResponse.getBody().balance().compareTo(new BigDecimal("100.00"))).isEqualTo(0);
    }

    @Test
    void homeEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Welcome to the Bank API");
    }

    @Test
    void actuatorHealthEndpointIsPublic() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void protectedEndpointReturnsStructuredUnauthorizedError() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/users", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Missing or invalid Authorization header");
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void validationErrorsUseStructuredErrorResponse() {
        CreateUserDTO invalidRequest = new CreateUserDTO("  ", "not-an-email", "123");

        ResponseEntity<ErrorResponse> response =
                restTemplate.postForEntity("/api/auth/register", invalidRequest, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().errors()).containsKeys("username", "email", "password");
    }

    @Test
    void forbiddenEndpointReturnsStructuredError() {
        TestUserContext user = registerAndLogin("forbidden");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/stats",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(user.token())),
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Forbidden");
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void adminCanReadStats() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "admin" + suffix;
        String email = "admin" + suffix + "@example.com";
        String password = "adminpass";

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(adminRole);
        userRepository.save(admin);

        LoginDTO loginDTO = new LoginDTO(username, password);

        ResponseEntity<AuthResponseDTO> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, AuthResponseDTO.class);

        String token = Optional.ofNullable(loginResponse.getBody())
                .map(AuthResponseDTO::token)
                .orElse(null);
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<AdminStatsDTO> statsResponse = restTemplate.exchange(
                "/api/admin/stats",
                HttpMethod.GET,
                request,
                AdminStatsDTO.class
        );

        assertThat(statsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statsResponse.getBody()).isNotNull();
        assertThat(statsResponse.getBody().totalUsers()).isGreaterThanOrEqualTo(1);
        assertThat(statsResponse.getBody().totalAccounts()).isGreaterThanOrEqualTo(0);
        assertThat(statsResponse.getBody().totalTransactions()).isGreaterThanOrEqualTo(0);
        assertThat(statsResponse.getBody().totalBalances()).isNotNull();
    }

    @Test
    void userCanTransferToAnotherUserAccount() {
        TestUserContext sender = registerAndLogin("sender");
        TestUserContext receiver = registerAndLogin("receiver");

        deposit(sender.accountId(), sender.token(), new BigDecimal("100.00"));

        TransferDTO transferDTO = new TransferDTO(receiver.accountId(), new BigDecimal("40.00"));

        HttpEntity<TransferDTO> transferRequest = new HttpEntity<>(transferDTO, authJsonHeaders(sender.token()));
        ResponseEntity<TransactionDTO> transferResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId() + "/transfer",
                HttpMethod.POST,
                transferRequest,
                TransactionDTO.class
        );

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferResponse.getBody()).isNotNull();

        ResponseEntity<AccountDTO> senderAccountResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(sender.token())),
                AccountDTO.class
        );
        assertThat(senderAccountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(senderAccountResponse.getBody()).isNotNull();
        assertThat(senderAccountResponse.getBody().balance().compareTo(new BigDecimal("60.00"))).isEqualTo(0);

        ResponseEntity<AccountDTO> receiverAccountResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(receiver.token())),
                AccountDTO.class
        );

        assertThat(receiverAccountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receiverAccountResponse.getBody()).isNotNull();
        assertThat(receiverAccountResponse.getBody().balance().compareTo(new BigDecimal("40.00"))).isEqualTo(0);
    }

    @Test
    void highValueTransferCreatesPendingTransactionWithoutMovingBalances() {
        TestUserContext sender = registerAndLogin("highsender");
        TestUserContext receiver = registerAndLogin("highreceiver");

        deposit(sender.accountId(), sender.token(), new BigDecimal("6000.00"));

        TransferDTO transferDTO = new TransferDTO(receiver.accountId(), new BigDecimal("5500.00"));

        ResponseEntity<TransactionDTO> transferResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId() + "/transfer",
                HttpMethod.POST,
                new HttpEntity<>(transferDTO, authJsonHeaders(sender.token())),
                TransactionDTO.class
        );

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferResponse.getBody()).isNotNull();
        assertThat(transferResponse.getBody().status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transferResponse.getBody().isFraud()).isTrue();

        ResponseEntity<AccountDTO> senderAccountResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(sender.token())),
                AccountDTO.class
        );
        ResponseEntity<AccountDTO> receiverAccountResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(receiver.token())),
                AccountDTO.class
        );

        assertThat(senderAccountResponse.getBody()).isNotNull();
        assertThat(receiverAccountResponse.getBody()).isNotNull();
        assertThat(senderAccountResponse.getBody().balance().compareTo(new BigDecimal("6000.00"))).isEqualTo(0);
        assertThat(receiverAccountResponse.getBody().balance().compareTo(BigDecimal.ZERO)).isEqualTo(0);
    }

    @Test
    void transferFailsWhenInsufficientBalance() {
        TestUserContext sender = registerAndLogin("lowbal");
        TestUserContext receiver = registerAndLogin("target");

        TransferDTO transferDTO = new TransferDTO(receiver.accountId(), new BigDecimal("10.00"));

        HttpEntity<TransferDTO> transferRequest = new HttpEntity<>(transferDTO, authJsonHeaders(sender.token()));
        ResponseEntity<String> transferResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId() + "/transfer",
                HttpMethod.POST,
                transferRequest,
                String.class
        );

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transferFailsWhenSenderAndReceiverAreSame() {
        TestUserContext user = registerAndLogin("sameacct");
        deposit(user.accountId(), user.token(), new BigDecimal("25.00"));

        TransferDTO transferDTO = new TransferDTO(user.accountId(), new BigDecimal("10.00"));

        HttpEntity<TransferDTO> transferRequest = new HttpEntity<>(transferDTO, authJsonHeaders(user.token()));
        ResponseEntity<String> transferResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/transfer",
                HttpMethod.POST,
                transferRequest,
                String.class
        );

        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transferFailsWhenReceiverIsInactive() {
        TestUserContext sender = registerAndLogin("active");
        TestUserContext receiver = registerAndLogin("inactive");

        deposit(sender.accountId(), sender.token(), new BigDecimal("100.00"));

        ResponseEntity<AccountDTO> closeResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId() + "/close",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(receiver.token())),
                AccountDTO.class
        );
        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody()).isNotNull();
        assertThat(closeResponse.getBody().active()).isFalse();

        TransferDTO transferDTO = new TransferDTO(receiver.accountId(), new BigDecimal("10.00"));

        ResponseEntity<String> transferResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId() + "/transfer",
                HttpMethod.POST,
                new HttpEntity<>(transferDTO, authJsonHeaders(sender.token())),
                String.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void accountWithTransactionHistoryCanBeClosed() {
        TestUserContext user = registerAndLogin("cannotdelete");
        deposit(user.accountId(), user.token(), new BigDecimal("20.00"));
        withdraw(user.accountId(), user.token(), new BigDecimal("20.00"));

        ResponseEntity<AccountDTO> closeResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/close",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(user.token())),
                AccountDTO.class
        );

        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeResponse.getBody()).isNotNull();
        assertThat(closeResponse.getBody().active()).isFalse();

        ResponseEntity<AccountDTO> accountResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(user.token())),
                AccountDTO.class
        );
        assertThat(accountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accountResponse.getBody()).isNotNull();
        assertThat(accountResponse.getBody().active()).isFalse();
    }

    @Test
    void accountCloseFailsWhenBalanceIsNotZero() {
        TestUserContext user = registerAndLogin("nonzero");
        deposit(user.accountId(), user.token(), new BigDecimal("5.00"));

        ResponseEntity<ErrorResponse> closeResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/close",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(user.token())),
                ErrorResponse.class
        );

        assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(closeResponse.getBody()).isNotNull();
        assertThat(closeResponse.getBody().message()).isEqualTo("Account with a non-zero balance cannot be closed");
    }

    @Test
    void closedUserCannotAuthenticate() {
        TestUserContext user = registerAndLogin("closable");
        TestAdminContext admin = registerAndLoginAdmin("closeadmin");

        ResponseEntity<AccountDTO> closeAccountResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/close",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(user.token())),
                AccountDTO.class
        );
        assertThat(closeAccountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<UserDTO> closeUserResponse = restTemplate.exchange(
                "/api/users/" + user.userId() + "/close",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(admin.token())),
                UserDTO.class
        );
        assertThat(closeUserResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closeUserResponse.getBody()).isNotNull();
        assertThat(closeUserResponse.getBody().deleted()).isTrue();
        assertThat(closeUserResponse.getBody().deletedAt()).isNotNull();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> authService.login(new LoginDTO(user.username(), user.password()), "127.0.0.1")
        ).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void coreActionsAreAudited() {
        TestUserContext user = registerAndLogin("audit");
        deposit(user.accountId(), user.token(), new BigDecimal("15.00"));

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).isNotEmpty();
        assertThat(auditEvents)
                .extracting(AuditEvent::getAction)
                .contains("REGISTER", "LOGIN", "DEPOSIT");
    }

    @Test
    void depositIsIdempotentForSameKey() {
        TestUserContext user = registerAndLogin("idem");
        String idempotencyKey = UUID.randomUUID().toString();

        TransactionAmountDTO depositDto = new TransactionAmountDTO(new BigDecimal("50.00"));
        HttpHeaders headers = authJsonHeaders(user.token(), idempotencyKey);
        HttpEntity<TransactionAmountDTO> request = new HttpEntity<>(depositDto, headers);

        ResponseEntity<TransactionDTO> firstResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/deposit",
                HttpMethod.POST,
                request,
                TransactionDTO.class
        );

        ResponseEntity<TransactionDTO> secondResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/deposit",
                HttpMethod.POST,
                request,
                TransactionDTO.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().id()).isEqualTo(firstResponse.getBody().id());

        ResponseEntity<AccountDTO> accountResponse = restTemplate.exchange(
                "/api/accounts/" + user.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(user.token())),
                AccountDTO.class
        );

        assertThat(accountResponse.getBody()).isNotNull();
        assertThat(accountResponse.getBody().balance().compareTo(new BigDecimal("50.00"))).isEqualTo(0);
    }

    @Test
    void approveTransactionIsIdempotentForSameKey() {
        TestUserContext sender = registerAndLogin("apsnd");
        TestUserContext receiver = registerAndLogin("aprcv");
        TestAdminContext admin = registerAndLoginAdmin("approv");

        deposit(sender.accountId(), sender.token(), new BigDecimal("6000.00"));

        TransferDTO transferDTO = new TransferDTO(receiver.accountId(), new BigDecimal("5500.00"));
        ResponseEntity<TransactionDTO> pendingTransferResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId() + "/transfer",
                HttpMethod.POST,
                new HttpEntity<>(transferDTO, authJsonHeaders(sender.token())),
                TransactionDTO.class
        );

        assertThat(pendingTransferResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendingTransferResponse.getBody()).isNotNull();
        assertThat(pendingTransferResponse.getBody().status()).isEqualTo(TransactionStatus.PENDING);

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders headers = authHeaders(admin.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Idempotency-Key", idempotencyKey);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<TransactionDTO> firstResponse = restTemplate.exchange(
                "/api/transactions/" + pendingTransferResponse.getBody().id() + "/approve",
                HttpMethod.POST,
                request,
                TransactionDTO.class
        );

        ResponseEntity<TransactionDTO> secondResponse = restTemplate.exchange(
                "/api/transactions/" + pendingTransferResponse.getBody().id() + "/approve",
                HttpMethod.POST,
                request,
                TransactionDTO.class
        );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().id()).isEqualTo(firstResponse.getBody().id());
        assertThat(secondResponse.getBody().status()).isEqualTo(TransactionStatus.CONFIRMED);

        ResponseEntity<AccountDTO> senderAccountResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(sender.token())),
                AccountDTO.class
        );
        ResponseEntity<AccountDTO> receiverAccountResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(receiver.token())),
                AccountDTO.class
        );

        assertThat(senderAccountResponse.getBody()).isNotNull();
        assertThat(receiverAccountResponse.getBody()).isNotNull();
        assertThat(senderAccountResponse.getBody().balance().compareTo(new BigDecimal("500.00"))).isEqualTo(0);
        assertThat(receiverAccountResponse.getBody().balance().compareTo(new BigDecimal("5500.00"))).isEqualTo(0);
    }

    @Test
    void invalidIdempotencyKeyIsRejected() {
        TestUserContext user = registerAndLogin("invalidkey");
        TransactionAmountDTO depositDto = new TransactionAmountDTO(new BigDecimal("10.00"));

        HttpHeaders headers = authHeaders(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Idempotency-Key", "bad key");

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/deposit",
                HttpMethod.POST,
                new HttpEntity<>(depositDto, headers),
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        TestUserContext user = registerAndLogin("missingkey");
        TransactionAmountDTO depositDto = new TransactionAmountDTO(new BigDecimal("10.00"));

        HttpHeaders headers = authHeaders(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/accounts/" + user.accountId() + "/deposit",
                HttpMethod.POST,
                new HttpEntity<>(depositDto, headers),
                ErrorResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Idempotency-Key header is required");
    }

    @Test
    void loginIsRateLimitedAfterRepeatedFailures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ratelimit" + suffix;
        String email = "ratelimit" + suffix + "@example.com";
        String password = "password123";

        CreateUserDTO registerDto = new CreateUserDTO(username, email, password);
        ResponseEntity<UserDTO> registerResponse =
                restTemplate.postForEntity("/api/auth/register", registerDto, UserDTO.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LoginDTO badLogin = new LoginDTO(username, "wrong-password");

        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> authService.login(badLogin, "127.0.0.1")
            ).isInstanceOf(InvalidCredentialsException.class);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> authService.login(badLogin, "127.0.0.1")
            ).isInstanceOf(InvalidCredentialsException.class);

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> authService.login(badLogin, "127.0.0.1")
            ).isInstanceOf(LoginRateLimitExceededException.class)
                    .hasMessage("Too many login attempts. Please try again later.");
        } finally {
            loginRateLimiter.reset(username, "127.0.0.1");
        }
    }

    private void deposit(Long accountId, String token, BigDecimal amount) {
        TransactionAmountDTO depositDto = new TransactionAmountDTO(amount);

        ResponseEntity<TransactionDTO> depositResponse = restTemplate.exchange(
                "/api/accounts/" + accountId + "/deposit",
                HttpMethod.POST,
                new HttpEntity<>(depositDto, authJsonHeaders(token)),
                TransactionDTO.class
        );

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void withdraw(Long accountId, String token, BigDecimal amount) {
        TransactionAmountDTO withdrawDto = new TransactionAmountDTO(amount);

        ResponseEntity<TransactionDTO> withdrawResponse = restTemplate.exchange(
                "/api/accounts/" + accountId + "/withdraw",
                HttpMethod.POST,
                new HttpEntity<>(withdrawDto, authJsonHeaders(token)),
                TransactionDTO.class
        );

        assertThat(withdrawResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TestUserContext registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + suffix;
        String email = prefix + suffix + "@example.com";
        String password = "password123";

        CreateUserDTO registerDto = new CreateUserDTO(username, email, password);

        ResponseEntity<UserDTO> registerResponse =
                restTemplate.postForEntity("/api/auth/register", registerDto, UserDTO.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();

        LoginDTO loginDTO = new LoginDTO(username, password);
        ResponseEntity<AuthResponseDTO> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, AuthResponseDTO.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();

        String token = Optional.ofNullable(loginResponse.getBody())
                .map(AuthResponseDTO::token)
                .orElse(null);
        assertThat(token).isNotBlank();

        Long userId = registerResponse.getBody().id();
        List<com.omar.bankapi.model.Account> accounts = accountRepository.findByUserId(userId);
        assertThat(accounts).isNotEmpty();

        return new TestUserContext(userId, accounts.get(0).getId(), token, username, password);
    }

    private TestAdminContext registerAndLoginAdmin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + suffix;
        String email = prefix + suffix + "@example.com";
        String password = "adminpass";

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();

        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(adminRole);
        userRepository.save(admin);

        LoginDTO loginDTO = new LoginDTO(username, password);
        ResponseEntity<AuthResponseDTO> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, AuthResponseDTO.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();

        String token = Optional.ofNullable(loginResponse.getBody())
                .map(AuthResponseDTO::token)
                .orElse(null);
        assertThat(token).isNotBlank();

        return new TestAdminContext(token);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders authJsonHeaders(String token) {
        return authJsonHeaders(token, UUID.randomUUID().toString());
    }

    private HttpHeaders authJsonHeaders(String token, String idempotencyKey) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private void ensureRole(String name) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        roleRepository.save(role);
    }

    private record TestUserContext(Long userId, Long accountId, String token, String username, String password) {
    }

    private record TestAdminContext(String token) {
    }
}
