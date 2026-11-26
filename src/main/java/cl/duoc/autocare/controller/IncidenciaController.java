package cl.duoc.autocare.controller;

import java.util.List;

import cl.duoc.autocare.dto.IncidenciaDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.service.IncidenciaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/incidencias")
@SecurityRequirement(name = "bearerAuth")
public class IncidenciaController {

    private final IncidenciaService incidencias;

    public IncidenciaController(IncidenciaService incidencias) {
        this.incidencias = incidencias;
    }

    @GetMapping
    public List<IncidenciaDTO.Respuesta> listar(@AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return incidencias.listar(usuario.rol() == RolUsuario.CLIENTE ? new ObjectId(usuario.id()) : null);
    }

    @PostMapping
    public ResponseEntity<IncidenciaDTO.Respuesta> crear(@Valid @RequestBody IncidenciaDTO.Solicitud datos,
                                                       @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidencias.crear(datos, new ObjectId(usuario.id())));
    }

    @PutMapping("/{id}/resolucion")
    public ResponseEntity<Void> resolver(@PathVariable ObjectId id) {
        incidencias.resolver(id);
        return ResponseEntity.noContent().build();
    }
}
