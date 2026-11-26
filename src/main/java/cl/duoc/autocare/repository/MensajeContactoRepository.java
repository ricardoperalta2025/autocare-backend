package cl.duoc.autocare.repository;

import cl.duoc.autocare.model.MensajeContacto;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MensajeContactoRepository extends MongoRepository<MensajeContacto, ObjectId> {
}
