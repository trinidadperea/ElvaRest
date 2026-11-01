package com.elvarest.servidor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * RBAC por rutas y verbos HTTP. El orden de los requestMatchers es crítico: gana la primera
 * coincidencia, así que las reglas específicas y más permisivas van antes que las genéricas.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Un usuario puede cambiar su propia contraseña; el resto de /usuarios sigue siendo del ADMIN
                        .requestMatchers(HttpMethod.POST, "/usuarios/cambiar-contrasena").hasAnyRole("ADMIN", "USER")
                        // Gestión de usuarios: exclusiva del ADMIN (listado, alta, modificación, baja y restablecer)
                        .requestMatchers("/usuarios/**").hasRole("ADMIN")
                        // Auditoría (historial de cambios): solo lectura para el ADMIN
                        .requestMatchers("/auditoria/**").hasRole("ADMIN")
                        // Concursos, cargos y postulaciones: CRUD completo para ADMIN y USER
                        .requestMatchers("/concursos/**", "/cargos/**", "/concursos-docentes/**")
                        .hasAnyRole("ADMIN", "USER")
                        // Exportación Excel de reportes: solo ADMIN (debe ir antes de las reglas GET de /reportes)
                        .requestMatchers("/reportes/excel/**").hasRole("ADMIN")
                        // Reporte PDF de concurso: ADMIN y USER
                        .requestMatchers(HttpMethod.GET, "/reportes/concurso/**").hasAnyRole("ADMIN", "USER")
                        // Módulos restantes: solo lectura (GET) para USER; la escritura queda en el cierre
                        .requestMatchers(HttpMethod.GET, "/docentes/**", "/cursos/**", "/materias/**",
                                "/designaciones/**", "/licencias/**", "/alertas/**", "/certificados/**",
                                "/tipos-certificado/**", "/reportes/**")
                        .hasAnyRole("ADMIN", "USER")
                        // Cierre: cualquier otra petición (POST/PUT/PATCH/DELETE no listados) es solo de ADMIN
                        .anyRequest().hasRole("ADMIN")
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}