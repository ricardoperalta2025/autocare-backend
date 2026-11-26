package cl.duoc.autocare.dto;

import java.time.Instant;

import cl.duoc.autocare.model.Incidencia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class IncidenciaDTO {
    private IncidenciaDTO() {
    }

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

    public record Respuesta(String id, String ordenId, String asunto, String descripcion,
                            Instant fecha, Incidencia.Estado estado) {
        public static Respuesta desde(Incidencia incidencia) {
            return new Respuesta(incidencia.id().toHexString(), incidencia.ordenId().toHexString(),
                    incidencia.asunto(), incidencia.descripcion(), incidencia.fecha(), incidencia.estado());
        }
    }
}
