package com.elvarest.cliente.services;

import com.elvarest.cliente.dto.UsuarioDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;

@Service
public class UsuarioService extends BaseService<UsuarioDTO, Long> {

    private static final String API_URL = "http://localhost:8080/usuarios";

    public UsuarioService() {
        super(API_URL, UsuarioDTO.class);
    }

    /** Restablece la contraseña de un usuario: vuelve a ser su nombreUsuario (acción de ADMIN). */
    public void restablecerContraseña(Long id) {
        HttpEntity<Void> entity = new HttpEntity<>(authService.authHeaders());
        try {
            restTemplate.exchange(apiUrl + "/" + id + "/restablecer-contrasena", HttpMethod.POST, entity, Void.class);
        } catch (HttpClientErrorException e) {
            throw errorDelServidor(e);
        }
    }

    /**
     * Cambia la contraseña propia: se envía con las credenciales del usuario logueado para que
     * el Servidor resuelva el Principal correcto (no la cuenta de servicio de la API).
     */
    public void cambiarContraseña(String username, String password, String contraseñaActual, String contraseñaNueva) {
        Map<String, String> datos = new HashMap<>();
        datos.put("contraseñaActual", contraseñaActual);
        datos.put("contraseñaNueva", contraseñaNueva);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(datos, authService.authHeaders(username, password));
        try {
            restTemplate.exchange(apiUrl + "/cambiar-contrasena", HttpMethod.POST, entity, Void.class);
        } catch (HttpClientErrorException e) {
            throw errorDelServidor(e);
        }
    }
}
