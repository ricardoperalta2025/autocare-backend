package cl.duoc.autocare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequestDTO(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(min = 8, max = 72) String contrasena
) {
    public RegistroRequestDTO {
        nombre = nombre == null ? null : nombre.strip();
        correo = correo == null ? null : correo.strip();
    }

    @Override
    public String toString() {
        return "RegistroRequestDTO[datos protegidos]";
    }
}
