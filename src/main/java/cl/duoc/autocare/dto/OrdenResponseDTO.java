package cl.duoc.autocare.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.autocare.model.Orden.Estado;

public record OrdenResponseDTO(
        String id,
        Instant fechaCreacion,
        Estado estado,
        List<Detalle> productos,
        BigDecimal total
) {

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
