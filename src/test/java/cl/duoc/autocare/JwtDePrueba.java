package cl.duoc.autocare;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;

import org.bson.types.ObjectId;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class JwtDePrueba {

    private static final byte[] CLAVE = new SecureRandom().generateSeed(32);

    private JwtDePrueba() {
    }

    public static void configurar(DynamicPropertyRegistry propiedades) {
        propiedades.add("autocare.jwt.secreto-base64", () -> Base64.getEncoder().encodeToString(CLAVE));
    }

    public static String firmar(ObjectId usuario, Instant expiracion) {
        var clave = new SecretKeySpec(CLAVE, "HmacSHA256");
        var codificador = NimbusJwtEncoder.withSecretKey(clave).algorithm(MacAlgorithm.HS256).build();
        var claims = JwtClaimsSet.builder().subject(usuario.toHexString())
                .issuer("autocarepro").audience(List.of("autocare-api")).expiresAt(expiracion)
                .claim("rol", "ADMIN").build();
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
