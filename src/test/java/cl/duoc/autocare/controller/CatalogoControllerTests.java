package cl.duoc.autocare.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import cl.duoc.autocare.config.SeguridadConfig;
import cl.duoc.autocare.model.Categoria;
import cl.duoc.autocare.model.Producto;
import cl.duoc.autocare.repository.CategoriaRepository;
import cl.duoc.autocare.repository.ProductoRepository;
import cl.duoc.autocare.repository.UsuarioRepository;
import cl.duoc.autocare.service.CategoriaService;
import cl.duoc.autocare.service.ProductoService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ProductoController.class, CategoriaController.class})
@ActiveProfiles("catalogo-local")
@Import({ProductoService.class, CategoriaService.class, SeguridadConfig.class})
class CatalogoControllerTests {

    private static final String PRODUCTOS = "/api/v1/productos";
    private static final String CATEGORIAS = "/api/v1/categorias";
    private static final ObjectId ID_CATEGORIA = new ObjectId("660000000000000000000001");
    private static final ObjectId ID_PRODUCTO = new ObjectId("660000000000000000000002");
    private static final Categoria CATEGORIA = new Categoria(ID_CATEGORIA, "Aceites", "aceites");
    private static final Producto PRODUCTO = new Producto(ID_PRODUCTO, "ACE001", "ACE001",
            "Aceite", "Sintético", new BigDecimal("35000"), new BigDecimal("20"),
            24, 5, "/images/Aceite.png", ID_CATEGORIA);

    private static final String DATOS_PRODUCTO = """
            {
              "codigo": "ACE001",
              "nombre": "Aceite",
              "descripcion": "Sintético",
              "precio": 35000,
              "descuento": 20,
              "stock": 24,
              "stockCritico": 5,
              "imagen": "/images/Aceite.png",
              "categoriaId": "%s"
            }
            """.formatted(ID_CATEGORIA.toHexString());

    private RequestPostProcessor comoAdmin() {
        return user("admin-de-prueba").roles("ADMIN");
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UsuarioRepository usuarios;

    @MockitoBean
    private ProductoRepository productos;

    @MockitoBean
    private CategoriaRepository categorias;

    @Test
    void creaCategoriaConNombreLimpioYDevuelveSuUbicacion() throws Exception {
        Categoria nueva = new Categoria(null, "Aceites", "aceites");
        when(categorias.save(nueva)).thenReturn(CATEGORIA);

        mvc.perform(post(CATEGORIAS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  Aceites  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", CATEGORIAS + "/" + ID_CATEGORIA))
                .andExpect(jsonPath("$.id").value(ID_CATEGORIA.toHexString()))
                .andExpect(jsonPath("$.nombre").value("Aceites"))
                .andExpect(jsonPath("$.nombreNormalizado").doesNotExist());

        verify(categorias).save(nueva);
    }

    @Test
    void listaCategoriasYConsultaPorId() throws Exception {
        when(categorias.findAll(Sort.by("nombreNormalizado"))).thenReturn(List.of(CATEGORIA));
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));

        mvc.perform(get(CATEGORIAS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID_CATEGORIA.toHexString()));
        mvc.perform(get(CATEGORIAS + "/" + ID_CATEGORIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Aceites"));
    }

    @Test
    void actualizaCategoriaConservandoId() throws Exception {
        Categoria editada = new Categoria(ID_CATEGORIA, "Aceites de motor", "aceites de motor");
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));
        when(categorias.save(editada)).thenReturn(editada);

        mvc.perform(put(CATEGORIAS + "/" + ID_CATEGORIA).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Aceites de motor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_CATEGORIA.toHexString()))
                .andExpect(jsonPath("$.nombre").value("Aceites de motor"));

        verify(categorias).save(editada);
    }

