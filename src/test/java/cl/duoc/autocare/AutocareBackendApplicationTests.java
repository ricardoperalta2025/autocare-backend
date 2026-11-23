package cl.duoc.autocare;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.autocare.controller.CategoriaController;
import cl.duoc.autocare.controller.OrdenController;
import cl.duoc.autocare.controller.ProductoController;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.Producto;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/autocare_test",
        "spring.data.mongodb.auto-index-creation=false",
        "logging.level.org.mongodb.driver=OFF"
})
class AutocareBackendApplicationTests {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ApplicationContext contexto;

    @Test
    void contextLoads() {
    }

    @Test
    void mapeaIdentificadoresYPreciosABsonNativo() {
        ObjectId id = new ObjectId();
        ObjectId categoriaId = new ObjectId();
        Producto producto = new Producto(id, "LAB001", "LAB001", "Aceite", "",
                new BigDecimal("12.50"), new BigDecimal("10.25"), 4, 1, "/images/Tienda.png", categoriaId);
        Document bson = new Document();

        mongoTemplate.getConverter().write(producto, bson);

        assertEquals(id, bson.getObjectId("_id"));
        assertEquals(categoriaId, bson.getObjectId("categoriaId"));
        assertInstanceOf(Decimal128.class, bson.get("precio"));
        assertInstanceOf(Decimal128.class, bson.get("descuento"));
        assertEquals(producto, mongoTemplate.getConverter().read(Producto.class, bson));
    }

    @Test
    void conservaElDetalleDeOrdenYLosImportesMayoresALaPrecisionDecimal128() {
        ObjectId productoId = new ObjectId();
        BigDecimal precio = new BigDecimal("9007199254740990.1234567890123456");
        int cantidad = Integer.MAX_VALUE;
        BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(cantidad));
        var detalle = new Orden.Detalle(productoId, "LAB001", "Aceite", cantidad, precio,
                BigDecimal.ZERO, precio, subtotal);
        var orden = new Orden(new ObjectId(), Instant.parse("2026-01-01T00:00:00Z"),
                Orden.Estado.PROCESANDO, List.of(detalle), subtotal);
        Document bson = new Document();

        mongoTemplate.getConverter().write(orden, bson);

        Document primerDetalle = bson.getList("productos", Document.class).getFirst();
        assertEquals(productoId, primerDetalle.getObjectId("productoId"));
        assertInstanceOf(Decimal128.class, primerDetalle.get("precioUnitario"));
        assertEquals(subtotal.toString(), primerDetalle.getString("subtotal"));
        assertEquals(subtotal.toString(), bson.getString("total"));
        assertEquals(orden, mongoTemplate.getConverter().read(Orden.class, bson));
    }

    @Test
    void mantieneElCrudDeshabilitadoSinElPerfilLocal() {
        assertTrue(contexto.getBeansOfType(ProductoController.class).isEmpty());
        assertTrue(contexto.getBeansOfType(CategoriaController.class).isEmpty());
        assertTrue(contexto.getBeansOfType(OrdenController.class).isEmpty());
    }
}
