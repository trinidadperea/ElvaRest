package com.elvarest.servidor.controllers;

import com.elvarest.servidor.entities.Usuario;
import com.elvarest.servidor.services.UsuarioService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController extends BaseController<Usuario, Long> {

    public UsuarioController(UsuarioService service) {
        super(service);
    }
}
