package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private static final String BASE = "autocare_ordenes_test_" + new ObjectId().toHexString();

    @DynamicPropertySource
    static void configurarMongoLocal(DynamicPropertyRegistry propiedades) {
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
        var listos = new CountDownLatch(2);
        var inicio = new CountDownLatch(1);
        Callable<HttpStatus> comprar = () -> {
            listos.countDown();
            if (!inicio.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No se iniciaron ambas solicitudes.");
            }
            try {
                compras.crear(new OrdenRequestDTO(List.of(linea(ultimo))));
                return HttpStatus.CREATED;
            } catch (CatalogoException error) {
                return error.estado();
            }
        };

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(comprar);
            var segunda = ejecutor.submit(comprar);
            assertTrue(listos.await(5, TimeUnit.SECONDS));
            inicio.countDown();
            var estados = List.of(primera.get(10, TimeUnit.SECONDS), segunda.get(10, TimeUnit.SECONDS));

            assertEquals(1, estados.stream().filter(HttpStatus.CREATED::equals).count());
            assertEquals(1, estados.stream().filter(HttpStatus.CONFLICT::equals).count());
        }
        assertEquals(0, stock(ultimo));
        assertEquals(ordenesAntes + 1, ordenes.count());
        assertEquals(new BigDecimal("28000"), compras.listar().getFirst().total());
    }

    @Test
    void revierteTodosLosDescuentosSiMongoRechazaLaOrden() {
        Producto primero = guardarProducto(2);
        Producto segundo = guardarProducto(3);
        long ordenesAntes = ordenes.count();

        fijarValidadorDeOrdenes(new Document("total", new Document("$exists", false)));
        try {
            var solicitud = new OrdenRequestDTO(List.of(linea(primero), linea(segundo)));
            CatalogoException error = assertThrows(CatalogoException.class, () -> compras.crear(solicitud));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.estado());

            assertEquals(2, stock(primero));
            assertEquals(3, stock(segundo));
            assertEquals(ordenesAntes, ordenes.count());
        } finally {
            fijarValidadorDeOrdenes(new Document());
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
