package cl.duoc.autocare.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record OrdenRequestDTO(
        @NotEmpty(message = "La orden debe incluir productos.")
        List<@NotNull(message = "El detalle del producto es obligatorio.") @Valid ProductoSolicitado> productos
) {

    public record ProductoSolicitado(
            @NotBlank(message = "El identificador del producto es obligatorio.")
            @Pattern(regexp = "[0-9a-fA-F]{24}", message = "El identificador del producto no es válido.")
            String productoId,

            @NotNull(message = "La cantidad es obligatoria.")
            @Positive(message = "La cantidad debe ser mayor que cero.")
            Integer cantidad
    ) {
    }
}
