package cl.duoc.autocare.service;

import java.util.List;

import cl.duoc.autocare.dto.ClienteAdminDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile({"catalogo-local", "railway"})
public class UsuarioAdminService {

    private final UsuarioRepository usuarios;
    private final AutenticacionService autenticacion;
    private final MongoTemplate mongo;

    public UsuarioAdminService(UsuarioRepository usuarios, AutenticacionService autenticacion, MongoTemplate mongo) {
        this.usuarios = usuarios;
        this.autenticacion = autenticacion;
        this.mongo = mongo;
    }

    public List<ClienteAdminDTO.Respuesta> listar() {
        return usuarios.findAllByRol(RolUsuario.CLIENTE, Sort.by("nombre").ascending())
                .stream().map(ClienteAdminDTO.Respuesta::desde).toList();
    }

    public ClienteAdminDTO.Respuesta buscar(ObjectId id) {
        Usuario usuario = usuarios.findById(id).filter(cuenta -> cuenta.rol() == RolUsuario.CLIENTE)
                .orElseThrow(UsuarioAdminService::clienteNoEncontrado);
        return ClienteAdminDTO.Respuesta.desde(usuario);
    }

    public ClienteAdminDTO.Respuesta crear(ClienteAdminDTO.Alta alta) {
        var datos = alta.datos();
        return ClienteAdminDTO.Respuesta.desde(autenticacion.crearCliente(
                datos.nombre(), datos.correo(), alta.contrasena(), datos.perfil()));
    }

    public ClienteAdminDTO.Respuesta perfil(ObjectId id) {
        Usuario usuario = usuarios.findById(id).filter(Usuario::activo).orElseThrow(UsuarioAdminService::clienteNoEncontrado);
        return ClienteAdminDTO.Respuesta.desde(usuario);
    }

    public ClienteAdminDTO.Respuesta editar(ObjectId id, ClienteAdminDTO.Datos datos) {
        return guardarDatos(Criteria.where("_id").is(id).and("rol").is(RolUsuario.CLIENTE), datos);
    }

    public ClienteAdminDTO.Respuesta editarPerfil(ObjectId id, ClienteAdminDTO.Datos datos) {
        return guardarDatos(Criteria.where("_id").is(id).and("activo").is(true)
                .and("rol").in(RolUsuario.CLIENTE, RolUsuario.ADMIN), datos);
    }

    private ClienteAdminDTO.Respuesta guardarDatos(Criteria criterios, ClienteAdminDTO.Datos datos) {
        autenticacion.verificarIndiceDeCorreo();
        Query filtro = Query.query(criterios);
        Update cambios = new Update().set("nombre", datos.nombre())
                .set("correoNormalizado", AutenticacionService.normalizarCorreo(datos.correo()))
                .set("perfil", datos.perfil());
        try {
            Usuario actualizado = mongo.findAndModify(filtro, cambios, FindAndModifyOptions.options().returnNew(true), Usuario.class);
            if (actualizado == null) throw clienteNoEncontrado();
            return ClienteAdminDTO.Respuesta.desde(actualizado);
        } catch (DuplicateKeyException error) {
            throw new CatalogoException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo.");
        }
    }

    private static CatalogoException clienteNoEncontrado() {
        return new CatalogoException(HttpStatus.NOT_FOUND, "Cliente no encontrado.");
    }
}
