package cl.duoc.autocare.service;

import java.time.Instant;

import cl.duoc.autocare.dto.ContactoRequestDTO;
import cl.duoc.autocare.model.MensajeContacto;
import cl.duoc.autocare.repository.MensajeContactoRepository;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"catalogo-local", "railway"})
public class ContactoService {

    private final MensajeContactoRepository mensajes;

    public ContactoService(MensajeContactoRepository mensajes) {
        this.mensajes = mensajes;
    }

    public void enviar(ContactoRequestDTO datos, ObjectId usuarioId) {
        mensajes.insert(new MensajeContacto(null, usuarioId, datos.nombre(), datos.correo(), datos.comentario(), Instant.now()));
    }
}
