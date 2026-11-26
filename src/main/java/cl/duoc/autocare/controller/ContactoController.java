package cl.duoc.autocare.controller;

import cl.duoc.autocare.dto.ContactoRequestDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.service.ContactoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Profile({"catalogo-local", "railway"})
@RestController
@SecurityRequirement(name = "bearerAuth")
public class ContactoController {

    private final ContactoService contacto;

    public ContactoController(ContactoService contacto) {
        this.contacto = contacto;
    }

    @PostMapping("/api/v1/contacto")
    public ResponseEntity<Void> enviar(@Valid @RequestBody ContactoRequestDTO datos,
                                       @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        contacto.enviar(datos, new ObjectId(usuario.id()));
        return ResponseEntity.noContent().build();
    }
}
