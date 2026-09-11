package com.elvarest.cliente.controllers;

import com.elvarest.cliente.dto.UsuarioDTO;
import com.elvarest.cliente.services.UsuarioService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController extends BaseController<UsuarioDTO, Long> {

    public UsuarioController(UsuarioService service) {
        super(service);
        initController(new UsuarioDTO(), "Lista de usuarios", "Editar usuario", "usuarios/");
    }
}
