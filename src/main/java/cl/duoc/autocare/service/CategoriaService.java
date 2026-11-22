package cl.duoc.autocare.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import cl.duoc.autocare.dto.CategoriaRequestDTO;
import cl.duoc.autocare.dto.CategoriaResponseDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Categoria;
import cl.duoc.autocare.repository.CategoriaRepository;
import cl.duoc.autocare.repository.ProductoRepository;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CategoriaService {

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;

    public CategoriaService(CategoriaRepository categorias, ProductoRepository productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    public List<CategoriaResponseDTO> listar() {
        return categorias.findAll(Sort.by("nombreNormalizado")).stream()
                .map(this::aRespuesta)
                .toList();
    }

    public CategoriaResponseDTO buscar(ObjectId id) {
        return aRespuesta(exigirCategoria(id));
    }

    // ProductoService comparte este monitor para impedir que se borre una categoría durante la asignación.
    public synchronized CategoriaResponseDTO crear(CategoriaRequestDTO datos) {
        return guardar(null, datos);
    }

    public synchronized CategoriaResponseDTO actualizar(ObjectId id, CategoriaRequestDTO datos) {
        exigirCategoria(id);
        return guardar(id, datos);
    }

    public synchronized void eliminar(ObjectId id) {
        exigirCategoria(id);
        if (productos.existsByCategoriaId(id)) {
            throw new CatalogoException(HttpStatus.CONFLICT, "La categoría tiene productos asociados.");
        }
        categorias.deleteById(id);
    }

    Optional<Categoria> buscarDocumento(ObjectId id) {
        return categorias.findById(id);
    }

    List<Categoria> buscarDocumentos(Iterable<ObjectId> ids) {
        return categorias.findAllById(ids);
    }

    private Categoria exigirCategoria(ObjectId id) {
        return buscarDocumento(id)
                .orElseThrow(() -> new CatalogoException(HttpStatus.NOT_FOUND, "Categoría no encontrada."));
    }

    private CategoriaResponseDTO guardar(ObjectId id, CategoriaRequestDTO datos) {
        String nombreNormalizado = datos.nombre().toLowerCase(Locale.ROOT);
        String mensajeDuplicado = "Ya existe una categoría con ese nombre.";
        if (categorias.findByNombreNormalizado(nombreNormalizado)
                .filter(categoria -> !categoria.id().equals(id)).isPresent()) {
            throw new CatalogoException(HttpStatus.CONFLICT, mensajeDuplicado);
        }

        try {
            return aRespuesta(categorias.save(new Categoria(id, datos.nombre(), nombreNormalizado)));
        } catch (DuplicateKeyException excepcion) {
            throw new CatalogoException(HttpStatus.CONFLICT, mensajeDuplicado);
        }
    }

    private CategoriaResponseDTO aRespuesta(Categoria categoria) {
        return new CategoriaResponseDTO(categoria.id().toHexString(), categoria.nombre());
    }
}
