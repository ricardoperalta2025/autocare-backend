package cl.duoc.autocare.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoRequestDTO(
        @NotBlank(message = "El código es obligatorio.")
        @Size(min = 3, max = 64, message = "El código debe tener entre 3 y 64 caracteres.")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres.")
        String nombre,

        @Size(max = 500, message = "La descripción admite hasta 500 caracteres.")
        String descripcion,

        @NotNull(message = "El precio es obligatorio.")
        @PositiveOrZero(message = "El precio debe ser mayor o igual a cero.")
        @Digits(integer = 16, fraction = 16, message = "El precio excede la precisión admitida.")
        @DecimalMax(value = "9007199254740991", message = "El precio excede el máximo admitido.")
        BigDecimal precio,

        @PositiveOrZero(message = "El descuento debe ser mayor o igual a cero.")
        @DecimalMax(value = "100", message = "El descuento no puede superar el 100 %.")
        @Digits(integer = 3, fraction = 16, message = "El descuento excede la precisión admitida.")
        BigDecimal descuento,

        @NotNull(message = "El stock es obligatorio.")
        @PositiveOrZero(message = "El stock debe ser mayor o igual a cero.")
        Integer stock,

        @PositiveOrZero(message = "El stock crítico debe ser mayor o igual a cero.")
        Integer stockCritico,

        @Size(max = 2048, message = "La ruta de imagen admite hasta 2048 caracteres.")
        String imagen,

        @NotBlank(message = "La categoría es obligatoria.")
        @Pattern(regexp = "[0-9a-fA-F]{24}", message = "El identificador de categoría no es válido.")
        String categoriaId
) {
    public ProductoRequestDTO {
        codigo = codigo == null ? null : codigo.strip();
        nombre = nombre == null ? null : nombre.strip();
        descripcion = descripcion == null ? "" : descripcion.strip();
        imagen = imagen == null || imagen.isBlank() ? "/images/Tienda.png" : imagen.strip();
        categoriaId = categoriaId == null ? null : categoriaId.strip();
    }
}
