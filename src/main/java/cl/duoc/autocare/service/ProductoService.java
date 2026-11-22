package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import cl.duoc.autocare.dto.ProductoRequestDTO;
import cl.duoc.autocare.dto.ProductoResponseDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Categoria;
import cl.duoc.autocare.model.Producto;
import cl.duoc.autocare.repository.ProductoRepository;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProductoService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ProductoRepository productos;
    private final CategoriaService categorias;

    public ProductoService(ProductoRepository productos, CategoriaService categorias) {
        this.productos = productos;
        this.categorias = categorias;
    }

    public List<ProductoResponseDTO> listar() {
        synchronized (categorias) {
            List<Producto> catalogo = productos.findAll(Sort.by("id"));
            List<ObjectId> ids = catalogo.stream().map(Producto::categoriaId).distinct().toList();
            Map<ObjectId, Categoria> categoriasPorId = categorias.buscarDocumentos(ids).stream()
                    .collect(Collectors.toMap(Categoria::id, Function.identity()));
            return catalogo.stream()
                    .map(producto -> aRespuesta(producto, categoriasPorId.get(producto.categoriaId())))
                    .toList();
        }
    }

    public ProductoResponseDTO buscar(ObjectId id) {
        synchronized (categorias) {
            Producto producto = exigirProducto(id);
            Categoria categoria = categorias.buscarDocumento(producto.categoriaId()).orElse(null);
            return aRespuesta(producto, categoria);
        }
    }

    public ProductoResponseDTO crear(ProductoRequestDTO datos) {
        synchronized (categorias) {
            return guardar(null, datos);
        }
    }

    public ProductoResponseDTO actualizar(ObjectId id, ProductoRequestDTO datos) {
        synchronized (categorias) {
            return guardar(exigirProducto(id), datos);
        }
    }

    public void eliminar(ObjectId id) {
        synchronized (categorias) {
            exigirProducto(id);
            productos.deleteById(id);
        }
    }

    private Producto exigirProducto(ObjectId id) {
        return productos.findById(id)
                .orElseThrow(() -> new CatalogoException(HttpStatus.NOT_FOUND, "Producto no encontrado."));
    }

    private ProductoResponseDTO guardar(Producto productoActual, ProductoRequestDTO datos) {
        ObjectId id = productoActual == null ? null : productoActual.id();
        String codigoNormalizado = datos.codigo().toUpperCase(Locale.ROOT);
        String mensajeDuplicado = "Ya existe un producto con ese código.";
        if (productos.findByCodigoNormalizado(codigoNormalizado)
                .filter(producto -> !producto.id().equals(id)).isPresent()) {
            throw new CatalogoException(HttpStatus.CONFLICT, mensajeDuplicado);
        }

        ObjectId categoriaId = new ObjectId(datos.categoriaId());
        Categoria categoria = categorias.buscarDocumento(categoriaId)
                .orElseThrow(() -> new CatalogoException(HttpStatus.BAD_REQUEST, "La categoría indicada no existe."));
        BigDecimal descuento = datos.descuento();
        if (descuento == null) {
            descuento = productoActual == null ? BigDecimal.ZERO : productoActual.descuento();
        }
        int stockCritico = datos.stockCritico() == null ? 0 : datos.stockCritico();
        Producto producto = new Producto(id, datos.codigo(), codigoNormalizado, datos.nombre(),
                datos.descripcion(), datos.precio(), descuento, datos.stock(), stockCritico,
                datos.imagen(), categoriaId);

        try {
            return aRespuesta(productos.save(producto), categoria);
        } catch (DuplicateKeyException excepcion) {
            throw new CatalogoException(HttpStatus.CONFLICT, mensajeDuplicado);
        }
    }

    private ProductoResponseDTO aRespuesta(Producto producto, Categoria categoria) {
        if (categoria == null) {
            throw new CatalogoException(HttpStatus.CONFLICT, "El producto tiene una categoría inválida.");
        }
        return new ProductoResponseDTO(producto.id().toHexString(), producto.codigo(), producto.nombre(),
                producto.descripcion(), producto.precio(), producto.descuento(), calcularPrecioFinal(producto),
                producto.stock(), producto.stockCritico(), producto.imagen(),
                categoria.id().toHexString(), categoria.nombre());
    }

    private BigDecimal calcularPrecioFinal(Producto producto) {
        if (producto.descuento().signum() == 0) {
            return producto.precio();
        }
        return producto.precio().multiply(CIEN.subtract(producto.descuento()))
                .movePointLeft(2).setScale(0, RoundingMode.HALF_UP);
    }
}
