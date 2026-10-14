package com.elvarest.servidor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/alertas/**",
                                "/cargos/**",
                                "/certificados/**",
                                "/concursos/**",
                                "/concursos-docentes/**",
                                "/cursos/**",
                                "/designaciones/**",
                                "/docentes/**",
                                "/licencias/**",
                                "/materias/**",
                                "/tipos-certificado/**",
                                "/reportes/**",
                                "/usuarios/**",
                                // Sin esto, cualquier excepción previa al controller (p. ej. JSON inválido)
                                // se redirige a /error y la seguridad la convierte en un 403 vacío.
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
