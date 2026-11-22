package cl.duoc.autocare.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("categorias")
public record Categoria(
        @Id ObjectId id,
        String nombre,
        @Indexed(unique = true) String nombreNormalizado
) {
}
