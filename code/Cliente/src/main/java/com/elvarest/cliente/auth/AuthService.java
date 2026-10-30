package com.elvarest.cliente.auth;

import com.elvarest.cliente.dto.UsuarioDTO;
import com.elvarest.cliente.dto.enums.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private String token; // JWT almacenado tras el login

    @Value("${api.user}")
    private String apiUser;

    @Value("${api.password}")
    private String apiPassword;

    // --- CLASES INTERNAS PARA MAPEAR LAS RESPUESTAS JSON ---

    // Para la respuesta de /auth/login
    public static class TokenResponse {
        private String token;
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    // Para la respuesta de /auth/perfil
    public static class PerfilResponse {
        private String rol;
        // Agrega getters y setters
        public String getRol() { return rol; }
        public void setRol(String rol) { this.rol = rol; }
    }

    // --- FIN CLASES INTERNAS ---


    public boolean login(String username, String password) {
        String loginUrl = "https://api.sprintproject.tech/auth/login";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(credentials, headers);

        try {
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(loginUrl, request, TokenResponse.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                return false;
            }

            this.token = response.getBody().getToken();
            String perfilUrl = "https://api.sprintproject.tech/auth/perfil"; //

            HttpHeaders authHeaders = authHeaders();
            HttpEntity<Void> perfilRequest = new HttpEntity<>(authHeaders);

            ResponseEntity<PerfilResponse> perfilResponse = restTemplate.exchange(
                    perfilUrl,
                    HttpMethod.GET,
                    perfilRequest,
                    PerfilResponse.class
            );

            if (perfilResponse.getStatusCode() != HttpStatus.OK || perfilResponse.getBody() == null) {
                this.token = null;
                return false;
            }

            String rol = perfilResponse.getBody().getRol();

            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol));

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            return true;

        } catch (Exception e) {
            // Un login fallido es un resultado esperado: se registra el motivo sin la traza.
            log.warn("Error de login o al obtener perfil: {}", e.getMessage());
            this.token = null;
        }
        return false;
    }

    public void logout() {
        this.token = null; //borra nuestro token
        SecurityContextHolder.clearContext();
    }

    public String getToken() {
        return token;
    }

    /**
     * Helper para obtener HttpHeaders con el Authorization Bearer token.
     * Útil para llamar a endpoints protegidos después de hacer login.
     */
    public HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(apiUser, apiPassword);
        return headers;
    }

    // --- Autenticación de los usuarios del frontend contra la API ---

    private static final String API_USUARIOS_URL = "http://localhost:8080/usuarios";

    /**
     * Valida las credenciales del login contra la API y devuelve el usuario real
     * (con su rol y su flag de cambio obligatorio). Devuelve null si son inválidas.
     * La cuenta de arranque del Servidor todavía no está en la tabla de usuarios:
     * mientras tanto ingresa como ADMIN sin flag de cambio.
     */
    public UsuarioDTO autenticar(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        if (!credencialesValidas(username, password)) {
            return null;
        }
        UsuarioDTO usuario = buscarUsuarioPorNombre(username);
        if (usuario == null) {
            usuario = new UsuarioDTO();
            usuario.setNombreUsuario(username);
            usuario.setRol(Rol.ADMIN);
            usuario.setDebeCambiarPassword(false);
        }
        return usuario;
    }

    /**
     * Verifica las credenciales contra la API: cualquier respuesta distinta de 401
     * significa que existen (403 es un usuario válido sin permisos de ADMIN).
     */
    private boolean credencialesValidas(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);
        try {
            restTemplate.exchange(API_USUARIOS_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return true;
        } catch (HttpClientErrorException e) {
            return e.getStatusCode() == HttpStatus.FORBIDDEN;
        } catch (Exception e) {
            log.warn("No se pudo validar el usuario '{}' contra la API: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Busca un usuario por nombreUsuario usando la cuenta de servicio de la API
     * (requiere que api.user tenga rol ADMIN). Devuelve null si no existe o falla la consulta.
     */
    public UsuarioDTO buscarUsuarioPorNombre(String nombreUsuario) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<UsuarioDTO[]> response = restTemplate.exchange(
                    API_USUARIOS_URL, HttpMethod.GET, entity, UsuarioDTO[].class);
            UsuarioDTO[] usuarios = response.getBody();
            if (usuarios == null) {
                return null;
            }
            for (UsuarioDTO usuario : usuarios) {
                if (nombreUsuario != null && nombreUsuario.equals(usuario.getNombreUsuario())) {
                    return usuario;
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo consultar la lista de usuarios: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Headers con las credenciales de un usuario concreto: se usan para el cambio de
     * contraseña propia, donde el Servidor resuelve el Principal del BasicAuth recibido.
     */
    public HttpHeaders authHeaders(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(username, password);
        return headers;
    }
}