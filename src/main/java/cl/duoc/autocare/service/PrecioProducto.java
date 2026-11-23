package cl.duoc.autocare.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import cl.duoc.autocare.model.Producto;

final class PrecioProducto {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private PrecioProducto() {
    }

    static BigDecimal calcularPrecioFinal(Producto producto) {
        if (producto.descuento().signum() == 0) {
            return producto.precio();
        }
        return producto.precio().multiply(CIEN.subtract(producto.descuento()))
                .movePointLeft(2).setScale(0, RoundingMode.HALF_UP);
    }
}
