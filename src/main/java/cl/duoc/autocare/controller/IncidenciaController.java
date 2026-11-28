package cl.duoc.autocare.controller;

import java.util.List;

import cl.duoc.autocare.dto.IncidenciaDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.service.IncidenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Incidencias")
@SecurityRequirement(name = "bearerAuth")
public class IncidenciaController {

    private final IncidenciaService incidencias;

    public IncidenciaController(IncidenciaService incidencias) {
        this.incidencias = incidencias;
    }

    @Operation(summary = "Listar incidencias", description = "ADMIN consulta todas. CLIENTE consulta únicamente las propias.")
    @ApiResponse(responseCode = "200", description = "Listado de incidencias.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @GetMapping
    public List<IncidenciaDTO.Respuesta> listar(@AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return incidencias.listar(usuario.rol() == RolUsuario.CLIENTE ? new ObjectId(usuario.id()) : null);
    }

    @Operation(summary = "Crear una incidencia", description = "Solo CLIENTE, sobre una orden propia. Se inicia en PENDIENTE. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "201", description = "Incidencia creada.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @PostMapping
    public ResponseEntity<IncidenciaDTO.Respuesta> crear(@Valid @RequestBody IncidenciaDTO.Solicitud datos,
                                                       @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidencias.crear(datos, new ObjectId(usuario.id())));
    }

    @Operation(summary = "Resolver una incidencia", description = "Solo ADMIN. Mantiene la respuesta administrativa y registra la fecha de actualización. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "204", description = "Incidencia resuelta.", content = @Content)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @PutMapping("/{id}/resolucion")
    public ResponseEntity<Void> resolver(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        incidencias.resolver(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Gestionar el estado y la respuesta de una incidencia",
            description = "Solo ADMIN. Permite conservar o avanzar el estado de PENDIENTE a EN_REVISION o RESUELTA. "
                    + "RESUELTA admite editar la respuesta. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "200", description = "Incidencia actualizada.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/EstadoIncompatible")
    @PutMapping("/{id}/gestion")
    public IncidenciaDTO.Respuesta actualizar(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id,
                                              @Valid @RequestBody IncidenciaDTO.Gestion datos) {
        return incidencias.actualizar(id, datos);
    }
}
