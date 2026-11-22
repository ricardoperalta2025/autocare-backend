package cl.duoc.autocare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequestDTO(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre admite hasta 100 caracteres.")
        String nombre
) {
    public CategoriaRequestDTO {
        nombre = nombre == null ? null : nombre.strip();
    }
}
