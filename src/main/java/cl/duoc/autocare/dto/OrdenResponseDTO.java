package cl.duoc.autocare.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.autocare.model.Orden.Entrega;
import cl.duoc.autocare.model.Orden.Estado;
import com.fasterxml.jackson.annotation.JsonInclude;

public record OrdenResponseDTO(
        String id,
        Instant fechaCreacion,
        Estado estado,
        List<Detalle> productos,
        BigDecimal total,
        @JsonInclude(JsonInclude.Include.NON_NULL) Entrega entrega
) {

    public OrdenResponseDTO(String id, Instant fechaCreacion, Estado estado, List<Detalle> productos, BigDecimal total) {
        this(id, fechaCreacion, estado, productos, total, null);
    }

    public OrdenResponseDTO sinEntrega() {
        return new OrdenResponseDTO(id, fechaCreacion, estado, productos, total);
    }

    public record Detalle(
            String productoId,
            String codigo,
            String nombre,
            int cantidad,
            BigDecimal precioOriginal,
            BigDecimal descuento,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {
    }
}
