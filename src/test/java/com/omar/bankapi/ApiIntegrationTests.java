package com.omar.bankapi;

import com.omar.bankapi.dto.*;
import com.omar.bankapi.model.Role;
import com.omar.bankapi.model.User;
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
import java.util.Map;
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
    private AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

        CreateUserDTO registerDto = new CreateUserDTO();
        registerDto.setUsername(username);
        registerDto.setEmail(email);
        registerDto.setPassword(password);

        ResponseEntity<UserDTO> registerResponse =
                restTemplate.postForEntity("/api/auth/register", registerDto, UserDTO.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(password);

        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, Map.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        String token = Optional.ofNullable(loginResponse.getBody().get("token"))
                .map(Object::toString)
                .orElse(null);
        assertThat(token).isNotBlank();

        Long userId = registerResponse.getBody().getId();
        List<com.omar.bankapi.model.Account> accounts = accountRepository.findByUserId(userId);
        assertThat(accounts).isNotEmpty();

        Long accountId = accounts.get(0).getId();

        TransactionAmountDTO depositDto = new TransactionAmountDTO();
        depositDto.setAmount(new BigDecimal("100.00"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

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
        assertThat(accountResponse.getBody().getBalance()).isNotNull();
        assertThat(accountResponse.getBody().getBalance().compareTo(new BigDecimal("100.00"))).isEqualTo(0);
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

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(password);

        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, Map.class);

        String token = Optional.ofNullable(loginResponse.getBody().get("token"))
                .map(Object::toString)
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
        assertThat(statsResponse.getBody().getTotalUsers()).isGreaterThanOrEqualTo(1);
        assertThat(statsResponse.getBody().getTotalAccounts()).isGreaterThanOrEqualTo(0);
        assertThat(statsResponse.getBody().getTotalTransactions()).isGreaterThanOrEqualTo(0);
        assertThat(statsResponse.getBody().getTotalBalances()).isNotNull();
    }

    @Test
    void userCanTransferToAnotherUserAccount() {
        TestUserContext sender = registerAndLogin("sender");
        TestUserContext receiver = registerAndLogin("receiver");

        deposit(sender.accountId(), sender.token(), new BigDecimal("100.00"));

        TransferDTO transferDTO = new TransferDTO();
        transferDTO.setReceiver(receiver.accountId());
        transferDTO.setAmount(new BigDecimal("40.00"));

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
        assertThat(senderAccountResponse.getBody().getBalance().compareTo(new BigDecimal("60.00"))).isEqualTo(0);

        ResponseEntity<AccountDTO> receiverAccountResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(receiver.token())),
                AccountDTO.class
        );

        assertThat(receiverAccountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receiverAccountResponse.getBody()).isNotNull();
        assertThat(receiverAccountResponse.getBody().getBalance().compareTo(new BigDecimal("40.00"))).isEqualTo(0);
    }

    @Test
    void transferFailsWhenInsufficientBalance() {
        TestUserContext sender = registerAndLogin("lowbal");
        TestUserContext receiver = registerAndLogin("target");

        TransferDTO transferDTO = new TransferDTO();
        transferDTO.setReceiver(receiver.accountId());
        transferDTO.setAmount(new BigDecimal("10.00"));

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

        TransferDTO transferDTO = new TransferDTO();
        transferDTO.setReceiver(user.accountId());
        transferDTO.setAmount(new BigDecimal("10.00"));

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

        ResponseEntity<AccountDTO> receiverAccountResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId(),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(receiver.token())),
                AccountDTO.class
        );
        assertThat(receiverAccountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receiverAccountResponse.getBody()).isNotNull();

        UpdateAccountDTO updateAccountDTO = new UpdateAccountDTO();
        updateAccountDTO.setIsActive(false);
        updateAccountDTO.setType(receiverAccountResponse.getBody().getType());

        ResponseEntity<AccountDTO> deactivateResponse = restTemplate.exchange(
                "/api/accounts/" + receiver.accountId(),
                HttpMethod.PUT,
                new HttpEntity<>(updateAccountDTO, authJsonHeaders(receiver.token())),
                AccountDTO.class
        );
        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        TransferDTO transferDTO = new TransferDTO();
        transferDTO.setReceiver(receiver.accountId());
        transferDTO.setAmount(new BigDecimal("10.00"));

        ResponseEntity<String> transferResponse = restTemplate.exchange(
                "/api/accounts/" + sender.accountId() + "/transfer",
                HttpMethod.POST,
                new HttpEntity<>(transferDTO, authJsonHeaders(sender.token())),
                String.class
        );
        assertThat(transferResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void deposit(Long accountId, String token, BigDecimal amount) {
        TransactionAmountDTO depositDto = new TransactionAmountDTO();
        depositDto.setAmount(amount);

        ResponseEntity<TransactionDTO> depositResponse = restTemplate.exchange(
                "/api/accounts/" + accountId + "/deposit",
                HttpMethod.POST,
                new HttpEntity<>(depositDto, authJsonHeaders(token)),
                TransactionDTO.class
        );

        assertThat(depositResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private TestUserContext registerAndLogin(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + suffix;
        String email = prefix + suffix + "@example.com";
        String password = "password123";

        CreateUserDTO registerDto = new CreateUserDTO();
        registerDto.setUsername(username);
        registerDto.setEmail(email);
        registerDto.setPassword(password);

        ResponseEntity<UserDTO> registerResponse =
                restTemplate.postForEntity("/api/auth/register", registerDto, UserDTO.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setUsername(username);
        loginDTO.setPassword(password);
        ResponseEntity<Map> loginResponse =
                restTemplate.postForEntity("/api/auth/login", loginDTO, Map.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();

        String token = Optional.ofNullable(loginResponse.getBody().get("token"))
                .map(Object::toString)
                .orElse(null);
        assertThat(token).isNotBlank();

        Long userId = registerResponse.getBody().getId();
        List<com.omar.bankapi.model.Account> accounts = accountRepository.findByUserId(userId);
        assertThat(accounts).isNotEmpty();

        return new TestUserContext(userId, accounts.get(0).getId(), token);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders authJsonHeaders(String token) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
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

    private record TestUserContext(Long userId, Long accountId, String token) {
    }
}
