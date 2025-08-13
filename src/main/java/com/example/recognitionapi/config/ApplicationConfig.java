package com.example.recognitionapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class ApplicationConfig {

    private static final String ROLES_CLAIM = "URL_Here";

    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapper() {
        return (authorities) -> {
            Set<GrantedAuthority> mappedAuthorities = new HashSet<>();

            authorities.forEach(authority -> {
                if (authority instanceof OidcUserAuthority oidcUserAuthority) {
                    List<String> groups = oidcUserAuthority.getAttributes().get(ROLES_CLAIM) != null ?
                            (List<String>) oidcUserAuthority.getAttributes().get(ROLES_CLAIM) :
                            List.of();

                    // Mapping IdP group names to the application's roles
                    if (groups.contains("Admin")) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    if (groups.contains("HR")) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_HR"));
                    }
                    if (groups.contains("Manager")) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
                    }
                    if (groups.contains("Employee")) {
                        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
                    }
                }
                // Adding a default role for any authenticated user
                mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
            });

            return mappedAuthorities;
        };
    }
}

