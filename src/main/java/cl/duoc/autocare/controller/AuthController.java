package cl.duoc.autocare.controller;

import cl.duoc.autocare.dto.LoginRequestDTO;
import cl.duoc.autocare.dto.LoginResponseDTO;
import cl.duoc.autocare.dto.RegistroRequestDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.service.AutenticacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"catalogo-local", "railway"})
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AutenticacionService autenticacion;

    public AuthController(AutenticacionService autenticacion) {
        this.autenticacion = autenticacion;
    }

    @Operation(summary = "Registrar una cuenta de cliente")
    @ApiResponse(responseCode = "201", description = "Cliente registrado.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Datos inválidos.")
    @ApiResponse(responseCode = "409", description = "Correo duplicado.")
    @ApiResponse(responseCode = "503", description = "Registro aún sin índice de correo.")
    @PostMapping("/registro")
    public ResponseEntity<UsuarioResponseDTO> registrar(@Valid @RequestBody RegistroRequestDTO datos) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(autenticacion.registrar(datos));
    }

    @Operation(summary = "Iniciar sesión")
    @ApiResponse(responseCode = "200", description = "Identidad y token Bearer.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Solicitud inválida.")
    @ApiResponse(responseCode = "401", description = "Credenciales inválidas.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> iniciarSesion(@Valid @RequestBody LoginRequestDTO datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(autenticacion.iniciarSesion(datos));
    }

    @Operation(summary = "Consultar la identidad autenticada")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Identidad autenticada.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "401", description = "Se requiere un JWT válido.")
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> me(@AuthenticationPrincipal UsuarioResponseDTO usuario) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usuario);
    }
}
