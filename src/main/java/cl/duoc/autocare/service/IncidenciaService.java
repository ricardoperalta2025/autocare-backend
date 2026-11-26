package cl.duoc.autocare.service;

import java.time.Instant;
import java.util.List;

import cl.duoc.autocare.dto.IncidenciaDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Incidencia;
import cl.duoc.autocare.repository.IncidenciaRepository;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile({"catalogo-local", "railway"})
public class IncidenciaService {

    private final IncidenciaRepository incidencias;
    private final OrdenService ordenes;
    private final MongoTemplate mongo;

    public IncidenciaService(IncidenciaRepository incidencias, OrdenService ordenes, MongoTemplate mongo) {
        this.incidencias = incidencias;
        this.ordenes = ordenes;
        this.mongo = mongo;
    }

    public List<IncidenciaDTO.Respuesta> listar(ObjectId compradorId) {
        Sort orden = Sort.by("fecha").descending().and(Sort.by("id").descending());
        List<Incidencia> resultados = compradorId == null ? incidencias.findAll(orden)
                : incidencias.findAllByCompradorId(compradorId, orden);
        return resultados.stream().map(IncidenciaDTO.Respuesta::desde).toList();
    }

    public IncidenciaDTO.Respuesta crear(IncidenciaDTO.Solicitud datos, ObjectId compradorId) {
        ObjectId ordenId = new ObjectId(datos.ordenId());
        ordenes.buscar(ordenId, compradorId);
        return IncidenciaDTO.Respuesta.desde(incidencias.insert(new Incidencia(null, compradorId, ordenId,
                datos.asunto(), datos.descripcion(), Instant.now(), Incidencia.Estado.PENDIENTE)));
    }

    public void resolver(ObjectId id) {
        Query filtro = Query.query(Criteria.where("_id").is(id));
        if (mongo.updateFirst(filtro, new Update().set("estado", Incidencia.Estado.RESUELTA), Incidencia.class)
                .getMatchedCount() == 0) {
            throw new CatalogoException(HttpStatus.NOT_FOUND, "Incidencia no encontrada.");
        }
    }
}
