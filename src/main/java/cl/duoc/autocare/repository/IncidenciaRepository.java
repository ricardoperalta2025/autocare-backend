package cl.duoc.autocare.repository;

import java.util.List;

import cl.duoc.autocare.model.Incidencia;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface IncidenciaRepository extends MongoRepository<Incidencia, ObjectId> {
    List<Incidencia> findAllByCompradorId(ObjectId compradorId, Sort orden);
}
