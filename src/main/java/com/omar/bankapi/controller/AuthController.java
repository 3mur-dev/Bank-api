package com.omar.bankapi.controller;

import com.omar.bankapi.dto.CreateUserDTO;
import com.omar.bankapi.dto.LoginDTO;
import com.omar.bankapi.dto.UserDTO;
import com.omar.bankapi.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@AllArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    final private UserService userService;

    @PostMapping("/register")
   public ResponseEntity<UserDTO> createUser(@Valid @RequestBody CreateUserDTO dto) {

      UserDTO createdUser = userService.register(dto);

       URI location = URI.create("/api/users/" + createdUser.getId());

       return ResponseEntity
             .created(location)
             .body(createdUser);
   }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginDTO dto) {
        Map<String, String> tokenMap = userService.login(dto);
        return ResponseEntity.ok(tokenMap);
    }

}
