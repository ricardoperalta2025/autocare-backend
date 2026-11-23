package cl.duoc.autocare.repository;

import java.util.List;

import cl.duoc.autocare.model.Orden;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrdenRepository extends MongoRepository<Orden, ObjectId> {
    List<Orden> findAllByCompradorId(ObjectId compradorId, Sort orden);
}
