package cl.duoc.autocare.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("mensajesContacto")
public record MensajeContacto(@Id ObjectId id, ObjectId usuarioId, String nombre, String correo,
                               String comentario, Instant fecha) {
}
