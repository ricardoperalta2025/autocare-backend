package cl.duoc.autocare.dto;

import cl.duoc.autocare.model.Orden;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EntregaOrdenRequestDTO(
        @NotBlank @Size(max = 150) String nombre,
        @NotBlank @Email @Size(max = 254) String correo,
        @NotBlank @Size(max = 30) String telefono,
        @NotBlank @Size(max = 100) String comuna,
        @NotBlank @Size(max = 300) String direccion,
        @NotBlank @Pattern(regexp = "Envío estándar|Envío express") String tipo
) {
    public EntregaOrdenRequestDTO {
        nombre = limpiar(nombre);
        correo = limpiar(correo);
        telefono = limpiar(telefono);
        comuna = limpiar(comuna);
        direccion = limpiar(direccion);
        tipo = limpiar(tipo);
    }

    public Orden.Entrega aDocumento() {
        return new Orden.Entrega(nombre, correo, telefono, comuna, direccion, tipo);
    }

    private static String limpiar(String valor) {
        return valor == null ? null : valor.strip();
    }
}
