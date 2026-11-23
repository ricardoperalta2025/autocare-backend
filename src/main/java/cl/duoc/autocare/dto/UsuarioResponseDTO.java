package cl.duoc.autocare.dto;

import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.model.Usuario;

public record UsuarioResponseDTO(String id, String nombre, String correo, RolUsuario rol) {
    public static UsuarioResponseDTO desde(Usuario usuario) {
        return new UsuarioResponseDTO(usuario.id().toHexString(), usuario.nombre(),
                usuario.correoNormalizado(), usuario.rol());
    }
}
