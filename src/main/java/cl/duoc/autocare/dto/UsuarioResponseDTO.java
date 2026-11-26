package cl.duoc.autocare.dto;

import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.model.Usuario;

public record UsuarioResponseDTO(String id, String nombre, String correo, RolUsuario rol) {
    public static UsuarioResponseDTO desde(Usuario usuario) {
        String apellido = usuario.perfil() == null ? null : usuario.perfil().apellido();
        String nombre = apellido == null || apellido.isBlank() ? usuario.nombre() : usuario.nombre() + " " + apellido;
        return new UsuarioResponseDTO(usuario.id().toHexString(), nombre,
                usuario.correoNormalizado(), usuario.rol());
    }
}
