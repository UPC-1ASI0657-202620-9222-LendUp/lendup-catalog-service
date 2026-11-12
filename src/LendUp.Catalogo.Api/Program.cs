using CloudinaryDotNet;
using FirebaseAdmin;
using FirebaseAdmin.Auth;
using Google.Apis.Auth.OAuth2;
using LendUp.Catalogo.Api;
using LendUp.Catalogo.Application;
using LendUp.Catalogo.Domain;
using LendUp.Catalogo.Infrastructure;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.OpenApi;
using MySql.Data.MySqlClient;
using System.Text.Json;
using System.Text.Json.Serialization;
var builder = WebApplication.CreateBuilder(args);
string Env(string name, string fallback = "") => builder.Configuration[name] ?? fallback;
bool Flag(string name, bool fallback = false) => bool.TryParse(Env(name), out var enabled) ? enabled : fallback;
builder.WebHost.UseUrls("http://0.0.0.0:" + Env("SERVER_PORT", "8082"));
builder.Logging.ClearProviders(); builder.Logging.AddJsonConsole();
var connection = new MySqlConnectionStringBuilder
{
    Server = Env("DB_HOST", "127.0.0.1"), Port = uint.Parse(Env("DB_PORT", "3308")),
    Database = Env("DB_NAME", "lendup_catalogo"), UserID = Env("DB_USERNAME", "lendup_catalogo"),
    Password = Env("DB_PASSWORD"), ConnectionTimeout = 3, DefaultCommandTimeout = 15, CharacterSet = "utf8mb4", OldGuids = true
};
builder.Services.AddDbContext<CatalogoDbContext>(o => o.UseMySQL(connection.ConnectionString));
builder.Services.AddScoped<ICatalogoRepository, CatalogoRepository>(); builder.Services.AddScoped<CatalogoService>();
var redis = Flag("REDIS_ENABLED");
builder.Services.AddSingleton(new CacheOptions(redis, TimeSpan.FromSeconds(Math.Clamp(int.Parse(Env("REDIS_TTL_SECONDS", "60")), 1, 3600))));
if (redis) builder.Services.AddStackExchangeRedisCache(o => o.Configuration = Env("REDIS_CONNECTION", "127.0.0.1:6379,abortConnect=false,connectTimeout=500,syncTimeout=500,asyncTimeout=500"));
else builder.Services.AddDistributedMemoryCache();
builder.Services.AddSingleton<ILecturaCache, ReadCache>();
builder.Services.AddSingleton<IImagenStorage>(_ => new CloudinaryStorage(Flag("CLOUDINARY_ENABLED") &&
    new[] { Env("CLOUDINARY_CLOUD_NAME"), Env("CLOUDINARY_API_KEY"), Env("CLOUDINARY_API_SECRET") }.All(x => !string.IsNullOrWhiteSpace(x))
    ? new Cloudinary(new Account(Env("CLOUDINARY_CLOUD_NAME"), Env("CLOUDINARY_API_KEY"), Env("CLOUDINARY_API_SECRET"))) : null));
builder.Services.AddSingleton<IFirebaseTokenVerifier>(_ =>
{
    FirebaseAuth? auth = null;
    if (Flag("CATALOGO_FIREBASE_ENABLED") && !string.IsNullOrWhiteSpace(Env("FIREBASE_PROJECT_ID")))
    {
        if (!builder.Environment.IsDevelopment() && !string.IsNullOrEmpty(Env("FIREBASE_AUTH_EMULATOR_HOST"))) throw new InvalidOperationException("Emulador Firebase no permitido en producción");
        try
        {
            var firebase = FirebaseApp.Create(new AppOptions { ProjectId = Env("FIREBASE_PROJECT_ID"), Credential = GoogleCredential.GetApplicationDefault() }, "catalogo-" + Guid.NewGuid());
            auth = FirebaseAuth.GetAuth(firebase);
        }
        catch { /* Protected operations fail closed while public reads remain available. */ }
    }
    return new FirebaseTokenVerifier(() => auth);
});
builder.Services.AddHttpClient<IIdentidadClient, IdentidadClient>(http =>
{
    try { http.BaseAddress = IdentidadClient.ValidarBase(Env("IDENTITY_BASE_URL", "http://localhost:8081"), builder.Environment.IsDevelopment() || builder.Environment.IsEnvironment("Testing")); }
    catch (InvalidOperationException) { throw CatalogoException.Dependencia(); }
    http.Timeout = TimeSpan.FromSeconds(4);
}).ConfigurePrimaryHttpMessageHandler(() => new SocketsHttpHandler { ConnectTimeout = TimeSpan.FromSeconds(3), AllowAutoRedirect = false });
builder.Services.AddAuthentication("Firebase")
    .AddScheme<AuthenticationSchemeOptions, FirebaseAuthentication>("Firebase", _ => { })
    .AddScheme<AuthenticationSchemeOptions, CatalogoInternalAuthentication>(CatalogoInternalAuthentication.SchemeName, _ => { });builder.Services.AddAuthentication("Firebase").AddScheme<AuthenticationSchemeOptions, FirebaseAuthentication>("Firebase", _ => { });
