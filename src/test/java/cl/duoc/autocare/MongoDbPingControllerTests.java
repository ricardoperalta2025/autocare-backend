package cl.duoc.autocare;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MongoDbPingControllerTests {

    private MongoTemplate mongoTemplate;
    private MockMvc mvc;

    @BeforeEach
    void prepararControlador() {
        mongoTemplate = mock(MongoTemplate.class);
        mvc = MockMvcBuilders.standaloneSetup(new MongoDbPingController(mongoTemplate)).build();
    }

    @Test
    void confirmaDisponibilidadCuandoMongoDbRespondeAlPing() throws Exception {
        Document comando = new Document("ping", 1);
        when(mongoTemplate.executeCommand(comando)).thenReturn(new Document("ok", 1.0));

        mvc.perform(get("/api/v1/salud/mongodb"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"mongodb\":\"disponible\"}"));

        verify(mongoTemplate).executeCommand(comando);
    }

    @Test
    void indicaIndisponibilidadCuandoMongoDbNoConfirmaElPing() throws Exception {
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
                .thenReturn(new Document("ok", 0.0));

        mvc.perform(get("/api/v1/salud/mongodb"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("{\"mongodb\":\"no_disponible\"}"));
    }

    @Test
    void ocultaLosDetallesInternosCuandoFallaLaConexion() throws Exception {
        when(mongoTemplate.executeCommand(new Document("ping", 1)))
                .thenThrow(new DataAccessResourceFailureException("Detalle interno simulado"));

        mvc.perform(get("/api/v1/salud/mongodb"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("{\"mongodb\":\"no_disponible\"}"));
    }
}
