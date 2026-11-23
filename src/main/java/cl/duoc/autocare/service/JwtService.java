package cl.duoc.autocare.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cl.duoc.autocare.dto.LoginResponseDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("catalogo-local")
public class JwtService {

    private static final String EMISOR = "autocarepro";
    private static final String AUDIENCIA = "autocare-api";
    private final SecretKey clave;
    private final Duration expiracion;
    private final JwtEncoder codificador;

    public JwtService(@Value("${autocare.jwt.secreto-base64}") String secreto,
                      @Value("${autocare.jwt.expiracion}") Duration expiracion) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secreto);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("JWT_SECRET_BASE64 debe ser Base64 válido.");
        }
        if (bytes.length < 32 || expiracion.toSeconds() < 1) {
            throw new IllegalStateException("Configura JWT_SECRET_BASE64 con al menos 32 bytes y una expiración positiva.");
        }
        this.clave = new SecretKeySpec(bytes, "HmacSHA256");
        this.expiracion = expiracion;
        this.codificador = NimbusJwtEncoder.withSecretKey(clave).algorithm(MacAlgorithm.HS256).build();
    }

    public JwtDecoder crearDecodificador() {
        var decodificador = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(EMISOR),
                new JwtTimestampValidator(Duration.ZERO),
                new JwtClaimValidator<Instant>("exp", valor -> valor != null),
                new JwtClaimValidator<List<String>>("aud", audiencias -> audiencias != null && audiencias.contains(AUDIENCIA))));
        return decodificador;
    }

    public LoginResponseDTO emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        var datos = JwtClaimsSet.builder().issuer(EMISOR).audience(List.of(AUDIENCIA))
                .subject(usuario.id().toHexString()).issuedAt(ahora).expiresAt(ahora.plus(expiracion)).build();
        var cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = codificador.encode(JwtEncoderParameters.from(cabecera, datos)).getTokenValue();
        return new LoginResponseDTO(token, "Bearer", expiracion.toSeconds(), UsuarioResponseDTO.desde(usuario));
    }
}
