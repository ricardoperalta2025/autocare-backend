package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cl.duoc.autocare.JwtDePrueba;
import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.ClienteAdminDTO;
import cl.duoc.autocare.dto.ContactoRequestDTO;
import cl.duoc.autocare.dto.EntregaOrdenRequestDTO;
import cl.duoc.autocare.dto.IncidenciaDTO;
import cl.duoc.autocare.model.Incidencia;
import cl.duoc.autocare.model.MensajeContacto;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UsuarioAdminService clientes;
    @Autowired private IncidenciaService incidencias;
    @Autowired private ContactoService contacto;

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


    @Test
    void persisteEntregaConLaOrdenYRechazaOtroDespachoParaLaMismaClave() {
        Producto producto = guardarProducto(3);
        ObjectId clave = new ObjectId();
        var entrega = new EntregaOrdenRequestDTO("Cliente", "cliente@example.test", "900000000", "Santiago",
                "Calle de prueba 10", "Envío estándar");
        var solicitud = new OrdenRequestDTO(List.of(linea(producto)), entrega);
        compras.crear(clave, solicitud, COMPRADOR);
        assertEquals(entrega.aDocumento(), ordenes.findById(clave).orElseThrow().entrega());
        assertEquals(entrega.aDocumento(), compras.buscar(clave, COMPRADOR).entrega());
        assertEquals(clave.toHexString(), compras.crear(clave, solicitud, COMPRADOR).id());
        var otraEntrega = new EntregaOrdenRequestDTO("Cliente", "cliente@example.test", "900000000", "Santiago",
                "Otra dirección", "Envío estándar");
        assertEquals(HttpStatus.CONFLICT, assertThrows(CatalogoException.class,
                () -> compras.crear(clave, new OrdenRequestDTO(List.of(linea(producto)), otraEntrega), COMPRADOR)).estado());
        compras.marcarEnviada(clave);
        compras.marcarEnviada(clave);
        assertEquals(Orden.Estado.ENVIADO, compras.buscar(clave, COMPRADOR).estado());
        assertEquals(2, stock(producto));
    }

    @Test
    void incidenciaSeCreaEnMongoSeFiltraPorClienteYSeResuelve() {
        ObjectId duenio = new ObjectId();
        ObjectId ordenId = new ObjectId();
        compras.crear(ordenId, new OrdenRequestDTO(List.of(linea(guardarProducto(1)))), duenio);
        var datos = new IncidenciaDTO.Solicitud(ordenId.toHexString(), "Seguimiento", "Solicito información.");
        ObjectId otraCuenta = new ObjectId();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CatalogoException.class,
                () -> incidencias.crear(datos, otraCuenta)).estado());
        var creada = incidencias.crear(datos, duenio);
        assertEquals(Incidencia.Estado.PENDIENTE, creada.estado());
        assertTrue(incidencias.listar(otraCuenta).isEmpty());
        assertEquals(creada.id(), incidencias.listar(duenio).getFirst().id());
        ObjectId id = new ObjectId(creada.id());
        // Simula un documento guardado antes de incorporar los campos opcionales.
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new Update().unset("respuestaAdmin").unset("fechaActualizacion"), Incidencia.class);
        assertNull(incidencias.listar(duenio).getFirst().respuestaAdmin());
        assertNull(incidencias.listar(duenio).getFirst().fechaActualizacion());
        incidencias.actualizar(id, new IncidenciaDTO.Gestion("EN_REVISION", "En consulta."));
        assertEquals(Incidencia.Estado.EN_REVISION, incidencias.listar(duenio).getFirst().estado());
        assertEquals("En consulta.", incidencias.listar(duenio).getFirst().respuestaAdmin());
        assertNotNull(incidencias.listar(duenio).getFirst().fechaActualizacion());
        assertEquals(HttpStatus.CONFLICT, assertThrows(CatalogoException.class,
                () -> incidencias.actualizar(id, new IncidenciaDTO.Gestion("PENDIENTE", null))).estado());
        incidencias.resolver(id);
        var resuelta = incidencias.listar(duenio).getFirst();
        assertEquals(Incidencia.Estado.RESUELTA, resuelta.estado());
        assertEquals("En consulta.", resuelta.respuestaAdmin());
        incidencias.actualizar(id, new IncidenciaDTO.Gestion("RESUELTA", "Consulta finalizada."));
        assertEquals("Consulta finalizada.", incidencias.listar(duenio).getFirst().respuestaAdmin());
        assertEquals(HttpStatus.CONFLICT, assertThrows(CatalogoException.class,
                () -> incidencias.actualizar(id, new IncidenciaDTO.Gestion("EN_REVISION", null))).estado());
        incidencias.actualizar(id, new IncidenciaDTO.Gestion("RESUELTA", ""));
        assertNull(incidencias.listar(duenio).getFirst().respuestaAdmin());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(CatalogoException.class,
                () -> incidencias.actualizar(new ObjectId(), new IncidenciaDTO.Gestion("EN_REVISION", null))).estado());
    }

    @Test
    void editaClientesSinCambiarSuHashYGuardaElMensajeDeContacto() {
        mongo.indexOps(Usuario.class).createIndex(new Index().on("correoNormalizado", Sort.Direction.ASC).unique());
        String correo = "aislado-" + new ObjectId() + "@example.test";
        var datos = new ClienteAdminDTO.Datos("Ana", "Díaz", correo, "900000000", "Santiago", "Calle de prueba");
        var alta = clientes.crear(new ClienteAdminDTO.Alta(datos, "ClaveDePrueba123"));
        ObjectId id = new ObjectId(alta.id());
        String hash = usuarios.findById(id).orElseThrow().contrasenaHash();
        assertTrue(clientes.listar().stream().anyMatch(cliente -> cliente.id().equals(alta.id())));
        assertEquals(HttpStatus.CONFLICT, assertThrows(CatalogoException.class,
                () -> clientes.crear(new ClienteAdminDTO.Alta(datos, "OtraClaveDePrueba"))).estado());
        var edicion = new ClienteAdminDTO.Datos("Ana", "Díaz", correo, "", "Providencia", "Otra calle");
        assertEquals("Providencia", clientes.editar(id, edicion).comuna());
        assertEquals("Otra calle", clientes.perfil(id).direccion());
        assertEquals(hash, usuarios.findById(id).orElseThrow().contrasenaHash());

        contacto.enviar(new ContactoRequestDTO("Ana", "", "Mensaje aislado"), id);
        var mensaje = mongo.findOne(Query.query(Criteria.where("usuarioId").is(id)), MensajeContacto.class);
        assertEquals("Mensaje aislado", mensaje.comentario());
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
