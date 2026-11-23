package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenRequestDTO.ProductoSolicitado;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.Producto;
import cl.duoc.autocare.repository.OrdenRepository;
import cl.duoc.autocare.repository.ProductoRepository;
import com.mongodb.MongoException;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdenServiceTests {

    private static final ObjectId ACEITE = new ObjectId("6600000000000000000000a1");
    private static final ObjectId FILTRO = new ObjectId("6600000000000000000000a2");

    @Mock private OrdenRepository ordenes;
    @Mock private ProductoRepository productos;
    @Mock private MongoTemplate mongo;
    @Mock private PlatformTransactionManager gestor;
    @Mock private TransactionStatus transaccion;

    @Test
    void calculaDescuentosYTotalYConfirmaOrdenEInventarioJuntos() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "35000", "20", 2);
        cuandoExiste(FILTRO, "FIL-TEST", "Filtro", "12000", "0", 4);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(ordenes.insert(any(Orden.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = servicio().crear(solicitud(linea(ACEITE, 2), linea(FILTRO, 1)));

        assertTrue(ObjectId.isValid(respuesta.id()));
        assertEquals(Orden.Estado.PROCESANDO, respuesta.estado());
        assertEquals(new BigDecimal("68000"), respuesta.total());
        assertEquals(new BigDecimal("35000"), respuesta.productos().getFirst().precioOriginal());
        assertEquals(new BigDecimal("20"), respuesta.productos().getFirst().descuento());
        assertEquals(new BigDecimal("28000"), respuesta.productos().getFirst().precioUnitario());
        assertEquals(new BigDecimal("56000"), respuesta.productos().getFirst().subtotal());
        assertEquals("ACE-TEST", respuesta.productos().getFirst().codigo());
        assertEquals("Aceite", respuesta.productos().getFirst().nombre());
        assertEquals(ACEITE.toHexString(), respuesta.productos().getFirst().productoId());

        ArgumentCaptor<Query> filtros = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> descuentos = ArgumentCaptor.forClass(UpdateDefinition.class);
        InOrder secuencia = inOrder(gestor, productos, mongo, ordenes);
        secuencia.verify(gestor).getTransaction(any());
        secuencia.verify(productos).findById(ACEITE);
        secuencia.verify(productos).findById(FILTRO);
        secuencia.verify(mongo, times(2))
                .updateFirst(filtros.capture(), descuentos.capture(), eq(Producto.class));
        secuencia.verify(ordenes).insert(any(Orden.class));
        secuencia.verify(gestor).commit(transaccion);
        assertEquals(new Document("_id", ACEITE).append("stock", new Document("$gte", 2)),
                filtros.getAllValues().getFirst().getQueryObject());
        assertEquals(new Document("$inc", new Document("stock", -2)),
                descuentos.getAllValues().getFirst().getUpdateObject());
    }

    @Test
    void aplicaElMismoRedondeoDeDescuentoQueElCatalogo() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "5", "50", 3);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(ordenes.insert(any(Orden.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = servicio().crear(solicitud(linea(ACEITE, 2)));

        assertEquals(new BigDecimal("3"), respuesta.productos().getFirst().precioUnitario());
        assertEquals(new BigDecimal("6"), respuesta.total());
    }

    @Test
    void devuelve404PorProductoInexistenteSinCambiarElInventario() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 2);
        when(productos.findById(FILTRO)).thenReturn(Optional.empty());

        verificarEstado(HttpStatus.NOT_FOUND, solicitud(linea(ACEITE, 1), linea(FILTRO, 1)));

        verifyNoInteractions(mongo, ordenes);
        verify(gestor).rollback(transaccion);
    }

    @Test
    void rechazaStockInsuficienteAntesDeDescontarCualquierProducto() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 0);

        verificarEstado(HttpStatus.CONFLICT, solicitud(linea(ACEITE, 1)));

        verifyNoInteractions(mongo, ordenes);
        verify(gestor).rollback(transaccion);
    }

    @Test
    void rechazaCantidadesNoPositivasSinAbrirTransaccion() {
        verificarEstado(HttpStatus.BAD_REQUEST, solicitud(linea(ACEITE, 0)));
        verificarEstado(HttpStatus.BAD_REQUEST, solicitud(linea(ACEITE, -1)));

        verifyNoInteractions(gestor, mongo, productos, ordenes);
    }

    @Test
    void rechazaElMismoProductoAunqueCambienLasMayusculasDelId() {
        OrdenRequestDTO solicitud = solicitud(linea(ACEITE, 1),
                new ProductoSolicitado("6600000000000000000000A1", 2));

        verificarEstado(HttpStatus.BAD_REQUEST, solicitud);

        verifyNoInteractions(gestor, mongo, productos, ordenes);
    }

    @Test
    void revierteElPrimerDescuentoSiElSegundoFiltroDeStockNoCoincide() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 3);
        cuandoExiste(FILTRO, "FIL-TEST", "Filtro", "200", "0", 1);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(0, 0L, null));

        verificarEstado(HttpStatus.CONFLICT, solicitud(linea(ACEITE, 1), linea(FILTRO, 1)));

        verifyNoInteractions(ordenes);
        verify(gestor).rollback(transaccion);
        verify(gestor, never()).commit(any());
    }

    @Test
    void revierteElStockSiFallaLaPersistenciaDeLaOrden() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 1);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(ordenes.insert(any(Orden.class))).thenThrow(new DataAccessResourceFailureException("Fallo de prueba."));

        verificarEstado(HttpStatus.SERVICE_UNAVAILABLE, solicitud(linea(ACEITE, 1)));

        InOrder secuencia = inOrder(mongo, ordenes, gestor);
        secuencia.verify(mongo).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class));
        secuencia.verify(ordenes).insert(any(Orden.class));
        secuencia.verify(gestor).rollback(transaccion);
        verify(gestor, never()).commit(any());
    }

    @Test
    void traduceUnConflictoDeEscrituraConcurrenteYRevierteLaTransaccion() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 1);
        MongoException conflicto = new MongoException(112, "WriteConflict");
        conflicto.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenThrow(new DataAccessResourceFailureException("Fallo de prueba.", conflicto));

        verificarEstado(HttpStatus.CONFLICT, solicitud(linea(ACEITE, 1)));

        verifyNoInteractions(ordenes);
        verify(gestor).rollback(transaccion);
    }

    @Test
    void unaFallaDeConexionTransitoriaDevuelve503EnVezDeUnConflictoDeInventario() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 1);
        MongoException conexion = new MongoException("Conexión interrumpida.");
        conexion.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenThrow(new DataAccessResourceFailureException("Fallo de prueba.", conexion));

        verificarEstado(HttpStatus.SERVICE_UNAVAILABLE, solicitud(linea(ACEITE, 1)));

        verifyNoInteractions(ordenes);
        verify(gestor).rollback(transaccion);
        verify(gestor, never()).commit(any());
    }

    @Test
    void informaElIdParaConsultarSiMongoNoConfirmaElCommit() {
        iniciarTransaccion();
        cuandoExiste(ACEITE, "ACE-TEST", "Aceite", "100", "0", 1);
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Producto.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(ordenes.insert(any(Orden.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        MongoException resultadoDesconocido = new MongoException(112, "Confirmación desconocida.");
        resultadoDesconocido.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(new TransactionSystemException("No se confirmó.", resultadoDesconocido))
                .when(gestor).commit(transaccion);

        CatalogoException error = verificarEstado(HttpStatus.SERVICE_UNAVAILABLE, solicitud(linea(ACEITE, 1)));

        ArgumentCaptor<Orden> ordenGuardada = ArgumentCaptor.forClass(Orden.class);
        verify(ordenes).insert(ordenGuardada.capture());
        assertTrue(error.getMessage().contains(ordenGuardada.getValue().id().toHexString()));
    }

    @Test
    void devuelve404AlBuscarUnaOrdenInexistente() {
        when(ordenes.findById(ACEITE)).thenReturn(Optional.empty());
        CatalogoException error = assertThrows(CatalogoException.class, () -> servicio().buscar(ACEITE));
        assertEquals(HttpStatus.NOT_FOUND, error.estado());
    }

    private OrdenService servicio() {
        return new OrdenService(ordenes, productos, mongo, gestor);
    }

    private void iniciarTransaccion() {
        when(gestor.getTransaction(any())).thenReturn(transaccion);
    }

    private void cuandoExiste(ObjectId id, String codigo, String nombre, String precio, String descuento, int stock) {
        when(productos.findById(id)).thenReturn(Optional.of(new Producto(id, codigo, codigo, nombre,
                "", new BigDecimal(precio), new BigDecimal(descuento), stock, 0, "", new ObjectId())));
    }

    private CatalogoException verificarEstado(HttpStatus estado, OrdenRequestDTO datos) {
        CatalogoException excepcion = assertThrows(CatalogoException.class, () -> servicio().crear(datos));
        assertEquals(estado, excepcion.estado());
        return excepcion;
    }

    private static ProductoSolicitado linea(ObjectId id, int cantidad) {
        return new ProductoSolicitado(id.toHexString(), cantidad);
    }

    private static OrdenRequestDTO solicitud(ProductoSolicitado... lineas) {
        return new OrdenRequestDTO(List.of(lineas));
    }
}