    @Test
    void eliminaCategoriaSinProductos() throws Exception {
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));

        mvc.perform(delete(CATEGORIAS + "/" + ID_CATEGORIA).with(comoAdmin()))
                .andExpect(status().isNoContent());

        verify(categorias).deleteById(ID_CATEGORIA);
    }

    @Test
    void rechazaEliminarCategoriaConProductos() throws Exception {
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));
        when(productos.existsByCategoriaId(ID_CATEGORIA)).thenReturn(true);

        mvc.perform(delete(CATEGORIAS + "/" + ID_CATEGORIA).with(comoAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La categoría tiene productos asociados."));

        verify(categorias, never()).deleteById(any());
    }

    @Test
    void rechazaCategoriaSinNombre() throws Exception {
        mvc.perform(post(CATEGORIAS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());

        verify(categorias, never()).save(any());
    }

    @Test
    void detectaNombreDuplicadoAunqueCambienMayusculasYEspacios() throws Exception {
        when(categorias.findByNombreNormalizado("aceites")).thenReturn(Optional.of(CATEGORIA));

        mvc.perform(post(CATEGORIAS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  ACEITES \"}"))
                .andExpect(status().isConflict());

        verify(categorias, never()).save(any());
    }

    @Test
    void informaCuandoLaCategoriaNoExiste() throws Exception {
        mvc.perform(get(CATEGORIAS + "/" + ID_CATEGORIA))
                .andExpect(status().isNotFound());
        mvc.perform(put(CATEGORIAS + "/" + ID_CATEGORIA).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Aceites\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(CATEGORIAS + "/" + ID_CATEGORIA).with(comoAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void creaProductoConCategoriaYCalculaPrecioFinal() throws Exception {
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));

        Producto nuevo = new Producto(null, "ACE001", "ACE001", "Aceite", "Sintético",
                new BigDecimal("35000"), new BigDecimal("20"), 24, 5, "/images/Aceite.png", ID_CATEGORIA);
        when(productos.save(nuevo)).thenReturn(PRODUCTO);

        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content(DATOS_PRODUCTO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", PRODUCTOS + "/" + ID_PRODUCTO))
                .andExpect(jsonPath("$.id").value(ID_PRODUCTO.toHexString()))
                .andExpect(jsonPath("$.categoriaId").value(ID_CATEGORIA.toHexString()))
                .andExpect(jsonPath("$.categoria").value("Aceites"))
                .andExpect(jsonPath("$.precioFinal").value(28000))
                .andExpect(jsonPath("$.codigoNormalizado").doesNotExist());

        verify(productos).save(nuevo);
    }

    @Test
    void usaValoresOpcionalesYConservaPrecioDecimalSinDescuento() throws Exception {
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));
        Producto nuevo = new Producto(null, "ACE001", "ACE001", "Aceite", "",
                new BigDecimal("12.50"), BigDecimal.ZERO, 0, 0, "/images/Tienda.png", ID_CATEGORIA);
        when(productos.save(nuevo)).thenReturn(new Producto(ID_PRODUCTO, nuevo.codigo(), nuevo.codigoNormalizado(),
                nuevo.nombre(), nuevo.descripcion(), nuevo.precio(), nuevo.descuento(), nuevo.stock(),
                nuevo.stockCritico(), nuevo.imagen(), nuevo.categoriaId()));

        String datosMinimos = """
                {"codigo":"ACE001","nombre":"Aceite","precio":12.50,"stock":0,"categoriaId":"%s"}
                """.formatted(ID_CATEGORIA.toHexString());

        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content(datosMinimos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioFinal").value(12.50))
                .andExpect(jsonPath("$.descuento").value(0))
                .andExpect(jsonPath("$.stockCritico").value(0))
                .andExpect(jsonPath("$.imagen").value("/images/Tienda.png"));
    }

    @Test
    void listaYConsultaProductosConElNombreActualDeLaCategoria() throws Exception {
        Categoria renombrada = new Categoria(ID_CATEGORIA, "Aceites de motor", "aceites de motor");
        when(productos.findAll(Sort.by("id"))).thenReturn(List.of(PRODUCTO));
        when(categorias.findAllById(List.of(ID_CATEGORIA))).thenReturn(List.of(renombrada));
        when(productos.findById(ID_PRODUCTO)).thenReturn(Optional.of(PRODUCTO));
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(renombrada));

        mvc.perform(get(PRODUCTOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria").value("Aceites de motor"));
        mvc.perform(get(PRODUCTOS + "/" + ID_PRODUCTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("Aceites de motor"))
                .andExpect(jsonPath("$.stock").value(24));
    }

    @Test
    void actualizaProductoConservandoSuCodigoEIdentificador() throws Exception {
        when(productos.findById(ID_PRODUCTO)).thenReturn(Optional.of(PRODUCTO));
        when(productos.findByCodigoNormalizado("ACE001")).thenReturn(Optional.of(PRODUCTO));
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));
        when(productos.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        mvc.perform(put(PRODUCTOS + "/" + ID_PRODUCTO).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS_PRODUCTO.replace("\"stock\": 24", "\"stock\": 8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_PRODUCTO.toHexString()))
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    void conservaElDescuentoSiElFormularioDeEdicionLoOmite() throws Exception {
        when(productos.findById(ID_PRODUCTO)).thenReturn(Optional.of(PRODUCTO));
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));
        when(productos.save(any(Producto.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        mvc.perform(put(PRODUCTOS + "/" + ID_PRODUCTO).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS_PRODUCTO.replace("\"descuento\": 20,", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descuento").value(20))
                .andExpect(jsonPath("$.precioFinal").value(28000));
    }

    @Test
    void eliminaProductoExistente() throws Exception {
        when(productos.findById(ID_PRODUCTO)).thenReturn(Optional.of(PRODUCTO));

        mvc.perform(delete(PRODUCTOS + "/" + ID_PRODUCTO).with(comoAdmin()))
                .andExpect(status().isNoContent());

        verify(productos).deleteById(ID_PRODUCTO);
    }

    @Test
    void informaCuandoElProductoNoExiste() throws Exception {
        mvc.perform(get(PRODUCTOS + "/" + ID_PRODUCTO))
                .andExpect(status().isNotFound());
        mvc.perform(put(PRODUCTOS + "/" + ID_PRODUCTO).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS_PRODUCTO))
                .andExpect(status().isNotFound());
        mvc.perform(delete(PRODUCTOS + "/" + ID_PRODUCTO).with(comoAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaProductoSinCamposObligatorios() throws Exception {
        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigo").exists())
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.precio").exists())
                .andExpect(jsonPath("$.campos.stock").exists())
                .andExpect(jsonPath("$.campos.categoriaId").exists());

        verify(productos, never()).save(any());
    }

    @Test
    void rechazaIdentificadoresConFormatoIncorrecto() throws Exception {
        mvc.perform(get(PRODUCTOS + "/id-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS_PRODUCTO.replace(ID_CATEGORIA.toHexString(), "id-invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.categoriaId").exists());
    }

    @ParameterizedTest
    @CsvSource({"precio, -1", "descuento, -1", "descuento, 101", "stock, -1", "stockCritico, -1"})
    void rechazaValoresFueraDeRango(String campo, int valor) throws Exception {
        String cuerpo = DATOS_PRODUCTO.replaceFirst("\"" + campo + "\": [0-9]+", "\"" + campo + "\": " + valor);

        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos." + campo).exists());

        verify(productos, never()).save(any());
    }

    @Test
    void rechazaStockFraccionario() throws Exception {
        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS_PRODUCTO.replace("\"stock\": 24", "\"stock\": 24.5")))
                .andExpect(status().isBadRequest());

        verify(productos, never()).save(any());
    }

    @Test
    void rechazaProductoConCategoriaInexistente() throws Exception {
        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content(DATOS_PRODUCTO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La categoría indicada no existe."));

        verify(productos, never()).save(any());
    }

    @Test
    void rechazaCodigoDuplicadoAunqueCambienMayusculasYEspacios() throws Exception {
        when(productos.findByCodigoNormalizado("ACE001")).thenReturn(Optional.of(PRODUCTO));

        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS_PRODUCTO.replace("ACE001", "  ace001 ")))
                .andExpect(status().isConflict());

        verify(productos, never()).save(any());
    }

    @Test
    void informaConflictoSiMongoDbDetectaUnDuplicadoAlGuardar() throws Exception {
        when(categorias.findById(ID_CATEGORIA)).thenReturn(Optional.of(CATEGORIA));
        when(productos.save(any(Producto.class))).thenThrow(new DuplicateKeyException("Detalle interno"));

        mvc.perform(post(PRODUCTOS).with(comoAdmin()).contentType(MediaType.APPLICATION_JSON).content(DATOS_PRODUCTO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un producto con ese código."));
    }
}
