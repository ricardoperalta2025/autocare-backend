package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.Producto;
import cl.duoc.autocare.repository.OrdenRepository;
import cl.duoc.autocare.repository.ProductoRepository;
import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("catalogo-local")
public class OrdenService {

    private static final int CODIGO_CONFLICTO_DE_ESCRITURA = 112;

    private final OrdenRepository ordenes;
    private final ProductoRepository productos;
    private final MongoTemplate mongo;
    private final TransactionTemplate transacciones;

    public OrdenService(OrdenRepository ordenes, ProductoRepository productos, MongoTemplate mongo,
                        @Qualifier("mongoTransactionManager") PlatformTransactionManager gestor) {
        this.ordenes = ordenes;
        this.productos = productos;
        this.mongo = mongo;
        this.transacciones = new TransactionTemplate(gestor);
    }

    public OrdenResponseDTO crear(ObjectId idOrden, OrdenRequestDTO solicitud) {
        Map<ObjectId, Integer> cantidades = validarProductosSolicitados(solicitud);
        try {
            Orden orden = transacciones.execute(estado -> ordenes.findById(idOrden)
                    .map(existente -> exigirMismaCompra(existente, cantidades))
                    .orElseGet(() -> registrarCompra(idOrden, cantidades)));
            return aRespuesta(Objects.requireNonNull(orden));
        } catch (DataAccessException | TransactionException excepcion) {
            // La transacción terminó. Se consulta el resultado durable antes de responder.
            Orden confirmada;
            try {
                confirmada = buscarOrdenConfirmada(idOrden);
            } catch (DataAccessException fallaDeConsulta) {
                throw compraSinConfirmacion(idOrden);
            }
            if (confirmada != null) {
                return aRespuesta(exigirMismaCompra(confirmada, cantidades));
            }
            if (esConflictoDeEscritura(excepcion)) {
                throw new CatalogoException(HttpStatus.CONFLICT,
                        "Hubo un conflicto de escritura en el inventario. Si reintentas la misma compra, conserva la clave.");
            }
            throw compraSinConfirmacion(idOrden);
        }
    }

    public OrdenResponseDTO buscar(ObjectId id) {
        Orden orden = buscarOrdenConfirmada(id);
        if (orden == null) {
            throw new CatalogoException(HttpStatus.NOT_FOUND, "Orden no encontrada.");
        }
        return aRespuesta(orden);
    }

    private Orden buscarOrdenConfirmada(ObjectId id) {
        Query filtro = Query.query(Criteria.where("_id").is(id))
                .withReadPreference(ReadPreference.primary())
                .withReadConcern(ReadConcern.MAJORITY);
        return mongo.findOne(filtro, Orden.class);
    }

    private Orden exigirMismaCompra(Orden orden, Map<ObjectId, Integer> cantidades) {
        if (orden.productos().size() != cantidades.size() || orden.productos().stream()
                .anyMatch(linea -> !Objects.equals(cantidades.get(linea.productoId()), linea.cantidad()))) {
            throw new CatalogoException(HttpStatus.CONFLICT,
                    "La clave Idempotency-Key ya pertenece a una compra con otros productos o cantidades.");
        }
        return orden;
    }

    private CatalogoException compraSinConfirmacion(ObjectId id) {
        return new CatalogoException(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo confirmar el resultado de la orden " + id.toHexString()
                        + ". Consulta ese identificador. Si reintentas, usa la misma clave y los mismos productos y cantidades.");
    }

    public List<OrdenResponseDTO> listar() {
        return ordenes.findAll(Sort.by(Orden::fechaCreacion).descending()
                        .and(Sort.by(Orden::id).descending()))
                .stream().map(this::aRespuesta).toList();
    }

    private Map<ObjectId, Integer> validarProductosSolicitados(OrdenRequestDTO solicitud) {
        if (solicitud == null || solicitud.productos() == null || solicitud.productos().isEmpty()) {
            throw new CatalogoException(HttpStatus.BAD_REQUEST, "La orden debe incluir productos.");
        }

        Map<ObjectId, Integer> cantidades = new LinkedHashMap<>();
        for (OrdenRequestDTO.ProductoSolicitado linea : solicitud.productos()) {
            if (linea == null || linea.productoId() == null || !ObjectId.isValid(linea.productoId())
                    || linea.cantidad() == null || linea.cantidad() <= 0) {
                throw new CatalogoException(HttpStatus.BAD_REQUEST, "Cada producto requiere un ID válido y una cantidad positiva.");
            }
            if (cantidades.putIfAbsent(new ObjectId(linea.productoId()), linea.cantidad()) != null) {
                throw new CatalogoException(HttpStatus.BAD_REQUEST,
                        "Cada producto debe aparecer una sola vez en la orden.");
            }
        }
        return cantidades;
    }

    private Orden registrarCompra(ObjectId idOrden, Map<ObjectId, Integer> cantidades) {
        List<Orden.Detalle> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var linea : cantidades.entrySet()) {
            Orden.Detalle detalle = prepararDetalle(linea.getKey(), linea.getValue());
            detalles.add(detalle);
            total = total.add(detalle.subtotal());
        }

        for (Orden.Detalle detalle : detalles) {
            descontarStock(detalle);
        }

        return ordenes.insert(new Orden(idOrden, Instant.now().truncatedTo(ChronoUnit.MILLIS),
                Orden.Estado.PROCESANDO, detalles, total));
    }

    private Orden.Detalle prepararDetalle(ObjectId id, int cantidad) {
        Producto producto = productos.findById(id)
                .orElseThrow(() -> new CatalogoException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado: " + id.toHexString()));
        if (producto.stock() < cantidad) {
            throw stockInsuficiente(producto.codigo());
        }

        BigDecimal precioUnitario = PrecioProducto.calcularPrecioFinal(producto);
        return new Orden.Detalle(id, producto.codigo(), producto.nombre(), cantidad, producto.precio(),
                producto.descuento(), precioUnitario, precioUnitario.multiply(BigDecimal.valueOf(cantidad)));
    }

    private void descontarStock(Orden.Detalle detalle) {
        Query filtro = Query.query(Criteria.where("_id").is(detalle.productoId())
                .and("stock").gte(detalle.cantidad()));
        Update descuento = new Update().inc("stock", -detalle.cantidad());
        if (mongo.updateFirst(filtro, descuento, Producto.class).getMatchedCount() != 1) {
            throw stockInsuficiente(detalle.codigo());
        }
    }

    private CatalogoException stockInsuficiente(String codigo) {
        return new CatalogoException(HttpStatus.CONFLICT, "Stock insuficiente para el producto " + codigo + ".");
    }

    private boolean esConflictoDeEscritura(Throwable excepcion) {
        boolean conflicto = false;
        for (Throwable causa = excepcion; causa != null; causa = causa.getCause()) {
            if (causa instanceof MongoException mongoError) {
                if (mongoError.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) {
                    return false;
                }
                conflicto = conflicto || mongoError.getCode() == CODIGO_CONFLICTO_DE_ESCRITURA;
            }
        }
        return conflicto;
    }

    private OrdenResponseDTO aRespuesta(Orden orden) {
        List<OrdenResponseDTO.Detalle> detalles = orden.productos().stream()
                .map(linea -> new OrdenResponseDTO.Detalle(linea.productoId().toHexString(), linea.codigo(),
                        linea.nombre(), linea.cantidad(), linea.precioOriginal(), linea.descuento(),
                        linea.precioUnitario(), linea.subtotal()))
                .toList();
        return new OrdenResponseDTO(orden.id().toHexString(), orden.fechaCreacion(),
                orden.estado(), detalles, orden.total());
    }
}
