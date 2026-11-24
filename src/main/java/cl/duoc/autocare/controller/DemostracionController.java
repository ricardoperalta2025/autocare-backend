package cl.duoc.autocare.controller;

import cl.duoc.autocare.config.ConfiguracionDemostracion;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("railway")
@RestController
public class DemostracionController {

    private final ConfiguracionDemostracion configuracion;

    DemostracionController(ConfiguracionDemostracion configuracion, MongoDatabaseFactory mongo) {
        if (!"autocarepro_demo".equals(mongo.getMongoDatabase().getName())) {
            throw new IllegalStateException("El perfil railway requiere la base aislada autocarepro_demo.");
        }
        this.configuracion = configuracion;
    }

    @GetMapping("/api/v1/configuracion")
    ConfiguracionDemostracion consultar() {
        return configuracion;
    }
}
