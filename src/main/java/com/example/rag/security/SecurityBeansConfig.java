package com.example.rag.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;

import javax.sql.DataSource;

// Provides PasswordEncoder and UserDetailsService beans separately from SecurityConfig
// to break the circular dependency: SecurityConfig → JwtAuthFilter → UserDetailsService.
@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // JdbcUserDetailsManager uses the users/authorities tables created by schema.sql.
    // Replace this bean with a UserDetailsService that delegates to your monolith's
    // user module when integrating into a larger application.
    @Bean
    public JdbcUserDetailsManager userDetailsService(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }
}
