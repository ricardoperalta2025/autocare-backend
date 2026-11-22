# AutoCarePro Backend

Catálogo de productos y categorías de AutoCarePro. Spring Boot 4.1.3, Java 21 y Spring Data MongoDB.

## Flujo del código

Los controladores convierten HTTP en llamadas al servicio. Los DTO definen el JSON que la API recibe y devuelve. Los servicios validan duplicados y relaciones, convierten entre DTO y documentos y calculan el precio final. Los repositorios `MongoRepository` leen y escriben las colecciones `productos` y `categorias`. El controlador de errores devuelve `application/problem+json` y agrega `campos` en errores de validación.

- `ProductoService` consulta nombres de categoría en una sola lectura para el listado. Al crear o actualizar, verifica el código y la categoría antes de guardar.
- `CategoriaService` comprueba el nombre único y rechaza la eliminación si hay productos asociados. Ambos servicios coordinan las operaciones del catálogo dentro de una sola instancia del backend. Las escrituras directas en MongoDB y otras instancias quedan fuera de esa coordinación.
- El perfil `catalogo-local` habilita las rutas CRUD y hace que el servidor escuche en `127.0.0.1`. La comprobación de conexión existente usa su propio perfil `verificar-mongodb`.

## Contrato para React

Cada recurso ofrece `GET` y `POST` en `/api/v1/productos` o `/api/v1/categorias`, y `GET`, `PUT` y `DELETE` en `/{id}`. Las consultas y las ediciones devuelven `200`. La creación devuelve `201` con el ID y el encabezado `Location`. La eliminación exitosa devuelve `204`. Los datos inválidos producen `400`, los recursos ausentes `404` y los duplicados o categorías en uso `409`.

- La API devuelve `id` como texto hexadecimal de 24 caracteres. Las pantallas que hoy convierten el ID a número deberán compararlo como texto al integrarse. Para migrar los JSON, crear primero las categorías, asociar sus antiguos IDs con los nuevos por nombre y luego asociar los productos por código. El carrito anterior puede reconciliarse por código cuando esté disponible. Este módulo no carga datos de ejemplo automáticamente.
- Un producto recibe `categoriaId` al escribir. La respuesta contiene también `categoria` con el nombre visible. Renombrar una categoría conserva la referencia de los productos.
- `precio`, `descuento` y `precioFinal` se devuelven como números JSON. Se conserva la posibilidad de ingresar precios decimales del formulario actual. En MongoDB, precio y descuento se guardan como `Decimal128`. Si se aplica descuento, el precio final se redondea al peso más cercano, como en el catálogo actual.
- Descripción, imagen, descuento y stock crítico son opcionales. En una creación sus valores por defecto son descripción vacía, `/images/Tienda.png`, `0` y `0`. Si se omite el descuento al actualizar, se conserva el vigente, igual que en la edición actual de React. Enviar `0` quita la oferta. Las rutas de imágenes se conservan como texto y React seguirá sirviendo las imágenes de `public/images`.

## Ejecutar el catálogo con MongoDB Atlas desde Windows

Abre la terminal PowerShell donde ya está configurada la variable de entorno privada `MONGODB_URI`. Desde `autocare-backend`, inicia Spring Boot con el perfil `catalogo-local`.

```powershell
.\mvnw.cmd '-Dspring-boot.run.profiles=catalogo-local' `
  '-Dspring-boot.run.arguments=--spring.data.mongodb.auto-index-creation=false --logging.level.org.mongodb.driver=OFF' `
  spring-boot:run
```

El servidor HTTP escucha en `127.0.0.1:8080`. `application.properties` resuelve `spring.mongodb.uri` desde `MONGODB_URI`. El perfil conserva esa conexión. El comando desactiva la creación automática de índices y el registro del driver durante esta comprobación.

Para verificar únicamente la lectura, ejecuta estos `GET` desde otra terminal. Una respuesta `200` con `[]` indica que la consulta funcionó y la colección no tiene registros visibles.

```powershell
curl.exe -i http://127.0.0.1:8080/api/v1/categorias
curl.exe -i http://127.0.0.1:8080/api/v1/productos
```

