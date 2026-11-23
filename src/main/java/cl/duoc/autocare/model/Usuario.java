package cl.duoc.autocare.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("usuarios")
public record Usuario(
        @Id ObjectId id,
        String nombre,
        @Indexed(name = "correo_normalizado_unico", unique = true) String correoNormalizado,
        String contrasenaHash,
        RolUsuario rol,
        boolean activo
) {
    @Override
    public String toString() {
        return "Usuario[id=" + id + ", rol=" + rol + ", activo=" + activo + "]";
    }
}
