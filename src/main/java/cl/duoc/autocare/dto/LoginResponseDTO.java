package cl.duoc.autocare.dto;

public record LoginResponseDTO(String token, String tipo, long expiraEnSegundos, UsuarioResponseDTO usuario) {
    @Override
    public String toString() {
        return "LoginResponseDTO[token protegido]";
    }
}
