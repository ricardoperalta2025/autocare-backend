package cl.duoc.autocare.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import cl.duoc.autocare.dto.LoginRequestDTO;
import cl.duoc.autocare.dto.LoginResponseDTO;
import cl.duoc.autocare.dto.RegistroRequestDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile({"catalogo-local", "railway"})
public class AutenticacionService {

    private final UsuarioRepository usuarios;
    private final MongoTemplate mongo;
    private final PasswordEncoder contrasenas;
    private final JwtService jwt;
    private final String hashAuxiliar;

    public AutenticacionService(UsuarioRepository usuarios, MongoTemplate mongo, PasswordEncoder contrasenas, JwtService jwt) {
        this.usuarios = usuarios;
        this.mongo = mongo;
        this.contrasenas = contrasenas;
        this.jwt = jwt;
        this.hashAuxiliar = contrasenas.encode(UUID.randomUUID().toString());
    }

    public UsuarioResponseDTO registrar(RegistroRequestDTO datos) {
        String correo = normalizarCorreo(datos.correo());
        if (datos.contrasena().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new CatalogoException(HttpStatus.BAD_REQUEST, "La contraseña admite como máximo 72 bytes UTF-8.");
        }
        verificarIndiceDeCorreo();
        try {
            Usuario cliente = new Usuario(null, datos.nombre(), correo,
                    contrasenas.encode(datos.contrasena()), RolUsuario.CLIENTE, true);
            return UsuarioResponseDTO.desde(usuarios.insert(cliente));
        } catch (DuplicateKeyException error) {
            throw new CatalogoException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }
    }

    public LoginResponseDTO iniciarSesion(LoginRequestDTO datos) {
        if (datos.contrasena().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw credencialesInvalidas();
        }
        Usuario usuario = usuarios.findByCorreoNormalizado(normalizarCorreo(datos.correo())).orElse(null);
        boolean coincide = contrasenas.matches(datos.contrasena(), usuario == null ? hashAuxiliar : usuario.contrasenaHash());
        if (!coincide || usuario == null || !usuario.activo() || usuario.rol() == null) {
            throw credencialesInvalidas();
        }
        return jwt.emitir(usuario);
    }

    private void verificarIndiceDeCorreo() {
        boolean existe;
        try {
            existe = mongo.indexOps(Usuario.class).getIndexInfo().stream().anyMatch(indice ->
                    indice.isUnique() && !indice.isSparse() && !StringUtils.hasText(indice.getPartialFilterExpression())
                            && indice.getIndexFields().size() == 1
                            && indice.getIndexFields().getFirst().getKey().equals("correoNormalizado"));
        } catch (DataAccessException error) {
            throw new CatalogoException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo verificar el índice único del correo.");
        }
        if (!existe) {
            throw new CatalogoException(HttpStatus.SERVICE_UNAVAILABLE, "El registro requiere el índice único del correo.");
        }
    }

    private String normalizarCorreo(String correo) {
        return correo.strip().toLowerCase(Locale.ROOT);
    }

    private CatalogoException credencialesInvalidas() {
        return new CatalogoException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas.");
    }
}
