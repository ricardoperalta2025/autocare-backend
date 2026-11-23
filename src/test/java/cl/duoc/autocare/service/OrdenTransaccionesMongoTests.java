package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cl.duoc.autocare.JwtDePrueba;
import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenRequestDTO.ProductoSolicitado;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.Producto;
import cl.duoc.autocare.repository.OrdenRepository;
import cl.duoc.autocare.repository.ProductoRepository;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AUTOCARE_ORDENES_MONGO_LOCAL", matches = "true")
@ActiveProfiles("catalogo-local")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.data.mongodb.auto-index-creation=false",
        "logging.level.org.mongodb.driver=OFF"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrdenTransaccionesMongoTests {

    private static final ObjectId COMPRADOR = new ObjectId();
    private static final String BASE = "autocare_ordenes_test_" + new ObjectId().toHexString();

    @DynamicPropertySource
    static void configurarMongoLocal(DynamicPropertyRegistry propiedades) {
        JwtDePrueba.configurar(propiedades);
        propiedades.add("spring.mongodb.uri", () -> "mongodb://127.0.0.1:27018/" + BASE
                + "?replicaSet=rsAutocareTest&directConnection=true&serverSelectionTimeoutMS=3000");
    }

    @Autowired private MongoTemplate mongo;
    @Autowired private OrdenService compras;
    @Autowired private OrdenRepository ordenes;
    @Autowired private ProductoRepository productos;

    @BeforeAll
    void crearColecciones() {
        mongo.createCollection(Producto.class);
        mongo.createCollection(Orden.class);
    }

    @AfterAll
    void eliminarBaseDeEstaEjecucion() {
        mongo.getDb().drop();
    }

    @Test
    void confirmaUnaSolaCompraCuandoDosSolicitudesQuierenLaUltimaUnidad() throws Exception {
        Producto ultimo = guardarProducto(1);
        long ordenesAntes = ordenes.count();

        Callable<HttpStatus> comprar = () -> {
            try {
                compras.crear(new ObjectId(), new OrdenRequestDTO(List.of(linea(ultimo))), COMPRADOR);
                return HttpStatus.CREATED;
            } catch (CatalogoException error) {
                return error.estado();
            }
        };

        var estados = ejecutarEnParalelo(comprar);
        assertEquals(1, estados.stream().filter(HttpStatus.CREATED::equals).count());
        assertEquals(1, estados.stream().filter(HttpStatus.CONFLICT::equals).count());
        assertEquals(0, stock(ultimo));
        assertEquals(ordenesAntes + 1, ordenes.count());
        assertEquals(new BigDecimal("28000"), compras.listar(null).getFirst().total());
    }

    @Test
    void revierteTodosLosDescuentosSiMongoRechazaLaOrden() {
        Producto primero = guardarProducto(2);
        Producto segundo = guardarProducto(3);
        long ordenesAntes = ordenes.count();

        fijarValidadorDeOrdenes(new Document("total", new Document("$exists", false)));
        try {
            var solicitud = new OrdenRequestDTO(List.of(linea(primero), linea(segundo)));
            CatalogoException error = assertThrows(CatalogoException.class, () -> compras.crear(new ObjectId(), solicitud, COMPRADOR));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.estado());

            assertEquals(2, stock(primero));
            assertEquals(3, stock(segundo));
            assertEquals(ordenesAntes, ordenes.count());
        } finally {
            fijarValidadorDeOrdenes(new Document());
        }
    }

    @Test
    void dosSolicitudesConLaMismaClaveDescuentanStockUnaSolaVez() throws Exception {
        Producto producto = guardarProducto(5);
        ObjectId clave = new ObjectId();
        var solicitud = new OrdenRequestDTO(List.of(new ProductoSolicitado(producto.id().toHexString(), 2)));
        long ordenesAntes = ordenes.count();

        Callable<HttpStatus> comprar = () -> {
            try {
                compras.crear(clave, solicitud, COMPRADOR);
                return HttpStatus.CREATED;
            } catch (CatalogoException error) {
                return error.estado();
            }
        };

        var estados = ejecutarEnParalelo(comprar);
        assertTrue(estados.contains(HttpStatus.CREATED));
        assertTrue(estados.stream().allMatch(estado -> estado == HttpStatus.CREATED
                || estado == HttpStatus.CONFLICT || estado == HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(clave.toHexString(), compras.crear(clave, solicitud, COMPRADOR).id());
        assertEquals(3, stock(producto));
        assertEquals(ordenesAntes + 1, ordenes.count());
    }

    private static List<HttpStatus> ejecutarEnParalelo(Callable<HttpStatus> comprar) throws Exception {
        var listos = new CountDownLatch(2);
        var inicio = new CountDownLatch(1);
        Callable<HttpStatus> sincronizar = () -> {
            listos.countDown();
            if (!inicio.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No se iniciaron ambas solicitudes.");
            }
            return comprar.call();
        };

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(sincronizar);
            var segunda = ejecutor.submit(sincronizar);
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            inicio.countDown();
            return List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));
        }
    }

    private void fijarValidadorDeOrdenes(Document validador) {
        mongo.getDb().runCommand(new Document("collMod", mongo.getCollectionName(Orden.class))
                .append("validator", validador));
    }

    private Producto guardarProducto(int stock) {
        String codigo = "PRUEBA-" + new ObjectId().toHexString();
        return productos.insert(new Producto(null, codigo, codigo, "Aceite de prueba", "",
                new BigDecimal("35000"), new BigDecimal("20"), stock, 0, "", new ObjectId()));
    }

    private int stock(Producto producto) {
        return productos.findById(producto.id()).orElseThrow().stock();
    }

    private static ProductoSolicitado linea(Producto producto) {
        return new ProductoSolicitado(producto.id().toHexString(), 1);
    }
}
