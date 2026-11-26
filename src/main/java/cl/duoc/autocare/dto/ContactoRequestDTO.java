package cl.duoc.autocare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContactoRequestDTO(
        @NotBlank @Size(max = 100) String nombre,
        @Size(max = 100) @Pattern(regexp = "(?i)(?:[^\\s@]+@(duoc\\.cl|profesor\\.duoc\\.cl|gmail\\.com))?") String correo,
        @NotBlank @Size(max = 500) String comentario
) {
    public ContactoRequestDTO {
        nombre = nombre == null ? null : nombre.strip();
        correo = correo == null ? "" : correo.strip();
        comentario = comentario == null ? null : comentario.strip();
    }
}
