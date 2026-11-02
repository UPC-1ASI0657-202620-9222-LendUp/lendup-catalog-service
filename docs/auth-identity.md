# Paso 8 — Firebase Authentication e integración con Identidad

## Contrato revisado (2 de noviembre de 2026)

La revisión del repositorio público del backend original
`UPC-1ASI0657-202620-9222-LendUp/lendup-backend`
rama `main` mostró:

- `GET /api/v1/estudiantes/me` en
  `identidad/interfaces/rest/IdentidadController.java`.
- Respuesta de `IdentidadApplicationService.me`:
  `{"usuario": {...,"id":"UUID",...}, "perfil": {...}}`.
- `FlowSupport.user()` recupera `usuarios` por `firebase_uid` obtenido
  **del contexto de seguridad**, no por un ID enviado en la solicitud.
- El monolito ya utiliza Firebase Admin y envía el ID token Firebase en
  `Authorization: Bearer <token>`.
- Si no existe perfil para el Firebase UID, devuelve **403**.
- No se encontró un repositorio independiente de Identidad accesible con un
  contrato HTTP o gRPC específico para microservicios.

Por ello este paso usa el contrato **existente** `GET /api/v1/estudiantes/me`
con una URL base configurable. La creación de una API interna específica de
Identidad (mTLS, gRPC, etc.) necesita coordinación con ese equipo; no se
inventó ese endpoint. Si el servidor de Identidad reemplaza al monolito, debe
conservar el contrato o adaptar el puerto `IdentidadUsuarioPort`.

## Flujo de seguridad

```text
Cliente -> Authorization: Bearer <ID token Firebase>
  -> FirebaseCatalogoAuthenticationFilter
  -> FirebaseTokenVerifier / Firebase Admin SDK:
       verifica firma, proyecto, expiración y revocación
       exige claim email_verified == true
  -> IdentidadUsuarioPort / IdentidadHttpClient:
       GET {IDENTITY_BASE_URL}/api/v1/estudiantes/me
       Authorization: Bearer <mismo token Firebase>
       obtiene y valida usuario.id (UUID interno)
  -> UsuarioAutenticado(UUID) en SecurityContext
  -> UsuarioActual.exigirId()
  -> Caso de uso: comprueba que el UUID interno es propietario
```

**Nunca** se utiliza el Firebase UID como UUID interno ni se confía en
`X-User-Id`, `propietarioUsuarioId` u otros parámetros de usuario enviados
por el navegador. Catálogo no lee la BD de Identidad.

Las consultas públicas siguen accesibles sin login:
`GET /categorias`, `GET /categorias/{id}`,
`GET /publicaciones`, `GET /publicaciones/{id}`.
Las demás requieren autenticación completa.

## Configuración segura / activación

De fábrica, `CATALOGO_FIREBASE_ENABLED=false` para seguir ejecutando las
consultas públicas y las pruebas locales sin credenciales o sin Identidad.
Cuando está deshabilitado, la ruta protegida **no se puede utilizar**
con un Bearer real; cualquier token provoca 503 y sin token recibe 401.
No existe autenticación falsa en desarrollo ni contraseñas precargadas.

Para **activar** la autenticación hay que preparar:

| Variable de entorno | Función |
|---|---|
| `CATALOGO_FIREBASE_ENABLED=true` | Instancia y activa los adaptadores Firebase/Identidad |
| `FIREBASE_PROJECT_ID` | ID exacto del proyecto Firebase |
| `IDENTITY_BASE_URL` | Dirección de Identidad sin `/api/v1`, por ejemplo `http://localhost:8080` en la misma máquina |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta local fuera del repositorio a una cuenta de servicio válida, o usar Google Application Default Credentials |
| `FIREBASE_AUTH_EMULATOR_HOST` | **Opcional**: host:puerto del emulador de Authentication en desarrollo, como `127.0.0.1:9099` |
| `CORS_ORIGINS` | Orígenes exactos separados por comas, por defecto `http://localhost:5173` |

Nunca subir archivos de service account a GitHub.
Cuando Firebase esté habilitado, la aplicación **falla al iniciar**
si `FIREBASE_PROJECT_ID`, `IDENTITY_BASE_URL` o las credenciales requeridas
no están disponibles.

En redes remotas se exige HTTPS para `IDENTITY_BASE_URL`;
HTTP se permite solo con los hosts `localhost` o `127.0.0.1`
para pruebas en la misma máquina. Las llamadas al servicio de Identidad
utilizan límites de conexión (3 s) y lectura (4 s). 
Para un contenedor Docker donde `localhost` no apunte a Identidad,
usar una URL HTTPS alcanzable y autorizada en la red del despliegue.

**Importante:** el emulador, la aplicación de Identidad y su base de datos
se configuran aparte; este paso no los instala ni copia sus credenciales.
No habilites el filtro hasta tenerlos preparados.

## Semántica de errores

- **401**: token sin formato válido, expirado, rechazado o revocado.
- **403**: email no verificado o estudiante no registrado en Identidad.
- **503**: adaptadores no configurados para el Bearer solicitado,
  Identidad no disponible, error de contrato o comunicación.
- **403 de dominio**: usuario interno válido que intenta modificar una
  publicación de otro propietario.
- **200, 201, 204**: operaciones correctas; reglas DDD y persistencia sin cambios.

Las consultas públicas omiten la validación Bearer; las rutas de escritura
son *fail-closed*. Los OPTIONS de preflight CORS pueden ejecutarse
sin sesión sólo desde orígenes autorizados.

## Tests agregados

1. `FirebaseCatalogoAuthenticationFilterTest`:
   firma/claims mediante puerto simulado, contexto interno correcto,
   cabeceras falsas, 401/403/503, seguridad de lecturas públicas.
2. `FirebaseAdminTokenVerifierTest`:
   llamada real al método `verifyIdToken(idToken,true)` del SDK,
   claims y valores ausentes, con FirebaseAuth mockeado.
3. `IdentidadHttpClientTest`:
   servidor HTTP local controlado que comprueba ruta, Bearer
   reenviado, parseo de `usuario.id`, 401/403/503 y URL insegura.
4. `CatalogoRestControllerTest`:
   dos pruebas HTTP de autenticación Bearer y una de preflight CORS.

**Total esperado:** 82 pruebas (61 previas y 21 nuevas).
La ejecución local del nuevo paso todavía está pendiente.

## Windows / VS Code

En la misma terminal donde ya funcionaban las pruebas de Java 21:

```powershell
git switch develop
git pull origin develop
docker compose ps
```

En una terminal nueva, antes de ejecutar las pruebas, configura Java 21,
Maven y la contraseña `DB_PASSWORD` según
[docs/rest-api.md](rest-api.md#pruebas-automatizadas). No hagas público el valor
de la contraseña.

El comando de Maven para tu equipo, que evita los fallos de `.cmd`, es:

```powershell
& "$env:JAVA_HOME\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=$((Get-Location).Path)" "-Dmaven.home=$mavenHome" "-Dclassworlds.conf=$mavenHome\bin\m2.conf" -cp "$mavenHome\boot\*" org.codehaus.plexus.classworlds.launcher.Launcher test
```

Resultado esperado: `Tests run: 82, Failures: 0, Errors: 0`
y `BUILD SUCCESS`. Si algo falla, comparte el bloque `[ERROR]`
con el nombre de la prueba, **sin tokens ni credenciales**.
