package cl.duoc.autocare.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.autocare.config.SeguridadConfig;
import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.dto.UsuarioResponseDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.model.RolUsuario;
import cl.duoc.autocare.repository.UsuarioRepository;
import cl.duoc.autocare.service.OrdenService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrdenController.class)
@ActiveProfiles("catalogo-local")
@Import(SeguridadConfig.class)
class OrdenControllerTests {

    private static final String RUTA = "/api/v1/ordenes";
    private static final String ID_ORDEN = "660000000000000000000007";
    private static final String ID_PRODUCTO = "660000000000000000000001";
    private static final String SOLICITUD = """
            {"productos":[{"productoId":"660000000000000000000001","cantidad":2}]}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private OrdenService ordenes;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioRepository usuarios;
    private static final ObjectId COMPRADOR = new ObjectId();

    @Test
    void devuelve201ConLocationYElDetalleDeLaOrden() throws Exception {
        when(ordenes.crear(new ObjectId(ID_ORDEN),
                new OrdenRequestDTO(List.of(new OrdenRequestDTO.ProductoSolicitado(ID_PRODUCTO, 2))), COMPRADOR))
                .thenReturn(respuesta());

        mvc.perform(post(RUTA).with(comoCliente()).header("Idempotency-Key", ID_ORDEN).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", RUTA + "/" + ID_ORDEN))
                .andExpect(jsonPath("$.estado").value("PROCESANDO"))
                .andExpect(jsonPath("$.fechaCreacion").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.productos[0].productoId").value(ID_PRODUCTO))
                .andExpect(jsonPath("$.productos[0].precioUnitario").value(28000))
                .andExpect(jsonPath("$.total").value(56000));
    }

    @Test
    void consultaOrdenPorIdConLosDatosHistoricosDelComprobante() throws Exception {
        when(ordenes.buscar(new ObjectId(ID_ORDEN), COMPRADOR)).thenReturn(respuesta());

        mvc.perform(get(RUTA + "/" + ID_ORDEN).with(comoCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_ORDEN))
                .andExpect(jsonPath("$.fechaCreacion").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.estado").value("PROCESANDO"))
                .andExpect(jsonPath("$.productos[0].nombre").value("Aceite"))
                .andExpect(jsonPath("$.productos[0].cantidad").value(2))
                .andExpect(jsonPath("$.productos[0].precioOriginal").value(35000))
                .andExpect(jsonPath("$.productos[0].descuento").value(20))
                .andExpect(jsonPath("$.productos[0].precioUnitario").value(28000))
                .andExpect(jsonPath("$.productos[0].subtotal").value(56000))
                .andExpect(jsonPath("$.total").value(56000));
    }

    @Test
    void listaOrdenes() throws Exception {
        when(ordenes.listar(COMPRADOR)).thenReturn(List.of(respuesta()));

        mvc.perform(get(RUTA).with(comoCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID_ORDEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"productos\":[]}",
            "{\"productos\":[null]}",
            "{\"productos\":[{\"cantidad\":1}]}",
            "{\"productos\":[{\"productoId\":\"invalido\",\"cantidad\":1}]}",
            "{\"productos\":[{\"productoId\":\"660000000000000000000001\"}]}",
            "{\"productos\":[{\"productoId\":\"660000000000000000000001\",\"cantidad\":0}]}",
            "{\"productos\":[{\"productoId\":\"660000000000000000000001\",\"cantidad\":-1}]}",
            "{\"productos\":[{\"productoId\":\"660000000000000000000001\",\"cantidad\":1.5}]}"
    })
    void rechazaPayloadsInvalidosAntesDeInvocarElServicio(String contenido) throws Exception {
        mvc.perform(post(RUTA).with(comoCliente()).header("Idempotency-Key", ID_ORDEN).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(ordenes);
    }

    @Test
    void rechazaIdDeOrdenInvalido() throws Exception {
        mvc.perform(get(RUTA + "/invalido").with(comoCliente())).andExpect(status().isBadRequest());

        verifyNoInteractions(ordenes);
    }

    @Test
    void entregaProblema409ParaStockInsuficiente() throws Exception {
        when(ordenes.crear(eq(new ObjectId(ID_ORDEN)), any(), eq(COMPRADOR))).thenThrow(new CatalogoException(HttpStatus.CONFLICT, "Stock insuficiente."));

        mvc.perform(post(RUTA).with(comoCliente()).header("Idempotency-Key", ID_ORDEN).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Stock insuficiente."));
    }

    @Test
    void exigeLaCabeceraIdempotencyKey() throws Exception {
        mvc.perform(post(RUTA).with(comoCliente()).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));

        verifyNoInteractions(ordenes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-es-un-object-id", "66000000000000000000000z"})
    void rechazaClavesInvalidas(String clave) throws Exception {
        mvc.perform(post(RUTA).with(comoCliente()).header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(ordenes);
    }

    @Test
    void validaElDespachoAntesDeProcesarLaCompra() throws Exception {
        String incompleta = """
                {"productos":[{"productoId":"660000000000000000000001","cantidad":2}],"entrega":{"tipo":"Express"}}
                """;
        mvc.perform(post(RUTA).with(comoCliente()).header("Idempotency-Key", ID_ORDEN)
                        .contentType(MediaType.APPLICATION_JSON).content(incompleta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['entrega.nombre']").exists())
                .andExpect(jsonPath("$.campos['entrega.tipo']").exists());
        verifyNoInteractions(ordenes);
    }

    private RequestPostProcessor comoCliente() {
        var usuario = new UsuarioResponseDTO(COMPRADOR.toHexString(), "Cliente", "cliente@example.test", RolUsuario.CLIENTE);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }

    private OrdenResponseDTO respuesta() {
        var detalle = new OrdenResponseDTO.Detalle(ID_PRODUCTO, "ACE-TEST", "Aceite", 2,
                BigDecimal.valueOf(35000), BigDecimal.valueOf(20), BigDecimal.valueOf(28000), BigDecimal.valueOf(56000));
        return new OrdenResponseDTO(ID_ORDEN, Instant.parse("2026-01-01T00:00:00Z"), Orden.Estado.PROCESANDO,
                List.of(detalle), BigDecimal.valueOf(56000));
    }
}
