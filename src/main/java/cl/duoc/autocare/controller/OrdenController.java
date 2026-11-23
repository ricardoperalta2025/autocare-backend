package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.service.OrdenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("catalogo-local")
@RestController
@RequestMapping(OrdenController.RUTA)
@Tag(name = "Órdenes", description = "Compras simuladas y consulta de órdenes.")
@SecurityRequirement(name = "bearerAuth")
public class OrdenController {

    static final String RUTA = "/api/v1/ordenes";
    static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";

    private final OrdenService ordenes;

    public OrdenController(OrdenService ordenes) {
        this.ordenes = ordenes;
    }

    @Operation(summary = "Registrar una compra simulada",
            description = "Envía un ID por producto y su cantidad. Reutiliza Idempotency-Key al reintentar la misma compra.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json", examples = @ExampleObject(name = "orden", value = """
                    {"productos": [{"productoId": "660000000000000000000001", "cantidad": 2}]}
                    """))))
    @ApiResponse(responseCode = "201", description = "Orden registrada o confirmación repetida de la misma orden.",
            useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "Ruta de la orden creada."))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoOrden")
    @ApiResponse(responseCode = "401", description = "Se requiere un JWT válido.")
    @ApiResponse(responseCode = "403", description = "Rol sin permiso de compra o clave de otro comprador.")
    @ApiResponse(responseCode = "503", ref = "#/components/responses/CompraSinConfirmacion")
    @PostMapping
    public ResponseEntity<OrdenResponseDTO> crear(
            @Parameter(required = true, description = "Clave del intento. Debe reutilizarse con los mismos productos y cantidades.",
                    schema = @Schema(type = "string", pattern = "^[0-9a-fA-F]{24}$"), example = "660000000000000000000007")
            @RequestHeader(name = CABECERA_IDEMPOTENCIA, required = false) String clave,
            @Valid @RequestBody OrdenRequestDTO solicitud,
            @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        if (clave == null || !ObjectId.isValid(clave)) {
            throw new CatalogoException(HttpStatus.BAD_REQUEST,
                    "La cabecera Idempotency-Key debe tener 24 caracteres hexadecimales.");
        }
        OrdenResponseDTO orden = ordenes.crear(new ObjectId(clave), solicitud, new ObjectId(usuario.id()));
        return ResponseEntity.created(URI.create(RUTA + "/" + orden.id())).body(orden);
    }

    @Operation(summary = "Consultar una orden por ID")
    @ApiResponse(responseCode = "200", description = "Detalle de la orden.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @GetMapping("/{id}")
    public OrdenResponseDTO buscar(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id,
                                   @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return ordenes.buscar(id, filtrarPorCliente(usuario));
    }

    @Operation(summary = "Listar órdenes de la más reciente a la más antigua")
    @ApiResponse(responseCode = "200", description = "Listado de órdenes.", useReturnTypeSchema = true)
    @GetMapping
    public List<OrdenResponseDTO> listar(@AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return ordenes.listar(filtrarPorCliente(usuario));
    }

    private ObjectId filtrarPorCliente(UsuarioResponseDTO usuario) {
        return usuario.rol() == RolUsuario.CLIENTE ? new ObjectId(usuario.id()) : null;
    }
}
