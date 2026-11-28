package cl.duoc.autocare.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cl.duoc.autocare.JwtDePrueba;
import cl.duoc.autocare.config.ConfiguracionDemostracion;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import cl.duoc.autocare.service.AutenticacionService;
import cl.duoc.autocare.service.JwtService;
import cl.duoc.autocare.service.OrdenService;
import cl.duoc.autocare.service.ProductoService;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static java.math.BigDecimal.ZERO;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/autocarepro_demo",
        "logging.level.org.mongodb.driver=OFF"
})
@AutoConfigureMockMvc
@ActiveProfiles("railway")
class RailwayHttpTests {

    private static final String ID = "660000000000000000000001";
    private static final String ORDEN = "670000000000000000000001";
    private static final String CUENTA = """
            {"nombre":"Cliente de prueba","correo":"cliente@example.test","contrasena":"ClaveSimulada1"}
            """;
    private static final String COMPRA = """
            {"productos":[{"productoId":"660000000000000000000001","cantidad":1}]}
            """;

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry propiedades) {
        JwtDePrueba.configurar(propiedades);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private UsuarioRepository usuarios;
    @MockitoBean private AutenticacionService autenticacion;
    @MockitoBean private ProductoService productos;
    @MockitoBean private OrdenService ordenes;
    @MockitoSpyBean private MongoTemplate mongo;

    private String autorizar(RolUsuario rol) {
        Usuario cuenta = new Usuario(new ObjectId(), rol.name(), "cuenta@example.test", "", rol, true);
        when(usuarios.findById(cuenta.id())).thenReturn(Optional.of(cuenta));
        return "Bearer " + jwt.emitir(cuenta).token();
    }

    @Nested
    @TestPropertySource(properties = {"AUTOCARE_DEMO_REGISTRO=false", "AUTOCARE_DEMO_ESCRITURAS=false"})
    class Cerrado {
        @Test
        void informaLaConfiguracionYPermiteCatalogoLoginYSalud() throws Exception {
            when(productos.listar()).thenReturn(List.of());
            doReturn(new Document("ok", 1)).when(mongo).executeCommand(new Document("ping", 1));
            Usuario cuenta = new Usuario(new ObjectId(), "Cliente", "cliente@example.test", "", RolUsuario.CLIENTE, true);
            when(autenticacion.iniciarSesion(any())).thenReturn(jwt.emitir(cuenta));

            mvc.perform(get("/api/v1/configuracion")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.registro").value(false))
                    .andExpect(jsonPath("$.escrituras").value(false));
            mvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
            mvc.perform(get("/api/v1/salud/mongodb")).andExpect(status().isOk());
            mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                    .content("{\"correo\":\"cliente@example.test\",\"contrasena\":\"ClaveSimulada1\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void bloqueaRegistroComprasYAdministracionAunConTokenValido() throws Exception {
            String admin = autorizar(RolUsuario.ADMIN);
            mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(CUENTA))
                    .andExpect(status().is4xxClientError());
            mvc.perform(post("/api/v1/productos").header("Authorization", admin))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/productos/" + ID).header("Authorization", admin))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/categorias/" + ID).header("Authorization", admin))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/ordenes").header("Authorization", admin)
                    .header("Idempotency-Key", ORDEN).contentType("application/json").content(COMPRA))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/ordenes/" + ORDEN + "/envio").header("Authorization", admin))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/usuarios").header("Authorization", admin)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/usuarios/me/perfil").header("Authorization", admin)).andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/incidencias/" + ID + "/resolucion").header("Authorization", admin))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/incidencias/" + ID + "/gestion").header("Authorization", admin)
                    .contentType("application/json").content("{\"estado\":\"EN_REVISION\"}"))
                    .andExpect(status().isForbidden());
            String cliente = autorizar(RolUsuario.CLIENTE);
            mvc.perform(post("/api/v1/incidencias").header("Authorization", cliente)).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/contacto").header("Authorization", cliente)).andExpect(status().isForbidden());
            verifyNoInteractions(autenticacion, productos, ordenes);
        }
    }

    @Nested
    @TestPropertySource(properties = {"AUTOCARE_DEMO_REGISTRO=true", "AUTOCARE_DEMO_ESCRITURAS=true"})
    class Autorizado {
        @Test
        void habilitaRegistroYConservaValidacionDeCantidadesYRoles() throws Exception {
            when(autenticacion.registrar(any())).thenReturn(
                    new UsuarioResponseDTO(ID, "Cliente", "cliente@example.test", RolUsuario.CLIENTE));
            mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(CUENTA))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"));

            String cliente = autorizar(RolUsuario.CLIENTE);
            when(ordenes.crear(eq(new ObjectId(ORDEN)), any(), any())).thenReturn(
                    new OrdenResponseDTO(ORDEN, Instant.EPOCH, Orden.Estado.PROCESANDO, List.of(), ZERO));
            mvc.perform(post("/api/v1/ordenes").header("Authorization", cliente)
                    .header("Idempotency-Key", ORDEN).contentType("application/json").content(COMPRA))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/v1/ordenes").header("Authorization", cliente)
                    .header("Idempotency-Key", ORDEN).contentType("application/json")
                    .content(COMPRA.replace("\"cantidad\":1", "\"cantidad\":1.5")))
                    .andExpect(status().isBadRequest());
            mvc.perform(delete("/api/v1/productos/" + ID).header("Authorization", cliente))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/productos/" + ID).header("Authorization", autorizar(RolUsuario.ADMIN)))
                    .andExpect(status().isNoContent());
            mvc.perform(delete("/api/v1/productos/" + ID).header("Authorization", autorizar(RolUsuario.VENDEDOR)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rechazaLaBaseOriginal() {
        MongoDatabaseFactory conexion = mock(MongoDatabaseFactory.class);
        MongoDatabase base = mock(MongoDatabase.class);
        when(conexion.getMongoDatabase()).thenReturn(base);
        when(base.getName()).thenReturn("autocarepro");
        assertThrows(IllegalStateException.class,
                () -> new DemostracionController(new ConfiguracionDemostracion(false, false), conexion));
    }

    @Nested
    @TestPropertySource(properties = {"AUTOCARE_DEMO_REGISTRO=true", "AUTOCARE_DEMO_ESCRITURAS=false"})
    class SoloRegistro {
        @Test
        void elRegistroPublicoFuncionaSinAbrirLaAdministracion() throws Exception {
            when(autenticacion.registrar(any())).thenReturn(
                    new UsuarioResponseDTO(ID, "Cliente", "cliente@example.test", RolUsuario.CLIENTE));
            mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(CUENTA))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CLIENTE"));
            mvc.perform(delete("/api/v1/productos/" + ID).header("Authorization", autorizar(RolUsuario.ADMIN)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(productos);
        }
    }

    @Nested
    @TestPropertySource(properties = {"AUTOCARE_DEMO_REGISTRO=false", "AUTOCARE_DEMO_ESCRITURAS=true"})
    class SoloEscrituras {
        @Test
        void laAdministracionSigueRestringidaAlRolSinAbrirElRegistro() throws Exception {
            mvc.perform(post("/api/v1/auth/registro").contentType("application/json").content(CUENTA))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete("/api/v1/productos/" + ID).header("Authorization", autorizar(RolUsuario.ADMIN)))
                    .andExpect(status().isNoContent());
            mvc.perform(delete("/api/v1/productos/" + ID).header("Authorization", autorizar(RolUsuario.CLIENTE)))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(autenticacion);
        }
    }
}
