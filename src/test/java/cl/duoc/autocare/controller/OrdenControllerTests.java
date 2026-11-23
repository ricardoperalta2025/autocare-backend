package cl.duoc.autocare.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.duoc.autocare.dto.OrdenRequestDTO;
import cl.duoc.autocare.dto.OrdenResponseDTO;
import cl.duoc.autocare.exception.CatalogoException;
import cl.duoc.autocare.model.Orden;
import cl.duoc.autocare.service.OrdenService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrdenController.class)
@ActiveProfiles("catalogo-local")
class OrdenControllerTests {

    private static final String RUTA = "/api/v1/ordenes";
    private static final String ID_ORDEN = "660000000000000000000007";
    private static final String ID_PRODUCTO = "660000000000000000000001";
    private static final String SOLICITUD = """
            {"productos":[{"productoId":"660000000000000000000001","cantidad":2}]}
            """;

    @Autowired private MockMvc mvc;
    @MockitoBean private OrdenService ordenes;

    @Test
    void devuelve201ConLocationYElDetalleDeLaOrden() throws Exception {
        when(ordenes.crear(new ObjectId(ID_ORDEN),
                new OrdenRequestDTO(List.of(new OrdenRequestDTO.ProductoSolicitado(ID_PRODUCTO, 2)))))
                .thenReturn(respuesta());

        mvc.perform(post(RUTA).header("Idempotency-Key", ID_ORDEN).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", RUTA + "/" + ID_ORDEN))
                .andExpect(jsonPath("$.estado").value("PROCESANDO"))
                .andExpect(jsonPath("$.fechaCreacion").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.productos[0].productoId").value(ID_PRODUCTO))
                .andExpect(jsonPath("$.productos[0].precioUnitario").value(28000))
                .andExpect(jsonPath("$.total").value(56000));
    }

    @Test
    void consultaOrdenPorId() throws Exception {
        when(ordenes.buscar(new ObjectId(ID_ORDEN))).thenReturn(respuesta());

        mvc.perform(get(RUTA + "/" + ID_ORDEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_ORDEN));
    }

    @Test
    void listaOrdenes() throws Exception {
        when(ordenes.listar()).thenReturn(List.of(respuesta()));

        mvc.perform(get(RUTA))
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
        mvc.perform(post(RUTA).header("Idempotency-Key", ID_ORDEN).contentType(MediaType.APPLICATION_JSON).content(contenido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(ordenes);
    }

    @Test
    void rechazaIdDeOrdenInvalido() throws Exception {
        mvc.perform(get(RUTA + "/invalido")).andExpect(status().isBadRequest());

        verifyNoInteractions(ordenes);
    }

    @Test
    void entregaProblema409ParaStockInsuficiente() throws Exception {
        when(ordenes.crear(eq(new ObjectId(ID_ORDEN)), any())).thenThrow(new CatalogoException(HttpStatus.CONFLICT, "Stock insuficiente."));

        mvc.perform(post(RUTA).header("Idempotency-Key", ID_ORDEN).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Stock insuficiente."));
    }

    @Test
    void exigeLaCabeceraIdempotencyKey() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));

        verifyNoInteractions(ordenes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "no-es-un-object-id", "66000000000000000000000z"})
    void rechazaClavesInvalidas(String clave) throws Exception {
        mvc.perform(post(RUTA).header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(ordenes);
    }

    private OrdenResponseDTO respuesta() {
        var detalle = new OrdenResponseDTO.Detalle(ID_PRODUCTO, "ACE-TEST", "Aceite", 2,
                BigDecimal.valueOf(35000), BigDecimal.valueOf(20), BigDecimal.valueOf(28000), BigDecimal.valueOf(56000));
        return new OrdenResponseDTO(ID_ORDEN, Instant.parse("2026-01-01T00:00:00Z"), Orden.Estado.PROCESANDO,
                List.of(detalle), BigDecimal.valueOf(56000));
    }
}
