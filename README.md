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

Abre la terminal PowerShell donde están configuradas las variables privadas `MONGODB_URI` y `JWT_SECRET_BASE64`. Consulta la sección de autenticación para generar una clave local. Desde `autocare-backend`, inicia Spring Boot con el perfil `catalogo-local`.

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

### Swagger UI y OpenAPI en desarrollo

Con `catalogo-local` activo, abre [Swagger UI](http://127.0.0.1:8080/swagger-ui.html) para ver los grupos **Productos** y **Categorías**. El contrato en JSON está en [OpenAPI](http://127.0.0.1:8080/v3/api-docs). El perfil mantiene la API en `127.0.0.1` y la conexión a Atlas se configura desde `MONGODB_URI`.

1. Expande `GET /api/v1/categorias` o `GET /api/v1/productos`.
2. Pulsa **Try it out** y después **Execute**. Revisa el estado `200` y el JSON en **Server response**.
3. Para probar un detalle, copia un ID real del listado y pégalo en el `GET` que pide `{id}`. El listado puede devolver `[]` si la colección está vacía.

Los métodos de escritura incluyen ejemplos de JSON para consultar el contrato. El ejemplo de producto usa un `categoriaId` ficticio. Conserva el alcance de lectura durante la demostración y usa datos autorizados en pruebas de escritura. El perfil normal y `verificar-mongodb` devuelven `404` en las rutas de documentación.

### Prueba de escritura pendiente de autorización

Los ejemplos siguientes crean, actualizan o eliminan documentos en la base configurada en Atlas. Ejecútalos después de autorizar la prueba controlada, con nombres y códigos reservados para ella. Inicia sesión con una cuenta ADMIN y coloca su JWT en la variable de sesión `$cabecerasAdmin`.

```powershell
$api = 'http://127.0.0.1:8080/api/v1'
$tokenAdmin = [System.Net.NetworkCredential]::new('', (Read-Host 'JWT del administrador' -AsSecureString)).Password
$cabecerasAdmin = @{ Authorization = ('Bearer ' + $tokenAdmin) }
$categoria = Invoke-RestMethod -Method Post -Headers $cabecerasAdmin -Uri "$api/categorias" -ContentType 'application/json' -Body '{"nombre":"Aceites prueba"}'
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
$producto = Invoke-RestMethod -Method Post -Headers $cabecerasAdmin -Uri "$api/productos" -ContentType 'application/json' -Body ($datosProducto | ConvertTo-Json)
Invoke-RestMethod -Uri "$api/productos/$($producto.id)"
$datosProducto.stock = 20
Invoke-RestMethod -Method Put -Headers $cabecerasAdmin -Uri "$api/productos/$($producto.id)" -ContentType 'application/json' -Body ($datosProducto | ConvertTo-Json)
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
Invoke-RestMethod -Method Delete -Headers $cabecerasAdmin -Uri "$api/productos/$($producto.id)"
Invoke-RestMethod -Method Delete -Headers $cabecerasAdmin -Uri "$api/categorias/$($categoria.id)"
```

## Índices y validaciones

`productos.codigoNormalizado` y `categorias.nombreNormalizado` tienen índices únicos declarados. `productos.categoriaId` tiene un índice de consulta. La aplicación detecta duplicados con comparaciones normalizadas y devuelve `409`. Un índice ya creado en MongoDB también impide duplicados cuando la escritura llega desde otro proceso. [Creación de índices en Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/mongodb/mapping/mapping-index-management.html)

Para confirmar el estado de Atlas, revisa **Data Explorer → Indexes** en las colecciones del catálogo. Si falta algún índice, su creación requiere una operación autorizada en Atlas. Antes de crear los únicos, revisa si hay documentos con campos ausentes o valores normalizados duplicados. [Índices únicos en MongoDB](https://www.mongodb.com/docs/manual/core/index-unique/)

Los DTO rechazan campos obligatorios ausentes, precios o stocks negativos, stock fraccionario, descuentos fuera de 0 a 100 e IDs de categoría mal formados. El servicio comprueba que la categoría exista y rechaza su eliminación cuando tiene productos asociados.

## Inspeccionar Atlas y ejecutar pruebas

En Atlas, abre el clúster en **Data Explorer**, selecciona la base elegida para AutoCarePro y consulta **Documents** en `categorias` y `productos`. Compara el `_id` de la categoría con el `categoriaId` del producto. Las colecciones y los registros pueden no aparecer hasta una primera escritura autorizada. [Data Explorer de Atlas](https://www.mongodb.com/docs/atlas/atlas-ui/documents/)

Desde PowerShell, `.\mvnw.cmd test` ejecuta JUnit y Spring Boot Test. Los casos del catálogo usan MockMvc con repositorios simulados. La prueba de arranque usa una configuración local de prueba. Las pruebas de OpenAPI consultan los tres perfiles por HTTP en puertos locales efímeros. Swagger UI usa `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.1`, compatible con Spring Boot 4.1.x. [Compatibilidad de springdoc](https://springdoc.org/)

## Autenticación y roles

La API usa Spring Security y JWT en el perfil `catalogo-local`. La clave es compartida únicamente por instancias autorizadas del backend. Spring valida la firma HS256, el emisor, la audiencia y la expiración. Cada petición con token consulta la cuenta por el identificador del JWT y usa el rol y estado vigentes de MongoDB. Las contraseñas se guardan como hashes BCrypt con sal aleatoria y se comprueban con `PasswordEncoder.matches`. [JWT en Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)

| Solicitud | Resultado |
| --- | --- |
| `POST /api/v1/auth/registro` | `201` con la identidad CLIENTE. `400` por datos inválidos, `409` por correo duplicado y `503` si la unicidad aún no está disponible. |
| `POST /api/v1/auth/login` | `200` con `token`, `tipo`, `expiraEnSegundos` y `usuario`. `401` por credenciales inválidas o cuenta inactiva. |
| `GET /api/v1/auth/me` | `200` con `id`, `nombre`, `correo` y `rol`. Requiere `Authorization: Bearer <JWT>`. |

```json
{"nombre":"Cliente de demostración","correo":"cliente@example.test","contrasena":"<valor-privado-de-prueba>"}
```

El login recibe solo `correo` y `contrasena`. En Swagger, copia el valor de `token` a **Authorize → bearerAuth** para las operaciones protegidas. La sesión queda del lado del cliente y la respuesta de login lleva `Cache-Control: no-store`.

| Operación | Anónimo | ADMIN | VENDEDOR | CLIENTE |
| --- | --- | --- | --- | --- |
| Leer catálogo, categorías y detalles | Sí | Sí | Sí | Sí |
| Crear, editar o eliminar productos y categorías | 401 | Sí | 403 | 403 |
| Crear órdenes | 401 | Sí, con su identidad | 403 | Sí |
| Leer órdenes | 401 | Todas | Todas | Propias |
| `GET /api/v1/auth/me` | 401 | Sí | Sí | Sí |

La colección `ordenes` guarda `compradorId` en las compras nuevas a partir del principal autenticado. El cliente obtiene `404` para una orden ajena o inexistente. Reutilizar la clave de otro comprador devuelve `403`, incluso si coinciden los productos y cantidades. El personal puede consultar órdenes históricas sin comprador. Esos documentos conservan su estado histórico.

### Configurar el entorno

`MONGODB_URI` apunta a la base autorizada. `JWT_SECRET_BASE64` contiene una clave aleatoria de al menos 32 bytes codificada en Base64. `JWT_EXPIRACION` es una duración ISO 8601 y su valor predeterminado es `PT30M`. Genera la clave en la misma terminal PowerShell donde iniciarás Spring Boot. Esta asignación conserva el valor en el entorno de la terminal.

```powershell
$bytesJwt = [byte[]]::new(32)
$aleatorioJwt = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $aleatorioJwt.GetBytes($bytesJwt) } finally { $aleatorioJwt.Dispose() }
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytesJwt)
$env:JWT_EXPIRACION = 'PT30M'
```

### Preparar tres cuentas de demostración

1. Tras autorizar las escrituras, usa una base de demostración y crea el índice único de `usuarios.correoNormalizado`. Con la creación automática de índices deshabilitada, el registro comprueba que exista y devuelve `503` si falta. Verifica que el índice tenga `unique: true`, sin filtro parcial ni opción sparse. [Índices únicos de MongoDB](https://www.mongodb.com/docs/manual/core/index-unique/)
2. Registra tres cuentas con correos de prueba distintos y contraseñas distintas generadas fuera del repositorio. Las tres se crean como CLIENTE.
3. Desde Data Explorer con una cuenta autorizada, identifica por `_id` y correo normalizado las dos cuentas designadas. Cambia únicamente `rol` a `ADMIN` y `VENDEDOR` respectivamente. Conserva la tercera como `CLIENTE` y el hash creado por el registro en las tres.
4. Inicia sesión con cada cuenta y valida su rol con `/api/v1/auth/me`. Comprueba las operaciones de la matriz con productos temporales y solicita nuevamente el token si vence.

```javascript
// Ejecutar únicamente en la base de demostración autorizada.
db.usuarios.createIndex({ correoNormalizado: 1 }, { unique: true, name: "correo_normalizado_unico" })
db.usuarios.getIndexes()
```

### Integración React de la etapa 5B

Conecta login y registro al backend, restaura la identidad con `/auth/me` y maneja `401` como sesión vencida o inválida y `403` como falta de permiso. Envía el Bearer en rutas protegidas. El proxy de Vite deberá admitir los dos POST de autenticación y las rutas administrativas exactas que integre React. Mantén el intento de compra ligado al ID del comprador autenticado y reutiliza el mismo `Idempotency-Key` y JSON al recuperarlo. Para órdenes ajenas, la autorización se resuelve siempre en Spring Boot. La documentación de esta etapa conserva el envío real de React bajo su configuración de prueba.

## Órdenes e inventario

Las rutas de compra simulada se habilitan con `catalogo-local`. El servidor calcula el total y devuelve una orden en estado `PROCESANDO` con fecha UTC y un detalle histórico por producto. La solicitud admite un ID MongoDB y una cantidad entera positiva por línea. Los IDs repetidos se rechazan con `400`, incluso si su escritura hexadecimal cambia de mayúsculas a minúsculas.

| Método y ruta | Resultado |
| --- | --- |
| `POST /api/v1/ordenes` | Requiere Bearer ADMIN o CLIENTE. `201` con la orden y su cabecera `Location`, también al repetir una compra confirmada. `400` por clave ausente o inválida o por datos inválidos, `404` por producto ausente, `409` por stock insuficiente, conflicto de escritura o reutilizar la clave con otros datos, y `503` si la compra no se puede confirmar. |
| `GET /api/v1/ordenes/{id}` | Requiere Bearer. `200` con el detalle, `400` por ID inválido o `404` si la orden no existe. |
| `GET /api/v1/ordenes` | Requiere Bearer. `200` con el listado, de más reciente a más antiguo. |

```json
{
  "productos": [
    { "productoId": "660000000000000000000001", "cantidad": 2 }
  ]
}
```

Cada línea de respuesta incluye `productoId`, `codigo`, `nombre`, `cantidad`, `precioOriginal`, `descuento`, `precioUnitario` y `subtotal`. Con precio original de 35000 y descuento de 20 %, dos unidades producen un subtotal y total de 56000. La API devuelve los importes como números. En MongoDB, `subtotal` y `total` se guardan como cadenas decimales para mantener exactitud incluso cuando la suma supera la precisión de Decimal128.

### Identificar un intento de compra

Envía `Idempotency-Key` en cada POST. Debe contener exactamente 24 caracteres hexadecimales. Genera una clave aleatoria por compra y guarda la clave junto con el JSON de la solicitud antes de enviar. Por ejemplo, el encabezado puede tener el valor `660000000000000000000007`. El backend usa ese ObjectId como `_id` de la orden y conserva el cuerpo de solicitud actual.

Al reenviar la misma clave y los mismos productos y cantidades, Spring devuelve el `201`, el `Location` y la orden original con sus importes históricos. El orden de las líneas es indiferente. Si cambian los productos o cantidades de una clave que ya tiene una orden, devuelve `409`.

MongoDB ya crea el índice único obligatorio `_id_` sobre `ordenes._id` con patrón `{ _id: 1 }` al crear la colección. No hay un índice adicional de idempotencia que instalar ni una migración de órdenes. Si aún falta `ordenes`, prepara la colección antes de probar, con autorización. Verifica `autocarepro → ordenes → Indexes` en Atlas o consulta `db.getSiblingDB("autocarepro").ordenes.getIndexes()` y busca el índice `_id_`. [Índice `_id` de MongoDB](https://www.mongodb.com/docs/manual/indexes/)

### Qué asegura la transacción

Spring usa un `MongoTransactionManager` sobre la misma fábrica de conexiones que usan los repositorios y `MongoTemplate`. Valida todo el carrito, aplica un `$inc` negativo por producto con filtro `stock >= cantidad` e inserta la orden en la misma transacción, con lectura `snapshot` y confirmación `majority`. Si un descuento falla o MongoDB rechaza la orden, se abortan los cambios de la transacción. Si falla la transacción o la respuesta de confirmación queda incierta, Spring busca el mismo ID en el primario con lectura `majority`. Si la orden ya está confirmada, devuelve su resultado. Si todavía no se puede confirmar, consulta ese ID por GET o reintenta con la misma clave y el mismo cuerpo. Un `404` en la consulta puede indicar que la orden aún no es visible. Una compra distinta usa una clave nueva. Conserva el documento de la orden mientras debas reconocer sus reintentos. [Transacciones en Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html) · [Atomicidad en MongoDB](https://www.mongodb.com/docs/manual/core/transactions/)

El servidor debe pertenecer a un replica set o clúster compatible con transacciones. Un `mongod` en modo standalone no sirve para esta prueba. Usar la URI de conexión de ese despliegue y preparar las colecciones `productos` y `ordenes` antes de comprar evita depender de la creación de colecciones durante la transacción. La aplicación no crea esas colecciones al arrancar este módulo. [Operaciones de MongoDB en transacciones](https://www.mongodb.com/docs/manual/core/transactions-operations/)

### Probar desde Swagger con una base aislada

1. Usa una instancia local de MongoDB con un replica set de prueba. En una terminal nueva, configura `MONGODB_URI` a esa instancia y una base de demostración independiente, por ejemplo `mongodb://127.0.0.1:27018/autocare_ordenes_demo?replicaSet=rsAutocareTest&directConnection=true`. Usa otro puerto para la aplicación si ya está corriendo una instancia con la conexión de Atlas.
2. En la base de demostración, crea la colección vacía `ordenes`. Inicia Spring Boot con `catalogo-local` como se indica arriba. En **Categorías** y **Productos** de Swagger, crea una categoría y productos ficticios con stock conocido. El CRUD prepara `productos` al insertar.
3. Autoriza Swagger con el JWT de la cuenta que hará la compra. En **Órdenes**, crea una compra con la cabecera `Idempotency-Key` y el ID de un producto ficticio con stock 5. Pide dos unidades y comprueba el `201`, el `Location` y stock 3. Repite la misma cabecera y el mismo cuerpo. Debes ver la misma orden y stock 3. Conserva la cabecera, cambia la cantidad a una unidad y comprueba el `409` y que siga existiendo una sola orden para esa clave.
4. Si necesitas comprobar un rechazo por stock insuficiente, usa una clave nueva y pide más unidades que el stock disponible. Espera `409`. En Atlas usa únicamente productos temporales y realiza la prueba y la limpieza después de autorizar las escrituras.
5. Para probar concurrencia y rollback con MongoDB real, el replica set local debe escuchar en `127.0.0.1:27018` y llamarse `rsAutocareTest`. Ejecuta el caso optativo desde PowerShell.

```powershell
$env:AUTOCARE_ORDENES_MONGO_LOCAL = 'true'
.\mvnw.cmd '-Dtest=OrdenTransaccionesMongoTests' test
Remove-Item Env:AUTOCARE_ORDENES_MONGO_LOCAL
```

Esa prueba usa una base temporal distinta para cada ejecución. Prueba dos compras por la última unidad, dos solicitudes concurrentes con una misma clave y la reversión de stock cuando MongoDB rechaza el registro de la orden. La suite habitual usa mocks para verificar la lógica y el flujo transaccional. Habilita una prueba sobre una base de Atlas aislada únicamente después de autorizar la escritura y preparar allí colecciones y productos ficticios.
