package cl.duoc.autocare;

import java.util.Map;

import com.mongodb.MongoException;
import org.bson.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("verificar-mongodb")
@RestController
class MongoDbPingController {

    private final MongoTemplate mongoTemplate;

    MongoDbPingController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping("/api/v1/salud/mongodb")
    ResponseEntity<Map<String, String>> comprobarConexion() {
        try {
            Document respuesta = mongoTemplate.executeCommand(new Document("ping", 1));
            if (respuesta.get("ok") instanceof Number resultado && resultado.doubleValue() == 1.0) {
                return ResponseEntity.ok(Map.of("mongodb", "disponible"));
            }
        } catch (DataAccessException | MongoException excepcion) {
            return mongoDbNoDisponible();
        }

        return mongoDbNoDisponible();
    }

    private ResponseEntity<Map<String, String>> mongoDbNoDisponible() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("mongodb", "no_disponible"));
    }
}
