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
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
                datos.asunto(), datos.descripcion(), Instant.now(), Incidencia.Estado.PENDIENTE, null, null)));
    }

    public void resolver(ObjectId id) {
        actualizar(id, new IncidenciaDTO.Gestion(Incidencia.Estado.RESUELTA.name(), null));
    }

    public IncidenciaDTO.Respuesta actualizar(ObjectId id, IncidenciaDTO.Gestion datos) {
        Incidencia.Estado estado = Incidencia.Estado.valueOf(datos.estado());
        // El filtro valida el estado actual en la misma operación que guarda el cambio.
        Criteria criterio = Criteria.where("_id").is(id);
        switch (estado) {
            case PENDIENTE -> criterio.and("estado").is(Incidencia.Estado.PENDIENTE);
            case EN_REVISION -> criterio.and("estado").in(Incidencia.Estado.PENDIENTE, Incidencia.Estado.EN_REVISION);
            case RESUELTA -> { }
        }
        Update cambios = new Update().set("estado", estado).set("fechaActualizacion", Instant.now());
        if (datos.respuestaAdmin() != null) {
            if (datos.respuestaAdmin().isEmpty()) cambios.unset("respuestaAdmin");
            else cambios.set("respuestaAdmin", datos.respuestaAdmin());
        }
        Incidencia actualizada = mongo.findAndModify(Query.query(criterio), cambios,
                FindAndModifyOptions.options().returnNew(true), Incidencia.class);
        if (actualizada != null) {
            return IncidenciaDTO.Respuesta.desde(actualizada);
        }
        if (!incidencias.existsById(id)) {
            throw new CatalogoException(HttpStatus.NOT_FOUND, "Incidencia no encontrada.");
        }
        throw new CatalogoException(HttpStatus.CONFLICT, "La incidencia no puede volver a un estado anterior.");
    }
}