builder.Services.AddAuthorization(options => options.AddPolicy("ReservationsOnly", policy => policy
    .AddAuthenticationSchemes(CatalogoInternalAuthentication.SchemeName)
    .RequireAuthenticatedUser()
    .RequireClaim("service", "reservations")));builder.Services.AddAuthorization();
builder.Services.AddCors(o => o.AddDefaultPolicy(p => p.WithOrigins(Env("CORS_ORIGINS", "http://localhost:5173").Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)).AllowAnyHeader().AllowAnyMethod()));
builder.Services.AddScoped<InternalCatalogoController>();
builder.Services.AddControllers().AddJsonOptions(o =>
{
    o.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter(allowIntegerValues: false));
    o.JsonSerializerOptions.Converters.Add(new FechaLocalConverter());
});
builder.Services.Configure<ApiBehaviorOptions>(o => o.InvalidModelStateResponseFactory = ctx => new BadRequestObjectResult(new ApiError("VALIDACION", "Datos de entrada inválidos", DateTime.UtcNow, ctx.ModelState.ToDictionary(x => x.Key, _ => "Valor inválido"))));
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(o =>
{
    o.SwaggerDoc("v1", new OpenApiInfo { Title = "LendUp Catálogo", Version = "v1" });
    o.AddSecurityDefinition("firebaseBearer", new OpenApiSecurityScheme { Type = SecuritySchemeType.Http, Scheme = "bearer", BearerFormat = "Firebase ID token" });
    o.OperationFilter<CatalogoOpenApi>();
});
var app = builder.Build();
app.Use(async (context, next) =>
{
    try { await next(context); }
    catch (OperationCanceledException) when (context.RequestAborted.IsCancellationRequested) { context.Abort(); }
    catch (Exception ex)
    {
        var (status, codigo, mensaje) = ex switch
        {
            CatalogoException e => (e.Status, e.Codigo, e.Message),
            DisponibilidadSuperpuestaException => (409, "DISPONIBILIDAD_SUPERPUESTA", "El intervalo se superpone con otra disponibilidad"),
            ReglaDominioException => (400, "VALIDACION", ex.Message),
            KeyNotFoundException => (404, "NO_ENCONTRADO", "Recurso no encontrado"),
            JsonException or BadHttpRequestException or InvalidDataException => (400, "SOLICITUD_INVALIDA", "Solicitud inválida"),
            MySqlException or DbUpdateException => (503, "ALMACENAMIENTO_NO_DISPONIBLE", "Almacenamiento no disponible"),
            _ => (500, "ERROR_INTERNO", "Error interno")
        };
        if (status >= 500) app.Logger.LogError("Request failed ({ErrorType}); trace {TraceId}", ex.GetType().Name, context.TraceIdentifier);
        context.Response.StatusCode = status;
        await context.Response.WriteAsJsonAsync(new ApiError(codigo, mensaje, DateTime.UtcNow, new { }), context.RequestAborted);
    }
});
app.UseRouting(); app.UseCors(); app.UseAuthentication(); app.UseAuthorization(); app.MapControllers();
if (Flag("OPENAPI_ENABLED", true))
{
    app.UseSwagger(o => o.RouteTemplate = "v3/api-docs/{documentName}");
    app.UseSwaggerUI(o => { o.RoutePrefix = "swagger-ui"; o.SwaggerEndpoint("/v3/api-docs/v1", "Catálogo v1"); });
    app.MapGet("/v3/api-docs", () => Results.Redirect("/v3/api-docs/v1")).AllowAnonymous();
}
async Task<IResult> Health(CatalogoDbContext db, CancellationToken ct)
{
    try { return await db.Database.CanConnectAsync(ct) ? Results.Ok(new { status = "UP" }) : Results.Json(new { status = "DOWN" }, statusCode: 503); }
    catch { return Results.Json(new { status = "DOWN" }, statusCode: 503); }
}
app.MapGet("/actuator/health", Health).AllowAnonymous(); app.MapGet("/health", Health).AllowAnonymous();
app.Run();
public partial class Program;
