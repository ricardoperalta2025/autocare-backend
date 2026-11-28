package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.ClienteAdminDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.service.OrdenService;
import cl.duoc.autocare.service.UsuarioAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Clientes y perfil")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarios;
    private final OrdenService ordenes;

    public UsuarioAdminController(UsuarioAdminService usuarios, OrdenService ordenes) {
        this.usuarios = usuarios;
        this.ordenes = ordenes;
    }

    @Operation(summary = "Listar clientes", description = "Solo ADMIN. El listado incluye únicamente cuentas CLIENTE.")
    @ApiResponse(responseCode = "200", description = "Listado de clientes.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @GetMapping
    public List<ClienteAdminDTO.Respuesta> listar() {
        return usuarios.listar();
    }

    @Operation(summary = "Consultar un cliente", description = "Solo ADMIN. Consulta una cuenta con rol CLIENTE.")
    @ApiResponse(responseCode = "200", description = "Perfil del cliente.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @GetMapping("/{id}")
    public ClienteAdminDTO.Respuesta buscar(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        return usuarios.buscar(id);
    }

    @Operation(summary = "Listar el historial de compras de un cliente", description = "Solo ADMIN. Lista las órdenes del cliente sin incluir datos de entrega.")
    @ApiResponse(responseCode = "200", description = "Historial de órdenes.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @GetMapping("/{id}/ordenes")
    public List<OrdenResponseDTO> listarOrdenes(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        usuarios.buscar(id);
        return ordenes.listar(id);
    }

    @Operation(summary = "Consultar el perfil propio", description = "ADMIN o CLIENTE. La cuenta se obtiene del JWT.")
    @ApiResponse(responseCode = "200", description = "Perfil propio.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @GetMapping("/me/perfil")
    public ClienteAdminDTO.Respuesta perfil(@AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return usuarios.perfil(new ObjectId(usuario.id()));
    }

    @Operation(summary = "Editar el perfil propio", description = "ADMIN o CLIENTE. Solo cambia los datos del perfil. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/CorreoDuplicado")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/IndiceCorreoNoDisponible")
    @PutMapping("/me/perfil")
    public ClienteAdminDTO.Respuesta editarPerfil(@Valid @RequestBody ClienteAdminDTO.Datos datos,
                                                   @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return usuarios.editarPerfil(new ObjectId(usuario.id()), datos);
    }

    @Operation(summary = "Crear un cliente", description = "Solo ADMIN. Crea una cuenta CLIENTE. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "201", description = "Cliente creado.", useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "Ruta del cliente creado."))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/CorreoDuplicado")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/IndiceCorreoNoDisponible")
    @PostMapping
    public ResponseEntity<ClienteAdminDTO.Respuesta> crear(@Valid @RequestBody ClienteAdminDTO.Alta alta) {
        var cliente = usuarios.crear(alta);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/" + cliente.id())).body(cliente);
    }

    @Operation(summary = "Editar los datos de un cliente", description = "Solo ADMIN. Conserva el rol y la contraseña. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "200", description = "Cliente actualizado.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/CorreoDuplicado")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/IndiceCorreoNoDisponible")
    @PutMapping("/{id}")
    public ClienteAdminDTO.Respuesta editar(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id,
                                           @Valid @RequestBody ClienteAdminDTO.Datos datos) {
        return usuarios.editar(id, datos);
    }
}
