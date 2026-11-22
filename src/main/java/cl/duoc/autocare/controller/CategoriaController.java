package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.CategoriaRequestDTO;
import cl.duoc.autocare.dto.CategoriaResponseDTO;
import cl.duoc.autocare.service.CategoriaService;
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
public class CategoriaController {

    static final String RUTA = "/api/v1/categorias";

    private final CategoriaService categorias;

    public CategoriaController(CategoriaService categorias) {
        this.categorias = categorias;
    }

    @GetMapping
    public List<CategoriaResponseDTO> listar() {
        return categorias.listar();
    }

    @GetMapping("/{id}")
    public CategoriaResponseDTO buscar(@PathVariable ObjectId id) {
        return categorias.buscar(id);
    }

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> crear(@Valid @RequestBody CategoriaRequestDTO datos) {
        CategoriaResponseDTO categoria = categorias.crear(datos);
        return ResponseEntity.created(URI.create(RUTA + "/" + categoria.id())).body(categoria);
    }

    @PutMapping("/{id}")
    public CategoriaResponseDTO actualizar(@PathVariable ObjectId id, @Valid @RequestBody CategoriaRequestDTO datos) {
        return categorias.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable ObjectId id) {
        categorias.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
