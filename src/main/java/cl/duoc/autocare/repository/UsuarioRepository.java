package cl.duoc.autocare.repository;

import java.util.Optional;

import cl.duoc.autocare.model.Usuario;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsuarioRepository extends MongoRepository<Usuario, ObjectId> {
    Optional<Usuario> findByCorreoNormalizado(String correoNormalizado);
}
