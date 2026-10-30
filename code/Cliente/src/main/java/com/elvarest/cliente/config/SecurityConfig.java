package com.elvarest.cliente.config;

import com.elvarest.cliente.auth.AuthService;
import com.elvarest.cliente.auth.LoginSuccessHandler;
import com.elvarest.cliente.dto.UsuarioDTO;
import com.elvarest.cliente.dto.enums.Rol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Sin cuentas locales: el login se valida contra la API del Servidor. El bean vacío
     * evita que Spring registre la cuenta por defecto de spring.security.user.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    /** Autentica las credenciales del login contra la API y arma la sesión con el rol real del usuario. */
    @Bean
    public AuthenticationProvider apiAuthenticationProvider(AuthService authService) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String username = authentication.getName();
                String password = authentication.getCredentials() == null
                        ? null : authentication.getCredentials().toString();

                UsuarioDTO usuario = authService.autenticar(username, password);
                if (usuario == null) {
                    throw new BadCredentialsException("Usuario o contraseña incorrectos");
                }
                Rol rol = usuario.getRol() != null ? usuario.getRol() : Rol.ADMIN;
                return new UsernamePasswordAuthenticationToken(username, password,
                        List.of(new SimpleGrantedAuthority("ROLE_" + rol.name())));
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    @Bean
    public AuthenticationSuccessHandler loginSuccessHandler(AuthService authService) {
        return new LoginSuccessHandler(authService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationProvider apiAuthenticationProvider,
                                                   AuthenticationSuccessHandler loginSuccessHandler) throws Exception {
        return http
                .authenticationProvider(apiAuthenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/adminlte/**", "/css/**", "/js/**").permitAll()
                        // Cambio de contraseña propia: cualquier usuario autenticado (lo exige el flag de primer ingreso)
                        .requestMatchers("/usuarios/cambiar-contrasena").authenticated()
                        // Gestión de usuarios (alta, baja, edición y restablecer): exclusiva del ADMIN
                        .requestMatchers("/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/tipos-certificado/**").hasRole("ADMIN")
                        // Alertas: cualquiera las consulta; crear, editar, resolver y eliminar es de ADMIN
                        .requestMatchers("/alertas/nuevo", "/alertas/alta", "/alertas/actualizar",
                                "/alertas/*/editar", "/alertas/modificar/**", "/alertas/baja/**",
                                "/alertas/*/marcar-leida").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(loginSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .build();
    }
}
