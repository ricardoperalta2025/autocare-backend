package cl.duoc.autocare.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

import cl.duoc.autocare.JwtDePrueba;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/autocare_test",
        "spring.data.mongodb.auto-index-creation=false",
        "logging.level.org.mongodb.driver=OFF"
})
class OpenApiCatalogoTests {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry propiedades) {
        JwtDePrueba.configurar(propiedades);
    }

    @Nested
    @ActiveProfiles("catalogo-local")
    class PerfilCatalogoLocal {

        @LocalServerPort
        private int puerto;

        @Test
        void publicaElContratoDelCatalogoConSusDtOsYRespuestas() throws Exception {
            HttpResponse<String> respuesta = consultar(puerto, "/v3/api-docs");

            assertEquals(200, respuesta.statusCode());
            JsonNode documento = JSON.readTree(respuesta.body());
            assertEquals("AutoCarePro API", documento.at("/info/title").asString());
            assertEquals("bearer", documento.at("/components/securitySchemes/bearerAuth/scheme").asString());
            assertTrue(documento.path("paths").path("/api/v1/auth/me").at("/get/security/0").has("bearerAuth"));
            assertEquals("1.0", documento.at("/info/version").asString());
            assertEquals(Set.of("/api/v1/productos", "/api/v1/productos/{id}",
                    "/api/v1/categorias", "/api/v1/categorias/{id}",
                    "/api/v1/ordenes", "/api/v1/ordenes/{id}", "/api/v1/auth/registro",
                    "/api/v1/auth/login", "/api/v1/auth/me", "/api/v1/ordenes/{id}/envio",
                    "/api/v1/usuarios", "/api/v1/usuarios/{id}", "/api/v1/usuarios/{id}/ordenes",
                    "/api/v1/usuarios/me/perfil", "/api/v1/incidencias", "/api/v1/incidencias/{id}/resolucion",
                    "/api/v1/contacto"),
                    Set.copyOf(documento.path("paths").propertyNames()));

            verificarRecurso(documento, "productos", "Producto", "Productos", Set.of("204", "400", "404"));
            verificarRecurso(documento, "categorias", "Categoria", "Categorías", Set.of("204", "400", "404", "409"));
            assertEquals("ACE-PRUEBA", documento.path("paths").path("/api/v1/productos")
                    .at("/post/requestBody/content/application~1json/examples/producto/value/codigo").asString());
            assertEquals("Aceites de motor", documento.path("paths").path("/api/v1/categorias")
                    .at("/post/requestBody/content/application~1json/examples/categoria/value/nombre").asString());

            JsonNode producto = documento.at("/components/schemas/ProductoResponseDTO/properties");
            assertEquals("number", producto.at("/precio/type").asString());
            assertEquals("number", producto.at("/descuento/type").asString());
            assertEquals("number", producto.at("/precioFinal/type").asString());
            assertEquals("string", producto.at("/id/type").asString());
            assertEquals("string", producto.at("/categoriaId/type").asString());
            assertEquals("string", documento.at("/components/schemas/CategoriaResponseDTO/properties/id/type").asString());
            assertEquals("^[0-9a-fA-F]{24}$", documento.at("/components/parameters/IdCatalogo/schema/pattern").asString());
            assertEquals("#/components/schemas/ErrorCatalogo", documento.at(
                    "/components/responses/ConflictoCatalogo/content/application~1problem+json/schema/$ref").asString());
        }

        @Test
        void documentaOperacionesYDtoDeOrdenes() throws Exception {
            JsonNode documento = JSON.readTree(consultar(puerto, "/v3/api-docs").body());
            JsonNode coleccion = documento.path("paths").path("/api/v1/ordenes");
            JsonNode detalle = documento.path("paths").path("/api/v1/ordenes/{id}");
            assertEquals(Set.of("get", "post"), Set.copyOf(coleccion.propertyNames()));
            assertEquals(Set.of("get"), Set.copyOf(detalle.propertyNames()));
            assertEquals("Órdenes", coleccion.at("/post/tags/0").asString());
            JsonNode clave = coleccion.at("/post/parameters/0");
            assertEquals("Idempotency-Key", clave.path("name").asString());
            assertEquals("header", clave.path("in").asString());
            assertTrue(clave.path("required").asBoolean());
            assertEquals("^[0-9a-fA-F]{24}$", clave.at("/schema/pattern").asString());
            assertEquals(Set.of("201", "400", "401", "403", "404", "409", "503"),
                    Set.copyOf(coleccion.at("/post/responses").propertyNames()));
            assertEquals("#/components/schemas/OrdenRequestDTO",
                    coleccion.at("/post/requestBody/content/application~1json/schema/$ref").asString());
            assertEquals("#/components/schemas/OrdenResponseDTO",
                    coleccion.at("/post/responses/201/content/application~1json/schema/$ref").asString());
            assertEquals("number", documento.at("/components/schemas/OrdenResponseDTO/properties/total/type").asString());
            assertEquals(2, coleccion.at("/post/requestBody/content/application~1json/examples/orden/value/productos/0/cantidad").asInt());
        }

        @Test
        void abreSwaggerUiYCargaLaConfiguracionLocal() throws Exception {
            HttpResponse<String> entrada = consultar(puerto, "/swagger-ui.html");

            assertEquals(302, entrada.statusCode());
            String destino = entrada.headers().firstValue("Location").orElseThrow();
            assertEquals("/swagger-ui/index.html", URI.create(destino).getPath());
            HttpResponse<String> pagina = consultar(puerto, "/swagger-ui/index.html");
            assertEquals(200, pagina.statusCode());
            assertTrue(pagina.body().contains("Swagger UI"));
            assertEquals(200, consultar(puerto, "/swagger-ui/swagger-initializer.js").statusCode());

            HttpResponse<String> configuracion = consultar(puerto, "/v3/api-docs/swagger-config");
            assertEquals(200, configuracion.statusCode());
            assertEquals("/v3/api-docs", JSON.readTree(configuracion.body()).path("url").asString());
        }
    }

    @Nested
    class PerfilNormal {

        @LocalServerPort
        private int puerto;

        @Test
        void mantieneSwaggerDeshabilitado() throws Exception {
            verificarDocumentacionDeshabilitada(puerto);
        }
    }

    @Nested
    @ActiveProfiles("verificar-mongodb")
    class PerfilVerificarMongoDb {

        @LocalServerPort
        private int puerto;

        @Test
        void mantieneSwaggerDeshabilitado() throws Exception {
            verificarDocumentacionDeshabilitada(puerto);
        }
    }

    private static void verificarRecurso(JsonNode documento, String recurso, String dto, String etiqueta,
                                         Set<String> codigosEliminar) {
        JsonNode coleccion = documento.path("paths").path("/api/v1/" + recurso);
        JsonNode detalle = documento.path("paths").path("/api/v1/" + recurso + "/{id}");
        assertEquals(Set.of("get", "post"), Set.copyOf(coleccion.propertyNames()));
        assertEquals(Set.of("get", "put", "delete"), Set.copyOf(detalle.propertyNames()));
        assertEquals(etiqueta, coleccion.at("/get/tags/0").asString());

        assertEquals(Set.of("200"), Set.copyOf(coleccion.at("/get/responses").propertyNames()));
        assertEquals(Set.of("201", "400", "409"), Set.copyOf(coleccion.at("/post/responses").propertyNames()));
        assertEquals(Set.of("200", "400", "404"), Set.copyOf(detalle.at("/get/responses").propertyNames()));
        assertEquals(Set.of("200", "400", "404", "409"), Set.copyOf(detalle.at("/put/responses").propertyNames()));
        assertEquals(codigosEliminar, Set.copyOf(detalle.at("/delete/responses").propertyNames()));
        assertFalse(detalle.at("/delete/responses/204").has("content"));
        assertEquals("#/components/parameters/IdCatalogo", detalle.at("/get/parameters/0/$ref").asString());

        String esquemaRespuesta = "#/components/schemas/" + dto + "ResponseDTO";
        assertEquals(esquemaRespuesta, coleccion.at("/get/responses/200/content/application~1json/schema/items/$ref").asString());
        assertEquals(esquemaRespuesta, coleccion.at("/post/responses/201/content/application~1json/schema/$ref").asString());

        String esquemaEntrada = "#/components/schemas/" + dto + "RequestDTO";
        assertEquals(esquemaEntrada, coleccion.at("/post/requestBody/content/application~1json/schema/$ref").asString());
        assertEquals(esquemaEntrada, detalle.at("/put/requestBody/content/application~1json/schema/$ref").asString());
    }

    private static void verificarDocumentacionDeshabilitada(int puerto) throws Exception {
        for (String ruta : Set.of("/v3/api-docs", "/v3/api-docs/swagger-config",
                "/swagger-ui.html", "/swagger-ui/index.html", "/webjars/swagger-ui/index.html",
                "/api/v1/ordenes", "/api/v1/ordenes/660000000000000000000001")) {
            assertEquals(404, consultar(puerto, ruta).statusCode(), ruta);
        }
    }

    private static HttpResponse<String> consultar(int puerto, String ruta) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return HTTP.send(peticion, HttpResponse.BodyHandlers.ofString());
    }
}
