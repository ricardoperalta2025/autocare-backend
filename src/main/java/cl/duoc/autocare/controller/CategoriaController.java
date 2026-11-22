package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.CategoriaRequestDTO;
import cl.duoc.autocare.dto.CategoriaResponseDTO;
import cl.duoc.autocare.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Profile("catalogo-local")
@RestController
@RequestMapping(CategoriaController.RUTA)
@Tag(name = "Categorías", description = "Consulta y gestión de categorías del catálogo.")
public class CategoriaController {

    static final String RUTA = "/api/v1/categorias";

    private final CategoriaService categorias;

    public CategoriaController(CategoriaService categorias) {
        this.categorias = categorias;
    }

    @Operation(summary = "Listar categorías")
    @ApiResponse(responseCode = "200", description = "Listado de categorías.", useReturnTypeSchema = true)
    @GetMapping
    public List<CategoriaResponseDTO> listar() {
        return categorias.listar();
    }

    @Operation(summary = "Consultar la categoría por ID")
    @ApiResponse(responseCode = "200", description = "Detalle de la categoría.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @GetMapping("/{id}")
    public CategoriaResponseDTO buscar(@Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        return categorias.buscar(id);
    }

    @Operation(summary = "Crear categoría", description = "El nombre de la categoría debe ser único.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json", examples = @ExampleObject(name = "categoria", value = """
                    {"nombre": "Aceites de motor"}
                    """))))
    @ApiResponse(responseCode = "201", description = "Categoría creada.", useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "Ruta del recurso creado."))
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO datos) {
        CategoriaResponseDTO categoria = categorias.crear(datos);
        return ResponseEntity.created(URI.create(RUTA + "/" + categoria.id())).body(categoria);
    }

    @Operation(summary = "Actualizar categoría", description = "Renombra la categoría sin cambiar su identificador.")
    @ApiResponse(responseCode = "200", description = "Categoría actualizada.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @PutMapping("/{id}")
    public CategoriaResponseDTO actualizar(
            @Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id,
            @Valid @RequestBody CategoriaRequestDTO datos) {
        return categorias.actualizar(id, datos);
    }

    @Operation(summary = "Eliminar categoría", description = "Solo se puede eliminar una categoría sin productos asociados.")
    @ApiResponse(responseCode = "204", description = "Categoría eliminada.", content = @Content)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/RecursoNoEncontrado")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictoCatalogo")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(ref = "#/components/parameters/IdCatalogo") @PathVariable ObjectId id) {
        categorias.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
