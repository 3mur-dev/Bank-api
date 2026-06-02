package com.omar.bankapi.config;

import com.omar.bankapi.model.Role;
import com.omar.bankapi.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class RoleDataInitializer {

    private final RoleRepository roleRepository;

    @Bean
    public ApplicationRunner seedRoles() {
        return args -> List.of("ROLE_USER", "ROLE_ADMIN").forEach(this::createRoleIfMissing);
    }

    private void createRoleIfMissing(String roleName) {
        if (roleRepository.findByName(roleName).isPresent()) {
            return;
        }

        Role role = new Role();
        role.setName(roleName);
        roleRepository.save(role);
    }
}
