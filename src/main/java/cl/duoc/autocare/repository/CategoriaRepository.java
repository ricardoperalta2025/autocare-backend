package cl.duoc.autocare.repository;

import java.util.Optional;

import cl.duoc.autocare.model.Categoria;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoriaRepository extends MongoRepository<Categoria, ObjectId> {

    Optional<Categoria> findByNombreNormalizado(String nombreNormalizado);
}
