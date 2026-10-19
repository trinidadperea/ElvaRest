package com.elvarest.cliente.services;

import com.elvarest.cliente.auth.AuthService;
import com.elvarest.cliente.exceptions.ErrorServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public abstract class BaseService<T, ID> {

    protected final RestTemplate restTemplate = new RestTemplate();
    protected final String apiUrl;
    protected final Class<T> dtoClass;

    @Autowired
    protected AuthService authService; //para acceder al token JWT

    protected BaseService(String apiUrl, Class<T> dtoClass) {
        this.apiUrl = apiUrl;
        this.dtoClass = dtoClass;
    }

    public List<T> listarActivos() {
        HttpEntity<Void> entity = new HttpEntity<>(authService.authHeaders());

        ResponseEntity<T[]> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.GET,
                entity,
                getArrayType()
        );
        return Arrays.asList(response.getBody());
    }

    @SuppressWarnings("unchecked")
    private Class<T[]> getArrayType() {
        return (Class<T[]>) java.lang.reflect.Array.newInstance(dtoClass, 0).getClass();
    }

    public T obtener(ID id) {
        HttpEntity<Void> entity = new HttpEntity<>(authService.authHeaders());
        ResponseEntity<T> response = restTemplate.exchange(
                apiUrl + "/" + id,
                HttpMethod.GET,
                entity,
                dtoClass
        );
        return response.getBody();
    }

    public T alta(T dto) {
        HttpEntity<T> entity = new HttpEntity<>(dto, authService.authHeaders());
        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    dtoClass
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw errorDelServidor(e);
        }
    }

    public void modificar(ID id, T dto) {
        HttpEntity<T> entity = new HttpEntity<>(dto, authService.authHeaders());
        try {
            restTemplate.exchange(apiUrl + "/" + id, HttpMethod.PUT, entity, Void.class);
        } catch (HttpClientErrorException e) {
            throw errorDelServidor(e);
        }
    }

    public void bajaLogica(ID id) {
        HttpEntity<Void> entity = new HttpEntity<>(authService.authHeaders());
        try {
            restTemplate.exchange(apiUrl + "/" + id, HttpMethod.DELETE, entity, Void.class);
        } catch (HttpClientErrorException e) {
            throw errorDelServidor(e);
        }
    }

    /**
     * El servidor rechaza la operación (reglas de negocio, validaciones o errores de base de datos):
     * se propaga su mensaje para que el controlador lo muestre al usuario en lugar de un error genérico.
     */
    private RuntimeException errorDelServidor(HttpClientErrorException e) {
        String mensaje = mensajeDeError(new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8));
        if (mensaje != null && !mensaje.isBlank()) {
            return new ErrorServiceException(mensaje);
        }
        return e;
    }

    /**
     * Extrae el mensaje de la respuesta estándar de error del servidor ({"error":"..."}).
     * Parseo liviano, sin dependencias extra: el cuerpo de estos rechazos siempre tiene ese formato.
     */
    private String mensajeDeError(String cuerpo) {
        if (cuerpo == null) {
            return null;
        }
        String marca = "\"error\"";
        int clave = cuerpo.indexOf(marca);
        int dosPuntos = clave < 0 ? -1 : cuerpo.indexOf(':', clave + marca.length());
        int comillaInicial = dosPuntos < 0 ? -1 : cuerpo.indexOf('"', dosPuntos + 1);
        int comillaFinal = comillaInicial < 0 ? -1 : cuerpo.indexOf('"', comillaInicial + 1);
        if (comillaInicial < 0 || comillaFinal <= comillaInicial) {
            return null;
        }
        String mensaje = cuerpo.substring(comillaInicial + 1, comillaFinal);
        return mensaje.isBlank() ? null : mensaje;
    }

    protected void validar(T entidad) throws ErrorServiceException {}
    protected void preAlta(T entidad) throws ErrorServiceException {}
    protected void postAlta(T entidad)throws ErrorServiceException {}
    protected void preModificacion(T entidad)throws ErrorServiceException {}
    protected void preBaja(ID id)throws ErrorServiceException {}
}