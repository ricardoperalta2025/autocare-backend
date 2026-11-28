package cl.duoc.autocare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("catalogo-local")
public class OpenApiConfig {

    @Bean
    OpenAPI autoCareProOpenApi() {
        Components componentes = new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT"))
                .addParameters("IdCatalogo", new Parameter()
                        .name("id").in("path").required(true)
                        .description("Identificador MongoDB de 24 caracteres hexadecimales.")
                        .schema(new StringSchema().pattern("^[0-9a-fA-F]{24}$"))
                        .example("660000000000000000000001"))
                .addSchemas("ErrorCatalogo", new ObjectSchema()
                        .addProperty("type", new StringSchema().format("uri-reference"))
                        .addProperty("title", new StringSchema())
                        .addProperty("status", new IntegerSchema())
                        .addProperty("detail", new StringSchema())
                        .addProperty("instance", new StringSchema().format("uri-reference"))
                        .addProperty("campos", new MapSchema().additionalProperties(new StringSchema())
                                .description("Mensajes por campo cuando falla la validación.")))
                .addResponses("SesionRequerida", new ApiResponse().description("Falta un JWT válido de una cuenta activa."))
                .addResponses("PermisoInsuficiente", new ApiResponse().description("El rol no tiene permiso o la escritura está deshabilitada."))
                .addResponses("CorreoDuplicado", respuestaError("Ya existe una cuenta con ese correo."))
                .addResponses("IndiceCorreoNoDisponible", respuestaError("El índice único de correo no está disponible."))
                .addResponses("EstadoIncompatible", respuestaError("El estado actual no permite la transición solicitada."))
                .addResponses("DatosInvalidos", respuestaError("La solicitud contiene datos o identificadores inválidos."))
                .addResponses("RecursoNoEncontrado", respuestaError("El recurso solicitado no existe."))
                .addResponses("ConflictoCatalogo", respuestaError("La operación entra en conflicto con los datos del catálogo."))
                .addResponses("ConflictoOrden", respuestaError("Stock insuficiente, conflicto de escritura o clave de intento usada con otros productos o cantidades."))
                .addResponses("CompraSinConfirmacion", respuestaError("No se pudo confirmar la compra. Consulta el ID de la orden o reintenta con la misma clave y solicitud."));

        return new OpenAPI()
                .info(new Info().title("AutoCarePro API").version("1.0")
                        .description("""
                                API REST v1 de AutoCarePro. En catalogo-local la documentación incluye catálogo,
                                autenticación, clientes, órdenes, incidencias y contacto. Usa Authorize con el JWT
                                obtenido en /api/v1/auth/login. Un Bearer inválido puede provocar HTTP 401 incluso
                                en una operación pública.

                                Railway añade GET /api/v1/configuracion y GET /api/v1/salud/mongodb.
                                El perfil verificar-mongodb expone solo la comprobación de conexión.
                                Swagger se habilita únicamente en catalogo-local. Los HEAD permitidos
                                de catálogo y órdenes derivan de sus GET.
                                """))
                .components(componentes);
    }

    private ApiResponse respuestaError(String descripcion) {
        return new ApiResponse().description(descripcion)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorCatalogo"))));
    }
}
