package cl.duoc.autocare.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document("ordenes")
public record Orden(
        @Id ObjectId id,
        Instant fechaCreacion,
        Estado estado,
        List<Detalle> productos,
        @Field(targetType = FieldType.STRING) BigDecimal total,
        ObjectId compradorId,
        Entrega entrega
) {

    public Orden(ObjectId id, Instant fechaCreacion, Estado estado, List<Detalle> productos,
                 BigDecimal total, ObjectId compradorId) {
        this(id, fechaCreacion, estado, productos, total, compradorId, null);
    }

    public enum Estado {
        PROCESANDO, ENVIADO
    }

    public record Entrega(String nombre, String correo, String telefono, String comuna, String direccion, String tipo) {
    }

    public record Detalle(
            ObjectId productoId,
            String codigo,
            String nombre,
            int cantidad,
            @Field(targetType = FieldType.DECIMAL128) BigDecimal precioOriginal,
            @Field(targetType = FieldType.DECIMAL128) BigDecimal descuento,
            @Field(targetType = FieldType.DECIMAL128) BigDecimal precioUnitario,
            @Field(targetType = FieldType.STRING) BigDecimal subtotal
    ) {
    }
}
