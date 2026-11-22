package cl.duoc.autocare.dto;

import java.math.BigDecimal;

public record ProductoResponseDTO(
        String id,
        String codigo,
        String nombre,
        String descripcion,
        BigDecimal precio,
        BigDecimal descuento,
        BigDecimal precioFinal,
        int stock,
        int stockCritico,
        String imagen,
        String categoriaId,
        String categoria
) {
}
