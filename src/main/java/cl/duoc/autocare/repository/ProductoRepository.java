package cl.duoc.autocare.repository;

import java.util.Optional;

import cl.duoc.autocare.model.Producto;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductoRepository extends MongoRepository<Producto, ObjectId> {

    Optional<Producto> findByCodigoNormalizado(String codigoNormalizado);

    boolean existsByCategoriaId(ObjectId categoriaId);
}
