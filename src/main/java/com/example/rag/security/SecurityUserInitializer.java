package com.example.rag.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Component;

// Seeds default users into the database on first startup.
// Uses userExists() before inserting so it is safe to run on every restart.
// In production: remove these defaults and manage users through your admin UI or user module.
@Component
public class SecurityUserInitializer {

    private static final Logger log = LoggerFactory.getLogger(SecurityUserInitializer.class);

    private final JdbcUserDetailsManager userDetailsManager;
    private final PasswordEncoder passwordEncoder;

    public SecurityUserInitializer(JdbcUserDetailsManager userDetailsManager,
                                   PasswordEncoder passwordEncoder) {
        this.userDetailsManager = userDetailsManager;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultUsers() {
        createIfAbsent("admin", "admin", "ADMIN");
        createIfAbsent("user",  "user",  "USER");
    }

    private void createIfAbsent(String username, String password, String role) {
        if (!userDetailsManager.userExists(username)) {
            userDetailsManager.createUser(
                    User.withUsername(username)
                            .password(passwordEncoder.encode(password))
                            .roles(role)
                            .build()
            );
            log.info("Created default user '{}' with role ROLE_{}", username, role);
        }
    }
}
