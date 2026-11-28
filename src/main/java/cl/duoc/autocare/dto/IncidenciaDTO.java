package cl.duoc.autocare.dto;

import java.time.Instant;

import cl.duoc.autocare.model.Incidencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class IncidenciaDTO {
    private IncidenciaDTO() {
    }

    @Schema(name = "SolicitudIncidencia")
    public record Solicitud(
            @NotBlank @Pattern(regexp = "[0-9a-fA-F]{24}") String ordenId,
            @NotBlank @Size(max = 150) String asunto,
            @NotBlank @Size(max = 2000) String descripcion
    ) {
        public Solicitud {
            asunto = asunto == null ? null : asunto.strip();
            descripcion = descripcion == null ? null : descripcion.strip();
        }
    }

    @Schema(name = "GestionIncidencia")
    public record Gestion(
            @Schema(allowableValues = {"PENDIENTE", "EN_REVISION", "RESUELTA"})
            @NotBlank @Pattern(regexp = "PENDIENTE|EN_REVISION|RESUELTA") String estado,
            @Schema(description = "Omitir o enviar null conserva la respuesta. Una cadena vacía la elimina.",
                    types = {"string", "null"})
            @Size(max = 2000) String respuestaAdmin) {
        public Gestion {
            respuestaAdmin = respuestaAdmin == null ? null : respuestaAdmin.strip();
        }
    }

    @Schema(name = "RespuestaIncidencia")
    public record Respuesta(String id, String ordenId, String asunto, String descripcion,
                            Instant fecha, Incidencia.Estado estado,
                            @Schema(types = {"string", "null"}) String respuestaAdmin,
                            @Schema(types = {"string", "null"}, format = "date-time") Instant fechaActualizacion) {
        public static Respuesta desde(Incidencia incidencia) {
            return new Respuesta(incidencia.id().toHexString(), incidencia.ordenId().toHexString(),
                    incidencia.asunto(), incidencia.descripcion(), incidencia.fecha(), incidencia.estado(),
                    incidencia.respuestaAdmin(), incidencia.fechaActualizacion());
        }
    }
}
