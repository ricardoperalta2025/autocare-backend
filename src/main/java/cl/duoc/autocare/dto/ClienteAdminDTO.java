package cl.duoc.autocare.dto;

import cl.duoc.autocare.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ClienteAdminDTO {
    private ClienteAdminDTO() {
    }

    @Schema(name = "DatosCliente")
    public record Datos(
            @NotBlank @Size(max = 100) String nombre,
            @Size(max = 100) String apellido,
            @NotBlank @Email @Size(max = 254) String correo,
            @Size(max = 30) String telefono,
            @Size(max = 100) String comuna,
            @Size(max = 300) String direccion
    ) {
        public Datos {
            nombre = limpiar(nombre);
            apellido = limpiar(apellido);
            correo = limpiar(correo);
            telefono = limpiar(telefono);
            comuna = limpiar(comuna);
            direccion = limpiar(direccion);
        }

        public Usuario.Perfil perfil() {
            return new Usuario.Perfil(apellido, telefono, comuna, direccion);
        }

        private static String limpiar(String valor) {
            return valor == null ? "" : valor.strip();
        }
    }

    @Schema(name = "AltaCliente")
    public record Alta(@NotNull @Valid Datos datos,
                       @Schema(format = "password") @NotBlank @Size(min = 8, max = 72) String contrasena) {
        @Override
        public String toString() {
            return "Alta[datos de registro protegidos]";
        }
    }

    @Schema(name = "RespuestaCliente")
    public record Respuesta(String id, String nombre, String apellido, String correo, String telefono,
                            String comuna, String direccion) {
        public static Respuesta desde(Usuario usuario) {
            Usuario.Perfil perfil = usuario.perfil();
            return new Respuesta(usuario.id().toHexString(), usuario.nombre(), perfil == null ? "" : perfil.apellido(),
                    usuario.correoNormalizado(), perfil == null ? "" : perfil.telefono(),
                    perfil == null ? "" : perfil.comuna(), perfil == null ? "" : perfil.direccion());
        }
    }
}
