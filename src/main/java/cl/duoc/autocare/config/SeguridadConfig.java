package cl.duoc.autocare.config;

import java.util.List;

import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import cl.duoc.autocare.service.JwtService;
import jakarta.servlet.DispatcherType;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

    @Bean
    @Profile("catalogo-local")
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Profile("catalogo-local")
    JwtDecoder jwtDecoder(JwtService jwt) {
        return jwt.crearDecodificador();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @EnableWebSecurity
    static class Filtros {

        private static final String[] CATALOGO = {"/api/v1/productos", "/api/v1/productos/{id}",
                "/api/v1/categorias", "/api/v1/categorias/{id}"};
        private static final String ORDENES = "/api/v1/ordenes";
        private static final String DETALLE_ORDEN = ORDENES + "/{id}";

        @Bean
        @Profile("catalogo-local")
        SecurityFilterChain apiLocal(HttpSecurity http, JwtDecoder decodificador, UsuarioRepository usuarios) throws Exception {
            configurarSinSesion(http);
            http.authorizeHttpRequests(reglas -> reglas
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/salud/mongodb").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                    .requestMatchers(HttpMethod.GET, CATALOGO).permitAll()
                    .requestMatchers(HttpMethod.HEAD, CATALOGO).permitAll()
                    .requestMatchers(CATALOGO).hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, ORDENES, DETALLE_ORDEN).hasAnyRole("ADMIN", "VENDEDOR", "CLIENTE")
                    .requestMatchers(HttpMethod.HEAD, ORDENES, DETALLE_ORDEN).hasAnyRole("ADMIN", "VENDEDOR", "CLIENTE")
                    .requestMatchers(HttpMethod.POST, ORDENES).hasAnyRole("ADMIN", "CLIENTE")
                    .anyRequest().denyAll());
            http.oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.decoder(decodificador)
                    .jwtAuthenticationConverter(token -> autenticarUsuario(token.getSubject(), usuarios))));
            return http.build();
        }

        @Bean
        @Profile("!catalogo-local")
        SecurityFilterChain sinApiLocal(HttpSecurity http) throws Exception {
            configurarSinSesion(http);
            http.authorizeHttpRequests(reglas -> reglas
                    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/salud/mongodb").permitAll()
                    .anyRequest().denyAll());
            http.exceptionHandling(errores -> errores
                    .authenticationEntryPoint((peticion, respuesta, error) -> respuesta.setStatus(404))
                    .accessDeniedHandler((peticion, respuesta, error) -> respuesta.setStatus(404)));
            return http.build();
        }

        private void configurarSinSesion(HttpSecurity http) {
            http.csrf(csrf -> csrf.disable())
                    .logout(logout -> logout.disable())
                    .requestCache(cache -> cache.disable())
                    .sessionManagement(sesiones -> sesiones.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        }

        private UsernamePasswordAuthenticationToken autenticarUsuario(String sujeto, UsuarioRepository usuarios) {
            if (sujeto == null || !ObjectId.isValid(sujeto)) {
                throw new OAuth2AuthenticationException("invalid_token");
            }
            Usuario usuario = usuarios.findById(new ObjectId(sujeto))
                    .filter(cuenta -> cuenta.activo() && cuenta.rol() != null)
                    .orElseThrow(() -> new OAuth2AuthenticationException("invalid_token"));
            var permisos = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol().name()));
            return UsernamePasswordAuthenticationToken.authenticated(UsuarioResponseDTO.desde(usuario), null, permisos);
        }
    }
}
