package cl.duoc.autocare.repository;

import cl.duoc.autocare.model.Orden;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrdenRepository extends MongoRepository<Orden, ObjectId> {
}
