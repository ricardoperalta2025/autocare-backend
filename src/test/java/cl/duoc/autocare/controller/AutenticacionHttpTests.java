package cl.duoc.autocare.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import cl.duoc.autocare.JwtDePrueba;
import cl.duoc.autocare.config.SeguridadConfig;
import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import cl.duoc.autocare.service.AutenticacionService;
import cl.duoc.autocare.service.CategoriaService;
import cl.duoc.autocare.service.JwtService;
import cl.duoc.autocare.service.OrdenService;
import cl.duoc.autocare.service.ProductoService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, ProductoController.class, CategoriaController.class, OrdenController.class})
@Import({SeguridadConfig.class, AutenticacionService.class, JwtService.class})
@ActiveProfiles("catalogo-local")
class AutenticacionHttpTests {

    private static final ObjectId CUENTA = new ObjectId();
    private static final ObjectId PRODUCTO = new ObjectId();
    private static final ObjectId ORDEN = new ObjectId();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String REGISTRO = "/api/v1/auth/registro";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";
    private static final String ORDENES = "/api/v1/ordenes";

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry propiedades) {
        JwtDePrueba.configurar(propiedades);
    }

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder encoder;
    @MockitoBean private UsuarioRepository usuarios;
    @MockitoBean private MongoTemplate mongo;
    @MockitoBean private OrdenService ordenes;
    @MockitoBean private ProductoService productos;
    @MockitoBean private CategoriaService categorias;

    @Test
    void registroNormalizaCorreoIgnoraRolExternoYGuardaBCrypt() throws Exception {
        establecerIndiceUnico();
        when(usuarios.insert(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario datos = invocacion.getArgument(0);
            return new Usuario(CUENTA, datos.nombre(), datos.correoNormalizado(), datos.contrasenaHash(),
                    datos.rol(), datos.activo());
        });
        String contrasena = UUID.randomUUID().toString();

        mvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(registro(contrasena)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CUENTA.toHexString()))
                .andExpect(jsonPath("$.nombre").value("Persona de prueba"))
                .andExpect(jsonPath("$.correo").value("persona@example.test"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).insert(guardado.capture());
        Usuario cliente = guardado.getValue();
        assertEquals(RolUsuario.CLIENTE, cliente.rol());
        assertTrue(cliente.activo());
        assertTrue(cliente.contrasenaHash().startsWith("$2"));
        assertNotEquals(contrasena, cliente.contrasenaHash());
        assertTrue(encoder.matches(contrasena, cliente.contrasenaHash()));
    }

    @Test
    void registroRechazaCorreoDuplicadoInclusoSiFallaElInsertPorCarrera() throws Exception {
        establecerIndiceUnico();
        when(usuarios.insert(any(Usuario.class))).thenThrow(new DuplicateKeyException("Colisión en la prueba."));

        mvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(registro(UUID.randomUUID().toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una cuenta con ese correo."));
    }

    @Test
    void registroNoEscribeCuandoFaltaElIndiceUnico() throws Exception {
        when(mongo.indexOps(Usuario.class)).thenReturn(mock(IndexOperations.class));

        mvc.perform(post(REGISTRO).contentType(MediaType.APPLICATION_JSON).content(registro(UUID.randomUUID().toString())))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(usuarios);
    }

    @Test
    void loginValidoEmiteJwtYMeDevuelveLaCuenta() throws Exception {
        String contrasena = UUID.randomUUID().toString();
        var cliente = cuenta(RolUsuario.CLIENTE, true, encoder.encode(contrasena));
        when(usuarios.findByCorreoNormalizado("persona@example.test")).thenReturn(Optional.of(cliente));
        when(usuarios.findById(CUENTA)).thenReturn(Optional.of(cliente));
        String respuesta = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(login(contrasena)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(1800))
                .andExpect(jsonPath("$.usuario.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.usuario.contrasenaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = JSON.readTree(respuesta).path("token").asString();

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CUENTA.toHexString()))
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void loginDevuelveElMismo401ParaCorreoDesconocidoYClaveIncorrecta() throws Exception {
        String incorrecta = UUID.randomUUID().toString();
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(login(incorrecta)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas."));
        when(usuarios.findByCorreoNormalizado("persona@example.test"))
                .thenReturn(Optional.of(cuenta(RolUsuario.CLIENTE, true, encoder.encode(UUID.randomUUID().toString()))));

        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(login(incorrecta)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas."));
    }

    @Test
    void jwtInvalidoOExpiradoDevuelve401SinConsultarCuentas() throws Exception {
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer formato-invalido"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + JwtDePrueba.firmar(CUENTA, Instant.now().minusSeconds(120))))
                .andExpect(status().isUnauthorized());
        String[] partes = JwtDePrueba.firmar(CUENTA, Instant.now().plusSeconds(60)).split("\\.");
        String firmaAlterada = (partes[2].startsWith("A") ? "B" : "A") + partes[2].substring(1);
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + partes[0] + "." + partes[1] + "." + firmaAlterada))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios);
    }

    @Test
    void desactivarLaCuentaImpideLoginYRevocaElAccesoAunqueElJwtSigaVigente() throws Exception {
        String contrasena = UUID.randomUUID().toString();
        Usuario inactiva = cuenta(RolUsuario.ADMIN, false, encoder.encode(contrasena));
        when(usuarios.findByCorreoNormalizado(inactiva.correoNormalizado())).thenReturn(Optional.of(inactiva));
        when(usuarios.findById(CUENTA)).thenReturn(Optional.of(inactiva));
        mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(login(contrasena)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void laLecturaDelCatalogoEsPublicaYUnaOrdenRequiereSesion() throws Exception {
        mvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/productos/" + PRODUCTO)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk());
        mvc.perform(get(ME)).andExpect(status().isUnauthorized());
        mvc.perform(get(ORDENES + "/" + ORDEN)).andExpect(status().isUnauthorized());
        mvc.perform(post(ORDENES)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/productos/" + PRODUCTO)).andExpect(status().isUnauthorized());
        verifyNoInteractions(ordenes);
    }

    @Test
    void administradorPuedeModificarCatalogoYConsultarTodasLasOrdenes() throws Exception {
        autorizar(RolUsuario.ADMIN);
        mvc.perform(delete("/api/v1/productos/" + PRODUCTO).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/categorias/" + PRODUCTO).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isNoContent());
        mvc.perform(get(ORDENES).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isOk());
        verify(productos).eliminar(PRODUCTO);
        verify(categorias).eliminar(PRODUCTO);
        verify(ordenes).listar(null);
    }

    @Test
    void vendedorLeeOrdenesYElRolVigenteDeMongoPrevaleceSobreElClaim() throws Exception {
        autorizar(RolUsuario.VENDEDOR);
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("VENDEDOR"));
        mvc.perform(get("/api/v1/productos/" + PRODUCTO).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isOk());
        mvc.perform(get(ORDENES).header(HttpHeaders.AUTHORIZATION, tokenVigente())).andExpect(status().isOk());
        mvc.perform(get(ORDENES + "/" + ORDEN).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isOk());
        verify(ordenes).listar(null);
        verify(ordenes).buscar(ORDEN, null);
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/v1/productos", "PUT,/api/v1/productos/{id}", "DELETE,/api/v1/productos/{id}",
            "POST,/api/v1/categorias", "PUT,/api/v1/categorias/{id}", "DELETE,/api/v1/categorias/{id}",
            "POST,/api/v1/ordenes", "PUT,/api/v1/ordenes/{id}", "DELETE,/api/v1/usuarios/{id}"})
    void bloqueaLasEscriturasParaVendedor(String metodo, String ruta) throws Exception {
        autorizar(RolUsuario.VENDEDOR);
        mvc.perform(request(HttpMethod.valueOf(metodo), ruta.replace("{id}", PRODUCTO.toHexString()))
                        .header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ordenes, productos, categorias);
    }

    @Test
    void clienteCompraConSuIdentidadYNoPuedeModificarCatalogo() throws Exception {
        autorizar(RolUsuario.CLIENTE);
        String datos = """
                {"compradorId":"%s","productos":[{"productoId":"%s","cantidad":1}]}
                """.formatted(new ObjectId().toHexString(), PRODUCTO.toHexString());
        when(ordenes.crear(eq(ORDEN), any(OrdenRequestDTO.class), eq(CUENTA)))
                .thenReturn(new OrdenResponseDTO(ORDEN.toHexString(), Instant.now(),
                        Orden.Estado.PROCESANDO, List.of(), java.math.BigDecimal.ZERO));
        mvc.perform(post(ORDENES).header("Idempotency-Key", ORDEN.toHexString())
                        .header(HttpHeaders.AUTHORIZATION, tokenVigente()).contentType(MediaType.APPLICATION_JSON).content(datos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ORDEN.toHexString()));
        mvc.perform(get(ORDENES).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isOk());
        mvc.perform(get(ORDENES + "/" + ORDEN).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/categorias/" + PRODUCTO).header(HttpHeaders.AUTHORIZATION, tokenVigente()))
                .andExpect(status().isForbidden());
        verify(ordenes).crear(eq(ORDEN), any(OrdenRequestDTO.class), eq(CUENTA));
        verify(ordenes).listar(CUENTA);
        verify(ordenes).buscar(ORDEN, CUENTA);
    }

    private void establecerIndiceUnico() {
        IndexOperations indices = mock(IndexOperations.class);
        IndexInfo indice = mock(IndexInfo.class);
        IndexField campo = mock(IndexField.class);
        when(mongo.indexOps(Usuario.class)).thenReturn(indices);
        when(indices.getIndexInfo()).thenReturn(List.of(indice));
        when(indice.isUnique()).thenReturn(true);
        when(indice.getIndexFields()).thenReturn(List.of(campo));
        when(campo.getKey()).thenReturn("correoNormalizado");
    }

    private void autorizar(RolUsuario rol) {
        when(usuarios.findById(CUENTA)).thenReturn(Optional.of(cuenta(rol, true, null)));
    }

    private static Usuario cuenta(RolUsuario rol, boolean activa, String hash) {
        return new Usuario(CUENTA, "Persona de prueba", "persona@example.test", hash, rol, activa);
    }

    private static String tokenVigente() {
        return "Bearer " + JwtDePrueba.firmar(CUENTA, Instant.now().plusSeconds(60));
    }

    private static String registro(String contrasena) {
        return """
                {"nombre":" Persona de prueba ","correo":" PERSONA@EXAMPLE.TEST ","contrasena":"%s","rol":"ADMIN"}
                """.formatted(contrasena);
    }

    private static String login(String contrasena) {
        return """
                {"correo":"PERSONA@EXAMPLE.TEST","contrasena":"%s"}
                """.formatted(contrasena);
    }
}
