package cl.duoc.autocare.config;

import java.util.List;

import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.model.Usuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import cl.duoc.autocare.service.JwtService;
import jakarta.servlet.DispatcherType;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
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
@EnableConfigurationProperties(ConfiguracionDemostracion.class)
public class SeguridadConfig {

    @Bean
    @Profile({"catalogo-local", "railway"})
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Profile({"catalogo-local", "railway"})
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
        private static final String ENVIO_ORDEN = DETALLE_ORDEN + "/envio";
        private static final String USUARIOS = "/api/v1/usuarios";
        private static final String DETALLE_USUARIO = USUARIOS + "/{id}";
        private static final String PERFIL = USUARIOS + "/me/perfil";
        private static final String CONTACTO = "/api/v1/contacto";
        private static final String INCIDENCIAS = "/api/v1/incidencias";
        private static final String RESOLUCION_INCIDENCIA = INCIDENCIAS + "/{id}/resolucion";

        @Bean
        @Profile({"catalogo-local", "railway"})
        SecurityFilterChain apiCatalogo(HttpSecurity http, JwtDecoder decodificador, UsuarioRepository usuarios,
                                       Environment entorno, ConfiguracionDemostracion demostracion) throws Exception {
            configurarSinSesion(http);
            boolean railway = entorno.matchesProfiles("railway");
            http.authorizeHttpRequests(reglas -> {
                reglas.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/salud/mongodb").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, CATALOGO).permitAll()
                        .requestMatchers(HttpMethod.HEAD, CATALOGO).permitAll();
                if (railway) {
                    reglas.requestMatchers(HttpMethod.GET, "/api/v1/configuracion").permitAll();
                    if (!demostracion.registro()) {
                        reglas.requestMatchers(HttpMethod.POST, "/api/v1/auth/registro").denyAll();
                    }
                    if (!demostracion.escrituras()) {
                        reglas.requestMatchers(CATALOGO).denyAll()
                                .requestMatchers(HttpMethod.POST, ORDENES, USUARIOS, INCIDENCIAS, CONTACTO).denyAll()
                                .requestMatchers(HttpMethod.PUT, ENVIO_ORDEN, DETALLE_USUARIO, RESOLUCION_INCIDENCIA, PERFIL).denyAll();
                    }
                } else {
                    reglas.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                }
                reglas.requestMatchers(HttpMethod.POST, "/api/v1/auth/registro").permitAll()
                        .requestMatchers(CATALOGO).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, ORDENES, DETALLE_ORDEN).hasAnyRole("ADMIN", "VENDEDOR", "CLIENTE")
                        .requestMatchers(HttpMethod.HEAD, ORDENES, DETALLE_ORDEN).hasAnyRole("ADMIN", "VENDEDOR", "CLIENTE")
                        .requestMatchers(HttpMethod.POST, ORDENES).hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.GET, USUARIOS, DETALLE_USUARIO, DETALLE_USUARIO + "/ordenes").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, USUARIOS).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, ENVIO_ORDEN, DETALLE_USUARIO, RESOLUCION_INCIDENCIA).hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, INCIDENCIAS).hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(HttpMethod.POST, INCIDENCIAS).hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.GET, PERFIL).hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, PERFIL).hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, CONTACTO).hasAnyRole("CLIENTE", "ADMIN")
                        .anyRequest().denyAll();
            });
            http.oauth2ResourceServer(recurso -> recurso.jwt(jwt -> jwt.decoder(decodificador)
                    .jwtAuthenticationConverter(token -> autenticarUsuario(token.getSubject(), usuarios))));
            return http.build();
        }

        @Bean
        @Profile("!catalogo-local & !railway")
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
