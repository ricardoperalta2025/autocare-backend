package cl.duoc.autocare.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(max = 72) String contrasena
) {
    public LoginRequestDTO {
        correo = correo == null ? null : correo.strip();
    }

    @Override
    public String toString() {
        return "LoginRequestDTO[datos protegidos]";
    }
}
