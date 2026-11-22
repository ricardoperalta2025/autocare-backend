package cl.duoc.autocare.model;

import java.math.BigDecimal;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document("productos")
public record Producto(
        @Id ObjectId id,
        String codigo,
        @Indexed(unique = true) String codigoNormalizado,
        String nombre,
        String descripcion,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal precio,
        @Field(targetType = FieldType.DECIMAL128) BigDecimal descuento,
        int stock,
        int stockCritico,
        String imagen,
        @Indexed ObjectId categoriaId
) {
}
