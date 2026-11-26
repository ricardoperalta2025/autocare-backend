package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.ClienteAdminDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.service.OrdenService;
import cl.duoc.autocare.service.UsuarioAdminService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"catalogo-local", "railway"})
@RestController
@RequestMapping("/api/v1/usuarios")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarios;
    private final OrdenService ordenes;

    public UsuarioAdminController(UsuarioAdminService usuarios, OrdenService ordenes) {
        this.usuarios = usuarios;
        this.ordenes = ordenes;
    }

    @GetMapping
    public List<ClienteAdminDTO.Respuesta> listar() {
        return usuarios.listar();
    }

    @GetMapping("/{id}")
    public ClienteAdminDTO.Respuesta buscar(@PathVariable ObjectId id) {
        return usuarios.buscar(id);
    }

    @GetMapping("/{id}/ordenes")
    public List<OrdenResponseDTO> listarOrdenes(@PathVariable ObjectId id) {
        usuarios.buscar(id);
        return ordenes.listar(id);
    }

    @GetMapping("/me/perfil")
    public ClienteAdminDTO.Respuesta perfil(@AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return usuarios.perfil(new ObjectId(usuario.id()));
    }

    @PutMapping("/me/perfil")
    public ClienteAdminDTO.Respuesta editarPerfil(@Valid @RequestBody ClienteAdminDTO.Datos datos,
                                                   @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return usuarios.editarPerfil(new ObjectId(usuario.id()), datos);
    }

    @PostMapping
    public ResponseEntity<ClienteAdminDTO.Respuesta> crear(@Valid @RequestBody ClienteAdminDTO.Alta alta) {
        var cliente = usuarios.crear(alta);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + cliente.id())).body(cliente);
    }

    @PutMapping("/{id}")
    public ClienteAdminDTO.Respuesta editar(@PathVariable ObjectId id, @Valid @RequestBody ClienteAdminDTO.Datos datos) {
        return usuarios.editar(id, datos);
    }
}
