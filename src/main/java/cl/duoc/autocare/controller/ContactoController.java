package cl.duoc.autocare.controller;

import cl.duoc.autocare.dto.ContactoRequestDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.service.ContactoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Contacto")
@SecurityRequirement(name = "bearerAuth")
public class ContactoController {

    private final ContactoService contacto;

    public ContactoController(ContactoService contacto) {
        this.contacto = contacto;
    }

    @Operation(summary = "Guardar un mensaje de contacto", description = "ADMIN o CLIENTE. En Railway requiere escrituras habilitadas.")
    @ApiResponse(responseCode = "204", description = "Mensaje guardado.", content = @Content)
    @ApiResponse(responseCode = "400", ref = "#/components/responses/DatosInvalidos")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/SesionRequerida")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/PermisoInsuficiente")
    @PostMapping("/api/v1/contacto")
    public ResponseEntity<Void> enviar(@Valid @RequestBody ContactoRequestDTO datos,
                                       @AuthenticationPrincipal UsuarioResponseDTO usuario) {
        contacto.enviar(datos, new ObjectId(usuario.id()));
        return ResponseEntity.noContent().build();
    }
}
