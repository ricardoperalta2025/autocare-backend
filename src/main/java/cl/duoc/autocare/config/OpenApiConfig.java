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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("catalogo-local")
public class OpenApiConfig {

    @Bean
    OpenAPI autoCareProOpenApi() {
        Components componentes = new Components()
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
                .addResponses("DatosInvalidos", respuestaError("La solicitud contiene datos o identificadores inválidos."))
                .addResponses("RecursoNoEncontrado", respuestaError("El recurso solicitado no existe."))
                .addResponses("ConflictoCatalogo", respuestaError("La operación entra en conflicto con los datos del catálogo."))
                .addResponses("ConflictoOrden", respuestaError("Stock insuficiente o conflicto con otra operación de inventario."))
                .addResponses("CompraSinConfirmacion", respuestaError("No se pudo confirmar la compra. Consulta el ID de la orden."));

        return new OpenAPI()
                .info(new Info().title("AutoCarePro API").version("1.0")
                        .description("API REST de productos, categorías y compras simuladas de AutoCarePro."))
                .components(componentes);
    }

    private ApiResponse respuestaError(String descripcion) {
        return new ApiResponse().description(descripcion)
                .content(new Content().addMediaType("application/problem+json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorCatalogo"))));
    }
}
