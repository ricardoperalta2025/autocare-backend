package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.ProductoRequestDTO;
import cl.duoc.autocare.dto.ProductoResponseDTO;
import cl.duoc.autocare.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"catalogo-local", "railway"})
@RestController
@RequestMapping(ProductoController.RUTA)
@Tag(name = "Productos", description = "Consulta y gestión de productos del catálogo.")
public class ProductoController {

    static final String RUTA = "/api/v1/productos";

    private final ProductoService productos;

    public ProductoController(ProductoService productos) {
        this.productos = productos;
    }

    @Operation(summary = "Listar productos")
    @ApiResponse(responseCode = "200", description = "Listado de productos.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @GetMapping
    public List<ProductoResponseDTO> listar() {
        return productos.listar();
    }

    @Operation(summary = "Consultar el producto por ID")
    @ApiResponse(responseCode = "200", description = "Detalle del producto.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @GetMapping("/{id}")
    public ProductoResponseDTO buscar(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        return productos.buscar(id);
    }

    @Operation(summary = "Crear producto", description = "Solo ADMIN. El código debe ser único y la categoría debe existir. En Railway requiere escrituras habilitadas.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json", examples = @ExampleObject(name = "producto", value = """
                    {
                      "codigo": "ACE-PRUEBA",
                      "nombre": "Aceite sintético",
                      "precio": 35000,
                      "descuento": 20,
                      "stock": 24,
                      "categoriaId": "660000000000000000000001"
                    }
                    """))))
    @ApiResponse(responseCode = "201", description = "Producto creado.", useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "Ruta del recurso creado."))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(@Valid @RequestBody ProductoRequestDTO datos) {
        ProductoResponseDTO producto = productos.crear(datos);
        return ResponseEntity.created(URI.create(RUTA + "/" + producto.id())).body(producto);
    }

    @Operation(summary = "Actualizar producto", description = "Solo ADMIN. El descuento omitido conserva el vigente. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "200", description = "Producto actualizado.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @PutMapping("/{id}")
    public ProductoResponseDTO actualizar(
            @Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id,
            @Valid @RequestBody ProductoRequestDTO datos) {
        return productos.actualizar(id, datos);
    }

    @Operation(summary = "Eliminar producto", description = "Solo ADMIN. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "204", description = "Producto eliminado.", content = @Content)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        productos.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
