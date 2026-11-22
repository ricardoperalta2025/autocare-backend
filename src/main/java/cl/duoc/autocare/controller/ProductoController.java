package cl.duoc.autocare.controller;

import java.net.URI;
import java.util.List;

import cl.duoc.autocare.dto.ProductoRequestDTO;
import cl.duoc.autocare.dto.ProductoResponseDTO;
import cl.duoc.autocare.service.ProductoService;
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
@RequestMapping(ProductoController.RUTA)
public class ProductoController {

    static final String RUTA = "/api/v1/productos";

    private final ProductoService productos;

    public ProductoController(ProductoService productos) {
        this.productos = productos;
    }

    @GetMapping
    public List<ProductoResponseDTO> listar() {
        return productos.listar();
    }

    @GetMapping("/{id}")
    public ProductoResponseDTO buscar(@PathVariable ObjectId id) {
        return productos.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(@Valid @RequestBody ProductoRequestDTO datos) {
        ProductoResponseDTO producto = productos.crear(datos);
        return ResponseEntity.created(URI.create(RUTA + "/" + producto.id())).body(producto);
    }

    @PutMapping("/{id}")
    public ProductoResponseDTO actualizar(@PathVariable ObjectId id, @Valid @RequestBody ProductoRequestDTO datos) {
        return productos.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable ObjectId id) {
        productos.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
