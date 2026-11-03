# Autenticación Firebase e integración con Identidad

## Contrato HTTP de Identidad

El microservicio de Identidad proporciona `GET /api/v1/estudiantes/me` y devuelve una estructura compatible con `{ "usuario": { "id": "UUID", ... }, "perfil": { ... } }`. El identificador se resuelve a partir de un token Firebase verificado, nunca de un ID suministrado por el cliente.

Catálogo consulta este endpoint mediante `IdentidadHttpClient` y una URL base configurable (`IDENTITY_BASE_URL`). No comparte tablas con Identidad ni utiliza el Firebase UID como identificador interno del propietario. El contrato también conserva compatibilidad con la API de estudiantes del backend original.

La integración HTTP autenticada entre ambos microservicios ha sido comprobada en un entorno local. En despliegues separados deben configurarse las credenciales, la red y el transporte seguro correspondientes.

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

De forma predeterminada, `CATALOGO_FIREBASE_ENABLED=false` permite ejecutar las
consultas públicas y las pruebas locales sin credenciales o sin Identidad.
Cuando está deshabilitado, la ruta protegida **no se puede utilizar**
con un Bearer real; cualquier token provoca 503 y sin token recibe 401.
No existe autenticación falsa en desarrollo ni contraseñas precargadas.

Para **activar** la autenticación hay que preparar:

| Variable de entorno | Función |
|---|---|
| `CATALOGO_FIREBASE_ENABLED=true` | Instancia y activa los adaptadores Firebase/Identidad |
| `FIREBASE_PROJECT_ID` | ID exacto del proyecto Firebase |
| `IDENTITY_BASE_URL` | Dirección base de Identidad, sin `/api/v1`; por ejemplo `http://localhost:8081` en desarrollo local |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta local fuera del repositorio a una cuenta de servicio válida, o usar Google Application Default Credentials |
| `FIREBASE_AUTH_EMULATOR_HOST` | **Opcional**: host:puerto del emulador de Authentication en desarrollo, como `127.0.0.1:9099` |
| `CORS_ORIGINS` | Orígenes exactos separados por comas, por defecto `http://localhost:5173` |

Nunca subir archivos de service account a GitHub.
Cuando Firebase esté habilitado, la aplicación **falla al iniciar**
si `FIREBASE_PROJECT_ID`, `IDENTITY_BASE_URL` o las credenciales requeridas
no están disponibles.

En redes remotas se exige HTTPS para `IDENTITY_BASE_URL`;
HTTP se permite solo con los hosts `localhost` o `127.0.0.1`
para pruebas locales. Las llamadas al servicio de Identidad
utilizan límites de conexión (3 s) y lectura (4 s). 
Para un contenedor Docker donde `localhost` no apunte a Identidad,
usar una URL HTTPS alcanzable y autorizada en la red del despliegue.

**Dependencias externas:** el emulador, la aplicación de Identidad y su base de datos
se configuran de manera independiente. La integración de Catálogo no instala
estos componentes ni incluye sus credenciales. La autenticación debe habilitarse
solo cuando dichas dependencias estén disponibles.

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

## Pruebas de seguridad e integración

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

La suite automatizada de referencia consta de **97 pruebas aprobadas**. La comunicación HTTP real con Identidad se ha comprobado adicionalmente en un entorno local.

## Ejecución local (Windows PowerShell)

Desde una terminal con Java 21 y Maven configurados:

```powershell
git switch develop
git pull origin develop
docker compose ps
```

Para ejecutar pruebas desde una terminal nueva, establecer Java 21, Maven
y la variable `DB_PASSWORD` según
[docs/rest-api.md](rest-api.md#pruebas-automatizadas). Las contraseñas no
se registran en documentación ni en commits.

Comando alternativo en instalaciones donde los scripts `.cmd` de Maven fallen:

```powershell
& "$env:JAVA_HOME\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=$((Get-Location).Path)" "-Dmaven.home=$mavenHome" "-Dclassworlds.conf=$mavenHome\bin\m2.conf" -cp "$mavenHome\boot\*" org.codehaus.plexus.classworlds.launcher.Launcher test
```

Para diagnosticar errores, consultar los bloques `[ERROR]` y los informes
Surefire, sin copiar tokens ni credenciales.
