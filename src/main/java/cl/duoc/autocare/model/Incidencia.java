package cl.duoc.autocare.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("incidencias")
public record Incidencia(@Id ObjectId id, ObjectId compradorId, ObjectId ordenId, String asunto,
                         String descripcion, Instant fecha, Estado estado,
                         String respuestaAdmin, Instant fechaActualizacion) {
    public enum Estado {
        PENDIENTE, EN_REVISION, RESUELTA
    }
}
