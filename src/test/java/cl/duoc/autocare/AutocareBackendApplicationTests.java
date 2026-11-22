package cl.duoc.autocare;

import java.math.BigDecimal;

import cl.duoc.autocare.controller.CategoriaController;
import cl.duoc.autocare.controller.ProductoController;
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
    void mantieneElCrudDeshabilitadoSinElPerfilLocal() {
        assertTrue(contexto.getBeansOfType(ProductoController.class).isEmpty());
        assertTrue(contexto.getBeansOfType(CategoriaController.class).isEmpty());
    }
}