### Prueba de escritura pendiente de autorización

Los ejemplos siguientes crean, actualizan o eliminan documentos en la base configurada en Atlas. Ejecútalos después de autorizar la prueba controlada, con nombres y códigos reservados para ella.

```powershell
$api = 'http://127.0.0.1:8080/api/v1'
$categoria = Invoke-RestMethod -Method Post -Uri "$api/categorias" -ContentType 'application/json' -Body '{"nombre":"Aceites prueba"}'
$datosProducto = @{
    codigo = 'ACE-PRUEBA'
    nombre = 'Aceite sintetico de prueba'
    descripcion = 'Producto de prueba controlada'
    precio = 35000
    descuento = 20
    stock = 24
    stockCritico = 5
    imagen = '/images/Aceite.png'
    categoriaId = $categoria.id
}
$producto = Invoke-RestMethod -Method Post -Uri "$api/productos" -ContentType 'application/json' -Body ($datosProducto | ConvertTo-Json)
Invoke-RestMethod -Uri "$api/productos/$($producto.id)"
$datosProducto.stock = 20
Invoke-RestMethod -Method Put -Uri "$api/productos/$($producto.id)" -ContentType 'application/json' -Body ($datosProducto | ConvertTo-Json)
```

El producto creado devuelve un JSON similar a este. Los IDs de ejemplo representan cadenas generadas por MongoDB.

```json
{
  "id": "660000000000000000000002",
  "codigo": "ACE-PRUEBA",
  "nombre": "Aceite sintetico de prueba",
  "descripcion": "Producto de prueba controlada",
  "precio": 35000,
  "descuento": 20,
  "precioFinal": 28000,
  "stock": 24,
  "stockCritico": 5,
  "imagen": "/images/Aceite.png",
  "categoriaId": "660000000000000000000001",
  "categoria": "Aceites prueba"
}
```

Si intentas eliminar la categoría antes del producto, la API debe responder `409`. Eliminar primero el producto y después la categoría de prueba devuelve `204` en cada caso.

```powershell
Invoke-RestMethod -Method Delete -Uri "$api/productos/$($producto.id)"
Invoke-RestMethod -Method Delete -Uri "$api/categorias/$($categoria.id)"
```

## Índices y validaciones

`productos.codigoNormalizado` y `categorias.nombreNormalizado` tienen índices únicos declarados. `productos.categoriaId` tiene un índice de consulta. La aplicación detecta duplicados con comparaciones normalizadas y devuelve `409`. Un índice ya creado en MongoDB también impide duplicados cuando la escritura llega desde otro proceso. [Creación de índices en Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/mongodb/mapping/mapping-index-management.html)

Para confirmar el estado de Atlas, revisa **Data Explorer → Indexes** en las colecciones del catálogo. Si falta algún índice, su creación requiere una operación autorizada en Atlas. Antes de crear los únicos, revisa si hay documentos con campos ausentes o valores normalizados duplicados. [Índices únicos en MongoDB](https://www.mongodb.com/docs/manual/core/index-unique/)

Los DTO rechazan campos obligatorios ausentes, precios o stocks negativos, stock fraccionario, descuentos fuera de 0 a 100 e IDs de categoría mal formados. El servicio comprueba que la categoría exista y rechaza su eliminación cuando tiene productos asociados.

## Inspeccionar Atlas y ejecutar pruebas

En Atlas, abre el clúster en **Data Explorer**, selecciona la base elegida para AutoCarePro y consulta **Documents** en `categorias` y `productos`. Compara el `_id` de la categoría con el `categoriaId` del producto. Las colecciones y los registros pueden no aparecer hasta una primera escritura autorizada. [Data Explorer de Atlas](https://www.mongodb.com/docs/atlas/atlas-ui/documents/)

Desde PowerShell, `.\mvnw.cmd test` ejecuta JUnit y Spring Boot Test. Los casos del catálogo usan MockMvc con repositorios simulados. La prueba de arranque usa una configuración local de prueba. Para una futura incorporación de Swagger UI, la dependencia propuesta es `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.1`, según la matriz para Spring Boot 4.1.x. [Compatibilidad de springdoc](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-)
