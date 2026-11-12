using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using LendUp.Catalogo.Application;
using LendUp.Catalogo.Domain;
using LendUp.Catalogo.Infrastructure;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Caching.Distributed;
using Microsoft.Extensions.Logging.Abstractions;
using MySql.Data.MySqlClient;
using Testcontainers.MySql;
using Testcontainers.Redis;
using Xunit;
namespace LendUp.Catalogo.Integration.Tests;

public sealed class Fixture : IAsyncLifetime
{
    public readonly MySqlContainer Mysql = new MySqlBuilder().WithImage("mysql:8.4").WithDatabase("catalogo_test").Build();
    public readonly RedisContainer Redis = new RedisBuilder().WithImage("redis:7.4-alpine").Build();
    public readonly Guid Actor = Guid.NewGuid();
    public readonly Guid Categoria = Guid.NewGuid();
    public WebApplicationFactory<Program> Factory { get; private set; } = null!;
    public HttpClient Client { get; private set; } = null!;
    public async Task InitializeAsync()
    {
        await Task.WhenAll(Mysql.StartAsync(),Redis.StartAsync());
        await using var conn = new MySqlConnection(Mysql.GetConnectionString()); await conn.OpenAsync();
        await new MySqlCommand(await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory,"V1.sql")),conn).ExecuteNonQueryAsync();
        await new MySqlCommand(await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory,"V2.sql")),conn).ExecuteNonQueryAsync();
        await new MySqlCommand(await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory,"V3.sql")),conn).ExecuteNonQueryAsync();
        var categoriesCount = Convert.ToInt32(await new MySqlCommand("SELECT COUNT(*) FROM categorias", conn).ExecuteScalarAsync());
        Assert.Equal(8, categoriesCount); // A fresh installation must have initial categories before any manual test seed.
        var insert = new MySqlCommand("INSERT INTO categorias(id,codigo,nombre,activa) VALUES(@id,'TEC','Tecnología',true)",conn);
        insert.Parameters.AddWithValue("@id",Categoria.ToString()); await insert.ExecuteNonQueryAsync();
        // A Java-compatible preexisting row, inserted via raw SQL rather than EF.
        var seed = new MySqlCommand("""
            INSERT INTO publicaciones(id,propietario_usuario_id,categoria_id,titulo,descripcion,condicion_objeto,universidad,campus,ubicacion,lugar_intercambio,tarifa_diaria,moneda,condiciones_uso,condiciones_entrega,condiciones_devolucion,condiciones_cancelacion)
            VALUES('11111111-1111-4111-8111-111111111111',@actor,@cat,'Java fixture','Descripción','Bueno','UPC','Monterrico','Campus','Campus',12.34,'PEN','Uso','Entrega','Devolución','Cancelación');
            INSERT INTO disponibilidades_publicacion(id,publicacion_id,desde,hasta) VALUES('22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111','2026-12-12 09:00:00','2026-12-12 12:00:00');
            """,conn);
        seed.Parameters.AddWithValue("@actor",Actor.ToString()); seed.Parameters.AddWithValue("@cat",Categoria.ToString()); await seed.ExecuteNonQueryAsync();
        Factory = new WebApplicationFactory<Program>().WithWebHostBuilder(b =>
        {
            b.UseEnvironment("Testing");
            var cs = new MySqlConnectionStringBuilder(Mysql.GetConnectionString());
            b.ConfigureAppConfiguration((_,c) => c.AddInMemoryCollection(new Dictionary<string,string?>
            {
                ["DB_HOST"] = cs.Server, ["DB_PORT"] = cs.Port.ToString(), ["DB_NAME"] = cs.Database,
                ["DB_USERNAME"] = cs.UserID,["DB_PASSWORD"] = cs.Password,["REDIS_ENABLED"] = "true",
                ["REDIS_CONNECTION"] = Redis.GetConnectionString()+",abortConnect=false",["REDIS_TTL_SECONDS"] = "2",
                ["CATALOG_RESERVATIONS_SERVICE_TOKEN"] = new string('R', 48)["REDIS_CONNECTION"] = Redis.GetConnectionString()+",abortConnect=false",["REDIS_TTL_SECONDS"] = "2"
            }));
            b.ConfigureServices(s =>
            {
                s.RemoveAll<CatalogoDbContext>(); s.RemoveAll<DbContextOptions<CatalogoDbContext>>();
                s.AddDbContext<CatalogoDbContext>(o => o.UseMySQL(new MySqlConnectionStringBuilder(Mysql.GetConnectionString()) { OldGuids=true }.ConnectionString));
                s.Configure<Microsoft.Extensions.Caching.StackExchangeRedis.RedisCacheOptions>(o => o.Configuration = Redis.GetConnectionString()+",abortConnect=false");
                s.RemoveAll<CacheOptions>(); s.AddSingleton(new CacheOptions(true,TimeSpan.FromSeconds(2)));
                s.RemoveAll<IFirebaseTokenVerifier>(); s.AddSingleton<IFirebaseTokenVerifier>(new FakeFirebase());
                s.RemoveAll<IIdentidadClient>(); s.AddSingleton<IIdentidadClient>(new FakeIdentidad(Actor));
                s.RemoveAll<IImagenStorage>(); s.AddSingleton<IImagenStorage>(new FakeStorage());
            });
        });
        Client = Factory.CreateClient(); Client.DefaultRequestHeaders.Authorization = new("Bearer","owner");
        using var scope = Factory.Services.CreateScope();
        await scope.ServiceProvider.GetRequiredService<CatalogoDbContext>().Categorias.CountAsync();
    }
    public async Task DisposeAsync()
    {
        Client?.Dispose(); if (Factory is not null) await Factory.DisposeAsync();
        await Task.WhenAll(Mysql.DisposeAsync().AsTask(),Redis.DisposeAsync().AsTask());
    }
    private sealed class FakeFirebase : IFirebaseTokenVerifier
    {
        public Task<TokenVerificado> Verificar(string token,CancellationToken ct) => token switch
        {
            "invalid" => throw new CatalogoException(401,"NO_AUTORIZADO","Token inválido"),
            "dependency" => throw CatalogoException.Dependencia(),
            _ => Task.FromResult(new TokenVerificado("verified-firebase-uid",token != "unverified"))
        };
    }
    private sealed class FakeIdentidad(Guid actor) : IIdentidadClient
    {
        public Task<Guid> Resolver(string token,CancellationToken ct) => Task.FromResult(token == "other" ? Guid.Parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa") : actor);
    }
    private sealed class FakeStorage : IImagenStorage
    {
        public Task<ImagenAlmacenada> Subir(Guid id,byte[] bytes,CancellationToken ct) => Task.FromResult(new ImagenAlmacenada($"lendup/publicaciones/{id}/{Guid.NewGuid()}","https://example.test/image.jpg"));
        public Task Eliminar(string id,CancellationToken ct) => Task.CompletedTask;
    }
}

public partial class CatalogoIntegrationTests(Fixture f, Xunit.Abstractions.ITestOutputHelper output) : IClassFixture<Fixture>
{
    private const string Base = "/api/v1/catalogo";
    private object Solicitud(string titulo = "Cámara") => new
    {
        detalles = new { categoriaId = f.Categoria,titulo,descripcion="Descripción",condicionObjeto="Bueno",universidad="UPC",campus="Monterrico",ubicacion="Campus",latitud=(decimal?)-12.1m,longitud=(decimal?)-77.1m },
        condiciones = new { tarifaDiaria=25m,garantiaMonetaria=(decimal?)100m,moneda="PEN",lugarIntercambio="Campus",condicionesUso="Uso",condicionesEntrega="Entrega",condicionesDevolucion="Devolución",condicionesCancelacion="Avisar" }
    };
    private async Task<string> Crear(string titulo = "Cámara")
    {
        var r = await f.Client.PostAsJsonAsync(Base+"/publicaciones",Solicitud(titulo));
        Assert.Equal(HttpStatusCode.Created,r.StatusCode);
        Assert.NotNull(r.Headers.Location); var j = await r.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal(f.Actor.ToString(),j.GetProperty("propietarioUsuarioId").GetString());
        return r.Headers.Location!.ToString();
    }
    private async Task<JsonElement> Json(string url)
    {
        var r = await f.Client.GetAsync(url); Assert.Equal(HttpStatusCode.OK,r.StatusCode);
        return await r.Content.ReadFromJsonAsync<JsonElement>();
    }
    [Fact] public async Task FlujoDeLos16Endpoints()
    {
        Assert.NotEmpty((await Json(Base+"/categorias")).EnumerateArray());
        Assert.Equal(f.Categoria.ToString(),(await Json(Base+"/categorias/"+f.Categoria)).GetProperty("id").GetString());
        var url = await Crear(); var id = url.Split('/').Last();
        Assert.Equal("ACTIVA",(await Json(url)).GetProperty("estado").GetString());
        Assert.NotEmpty((await Json(Base+"/publicaciones")).EnumerateArray());
        Assert.NotEmpty((await Json(Base+"/mis-publicaciones")).EnumerateArray());
        Assert.Equal(HttpStatusCode.OK,(await f.Client.PutAsJsonAsync(url,Solicitud("Nueva cámara"))).StatusCode);
        var patch = await f.Client.PatchAsJsonAsync(url,new { detalles = new { titulo="Título parcial",latitud=(decimal?)null },condiciones = new { garantiaMonetaria=(decimal?)null } });
        Assert.Equal(HttpStatusCode.OK,patch.StatusCode); var body = await patch.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal(JsonValueKind.Null,body.GetProperty("condiciones").GetProperty("garantiaMonetaria").ValueKind);
        Assert.Equal("UPC",body.GetProperty("detalles").GetProperty("universidad").GetString());
        var period = new { desde="2026-12-12T09:00:00",hasta="2026-12-12T12:00:00" };
        var slot = await f.Client.PostAsJsonAsync(url+"/disponibilidades",period); Assert.Equal(HttpStatusCode.Created,slot.StatusCode);
        var slotUrl = slot.Headers.Location!.ToString();
        Assert.Equal("2026-12-12T09:00:00",(await slot.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("desde").GetString());
        Assert.Equal(HttpStatusCode.OK,(await f.Client.PutAsJsonAsync(slotUrl,period)).StatusCode);
        var overlap = await f.Client.PostAsJsonAsync(url+"/disponibilidades",period); Assert.Equal(HttpStatusCode.Conflict,overlap.StatusCode);
        Assert.Equal("DISPONIBILIDAD_SUPERPUESTA",(await overlap.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("codigo").GetString());
        Assert.Single((await Json(url+"/disponibilidades")).EnumerateArray());
        using var form = new MultipartFormDataContent(); var image = new ByteArrayContent([255,216,255]); image.Headers.ContentType = new("image/jpeg"); form.Add(image,"file","image.jpg");
        var upload = await f.Client.PostAsync(url+"/imagenes",form); Assert.Equal(HttpStatusCode.Created,upload.StatusCode);
        Assert.Single((await Json(url+"/imagenes")).EnumerateArray());
        var delImage = await f.Client.DeleteAsync(upload.Headers.Location); Assert.Equal(HttpStatusCode.NoContent,delImage.StatusCode); Assert.Empty(await delImage.Content.ReadAsByteArrayAsync());
        Assert.Empty((await Json(url+"/imagenes")).EnumerateArray());
        var pause = await f.Client.PatchAsJsonAsync(url+"/estado",new { estado="PAUSADA" }); Assert.Equal(HttpStatusCode.OK,pause.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,(await f.Client.GetAsync(url)).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,(await f.Client.GetAsync(url+"/imagenes")).StatusCode);
        Assert.DoesNotContain((await Json(Base+"/publicaciones")).EnumerateArray(),j => j.GetProperty("id").GetString()==id);
        Assert.Equal(HttpStatusCode.OK,(await f.Client.PatchAsJsonAsync(url+"/estado",new { estado="ACTIVA" })).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent,(await f.Client.DeleteAsync(slotUrl)).StatusCode);
        Assert.Empty((await Json(url+"/disponibilidades")).EnumerateArray());
    }
    [Fact]
    public async Task SnapshotCompatibleConReservasYRegistroInmutable()
    {
        var publicationUrl = await Crear("Snapshot Reservas");
        var id = publicationUrl.Split('/').Last();
        var period = new { desde = "2026-12-16T09:00:00", hasta = "2026-12-16T12:00:00" };
        var slot = await f.Client.PostAsJsonAsync(publicationUrl + "/disponibilidades", period);
        Assert.Equal(HttpStatusCode.Created, slot.StatusCode);
        var query = "?desde=" + period.desde + "&hasta=" + period.hasta;
        using var publicClient = f.Factory.CreateClient();
        var firstResponse = await publicClient.GetAsync(publicationUrl + "/snapshot" + query);
        Assert.Equal(HttpStatusCode.OK, firstResponse.StatusCode);
        var first = await firstResponse.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal("ACTIVA", first.GetProperty("estado").GetString());
        Assert.True(first.GetProperty("periodo_ofrecido").GetBoolean());
        Assert.Equal(f.Actor.ToString(), first.GetProperty("propietario_usuario_id").GetString());
        Assert.Equal(64, first.GetProperty("snapshot_hash").GetString()!.Length);
        using var internalClient = f.Factory.CreateClient();
        internalClient.DefaultRequestHeaders.Authorization = new("Bearer", new string('R', 48));
        var internalUrl = $"/internal/v1/catalogo/publicaciones/{id}/snapshot" + query;
        var privateResponse = await internalClient.GetAsync(internalUrl);
        Assert.Equal(HttpStatusCode.OK, privateResponse.StatusCode);
        var current = await privateResponse.Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal(first.GetProperty("snapshot_hash").GetString(), current.GetProperty("snapshot_hash").GetString());
        Assert.Equal(first.GetProperty("snapshot_id").GetString(), current.GetProperty("snapshot_id").GetString());
        Assert.Equal(25m, current.GetProperty("tarifa_diaria").GetDecimal());
        using (var scope = f.Factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<CatalogoDbContext>();
            Assert.Equal(1, await db.Snapshots.CountAsync(x => x.PublicacionId == id));
        }
        var changed = await f.Client.PatchAsJsonAsync(publicationUrl, new { condiciones = new { tarifaDiaria = 26m } });
        Assert.Equal(HttpStatusCode.OK, changed.StatusCode);
        var next = await (await publicClient.GetAsync(publicationUrl + "/snapshot" + query)).Content.ReadFromJsonAsync<JsonElement>();
        Assert.NotEqual(first.GetProperty("snapshot_hash").GetString(), next.GetProperty("snapshot_hash").GetString());
        Assert.Equal(26m, next.GetProperty("tarifa_diaria").GetDecimal());
        using (var scope = f.Factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<CatalogoDbContext>();
            Assert.Equal(2, await db.Snapshots.CountAsync(x => x.PublicacionId == id));
            var oldId = first.GetProperty("snapshot_id").GetString();
            var historic = await db.Snapshots.AsNoTracking().SingleAsync(x => x.Id == oldId);
            Assert.Contains("\"tarifa_diaria\":25", historic.SnapshotJson);
        }
        // Java LocalDateTime.toString() can omit :ss when seconds are zero.
        Assert.Equal(HttpStatusCode.OK, (await internalClient.GetAsync(
            $"/internal/v1/catalogo/publicaciones/{id}/snapshot?desde=2026-12-16T09:00&hasta=2026-12-16T12:00")).StatusCode);
    }

    [Fact]
    public async Task SnapshotInternoFallaCerradoYPeriodoNoOfrecidoSeRechaza()
    {
        var publicationUrl = await Crear("Snapshot protegido");
        var id = publicationUrl.Split('/').Last();
        var query = "?desde=2026-12-17T09:00:00&hasta=2026-12-17T10:00:00";
        var internalUrl = $"/internal/v1/catalogo/publicaciones/{id}/snapshot" + query;
        using var unauthenticated = f.Factory.CreateClient();
        Assert.Equal(HttpStatusCode.Unauthorized, (await unauthenticated.GetAsync(internalUrl)).StatusCode);
        unauthenticated.DefaultRequestHeaders.Authorization = new("Bearer", "owner");
        Assert.Equal(HttpStatusCode.Unauthorized, (await unauthenticated.GetAsync(internalUrl)).StatusCode);
        unauthenticated.DefaultRequestHeaders.Authorization = new("Bearer", new string('W', 48));
        Assert.Equal(HttpStatusCode.Unauthorized, (await unauthenticated.GetAsync(internalUrl)).StatusCode);
        using var reservations = f.Factory.CreateClient();
        reservations.DefaultRequestHeaders.Authorization = new("Bearer", new string('R', 48));
        var snapshot = await (await reservations.GetAsync(internalUrl)).Content.ReadFromJsonAsync<JsonElement>();
        Assert.False(snapshot.GetProperty("periodo_ofrecido").GetBoolean());
        Assert.Equal(HttpStatusCode.BadRequest, (await reservations.GetAsync(
            $"/internal/v1/catalogo/publicaciones/{id}/snapshot?desde=2026-12-17T09:00:00Z&hasta=2026-12-17T10:00:00")).StatusCode);
        var paused = await f.Client.PatchAsJsonAsync(publicationUrl + "/estado", new { estado = "PAUSADA" });
        Assert.Equal(HttpStatusCode.OK, paused.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await unauthenticated.GetAsync(publicationUrl + "/snapshot" + query)).StatusCode);
        var inactiveSnapshot = await (await reservations.GetAsync(internalUrl)).Content.ReadFromJsonAsync<JsonElement>();
        Assert.Equal("PAUSADA", inactiveSnapshot.GetProperty("estado").GetString());
    }

    [Fact] public async Task DatosV1YFiltrosSQL()
    {
        using var scope = f.Factory.Services.CreateScope();
        var repo = scope.ServiceProvider.GetRequiredService<ICatalogoRepository>();
        Assert.NotNull(await repo.Obtener(Guid.Parse("11111111-1111-4111-8111-111111111111"),default));
        var p = await Json(Base+"/publicaciones/11111111-1111-4111-8111-111111111111");
        Assert.Equal(12.34m,p.GetProperty("condiciones").GetProperty("tarifaDiaria").GetDecimal());
        Assert.Single(p.GetProperty("disponibilidades").EnumerateArray());
        var rows = await Json(Base+$"/publicaciones?nombre=Java&categoriaId={f.Categoria}&campus=Monterrico&universidad=UPC&desde=2026-12-12T10:00:00&hasta=2026-12-12T11:00:00");
        Assert.Single(rows.EnumerateArray());
        Assert.Empty((await Json(Base+"/publicaciones?nombre=Java&desde=2026-12-12T11:00:00&hasta=2026-12-12T13:00:00")).EnumerateArray());
        Assert.Empty((await Json(Base+"/publicaciones?campus=Otro")).EnumerateArray());
        Assert.Equal(HttpStatusCode.BadRequest,(await f.Client.GetAsync(Base+"/publicaciones?desde=2026-12-12T10:00:00")).StatusCode);
    }
    [Fact] public async Task ConcurrenciaSoloConfirmaUnIntervaloYConservaPatch()
    {
        var url = await Crear("Concurrente"); var period = new { desde="2026-12-14T09:00:00",hasta="2026-12-14T12:00:00" };
        var results = await Task.WhenAll(Enumerable.Range(0,4).Select(_ => f.Client.PostAsJsonAsync(url+"/disponibilidades",period)));
        Assert.Single(results,r => r.StatusCode==HttpStatusCode.Created);
        Assert.Equal(3,results.Count(r => r.StatusCode==HttpStatusCode.Conflict));
        Assert.Single((await Json(url+"/disponibilidades")).EnumerateArray());
        var patches = await Task.WhenAll(f.Client.PatchAsJsonAsync(url,new { detalles = new { titulo="Concurrencia" } }),f.Client.PatchAsJsonAsync(url,new { detalles = new { campus="San Miguel" } }));
        Assert.All(patches,r => Assert.Equal(HttpStatusCode.OK,r.StatusCode));
        var p = await Json(url); Assert.Equal("Concurrencia",p.GetProperty("detalles").GetProperty("titulo").GetString());
        Assert.Equal("San Miguel",p.GetProperty("detalles").GetProperty("campus").GetString());
    }
    [Theory,InlineData(null,401),InlineData("invalid",401),InlineData("unverified",403),InlineData("dependency",503),InlineData("other",403)]
    public async Task SeguridadNoModificaDatos(string? token,int status)
    {
        var url = await Crear("Protegida"); using var client = f.Factory.CreateClient();
        if (token is not null) client.DefaultRequestHeaders.Authorization = new("Bearer",token);
        client.DefaultRequestHeaders.Add("X-User-Id",f.Actor.ToString());
        var result = await client.PatchAsJsonAsync(url,new { detalles = new { titulo="Intrusión" } });
        Assert.Equal(status,(int)result.StatusCode);
        Assert.Equal("Protegida",(await Json(url)).GetProperty("detalles").GetProperty("titulo").GetString());
        Assert.Equal(HttpStatusCode.OK,(await client.GetAsync(url)).StatusCode);
    }
    [Theory,InlineData("{\"detalles\":{\"titulo\":null}}"),InlineData("{\"propietarioUsuarioId\":null}"),InlineData("{\"estado\":\"PAUSADA\"}"),InlineData("{\"detalles\":{\"desconocido\":true}}")]
    public async Task PatchInvalido400(string patch)
    {
        var url = await Crear(); var response = await f.Client.PatchAsync(url,new StringContent(patch,Encoding.UTF8,"application/json"));
        Assert.Equal(HttpStatusCode.BadRequest,response.StatusCode);
        var error = await response.Content.ReadFromJsonAsync<JsonElement>(); Assert.True(error.TryGetProperty("campos",out _));
    }
    [Fact] public async Task SwaggerCorsYSalud()
    {
        Assert.Equal(HttpStatusCode.OK,(await f.Client.GetAsync("/swagger-ui/index.html")).StatusCode);
        Assert.Equal(HttpStatusCode.OK,(await f.Client.GetAsync("/v3/api-docs")).StatusCode);
        Assert.Equal(HttpStatusCode.OK,(await f.Client.GetAsync("/actuator/health")).StatusCode);
        using var req = new HttpRequestMessage(HttpMethod.Options,Base+"/publicaciones");
        req.Headers.Add("Origin","http://localhost:5173"); req.Headers.Add("Access-Control-Request-Method","POST");
        var r = await f.Client.SendAsync(req); Assert.Contains("http://localhost:5173",r.Headers.GetValues("Access-Control-Allow-Origin"));
        using var evil = new HttpRequestMessage(HttpMethod.Options,Base+"/publicaciones");
        evil.Headers.Add("Origin","https://evil.test"); evil.Headers.Add("Access-Control-Request-Method","POST");
        Assert.False((await f.Client.SendAsync(evil)).Headers.Contains("Access-Control-Allow-Origin"));
    }
    [Fact] public async Task RedisHitExpiracionYCaida()
    {
        using var scope = f.Factory.Services.CreateScope(); var distributed = scope.ServiceProvider.GetRequiredService<IDistributedCache>();
        var cache = new ReadCache(distributed,new(true,TimeSpan.FromMilliseconds(100)),NullLogger<ReadCache>.Instance);
        var count = 0; Task<int> Query(CancellationToken _) => Task.FromResult(++count);
        var key = Guid.NewGuid().ToString(); Assert.Equal(1,await cache.Obtener(key,Query,default)); Assert.Equal(1,await cache.Obtener(key,Query,default));
        await Task.Delay(200); Assert.Equal(2,await cache.Obtener(key,Query,default)); Assert.Equal(3,await cache.Obtener(key+"filter",Query,default));
        var concurrentCalls=0;
        async Task<int> SlowQuery(CancellationToken ct) { Interlocked.Increment(ref concurrentCalls); await Task.Delay(20,ct); return 42; }
        var concurrentKey=Guid.NewGuid().ToString();
        Assert.All(await Task.WhenAll(Enumerable.Range(0,10).Select(_=>cache.Obtener(concurrentKey,SlowQuery,default))),v=>Assert.Equal(42,v));
        Assert.Equal(1,concurrentCalls);
        // A separate unreachable Redis instance verifies fail-open without disrupting other tests.
        var bad = new Microsoft.Extensions.Caching.StackExchangeRedis.RedisCache(Microsoft.Extensions.Options.Options.Create(new Microsoft.Extensions.Caching.StackExchangeRedis.RedisCacheOptions { Configuration="127.0.0.1:1,abortConnect=false,connectTimeout=100,syncTimeout=100,asyncTimeout=100,connectRetry=0" }));
        using (bad)
        {
            var fallback = new ReadCache(bad,new(true,TimeSpan.FromSeconds(1)),NullLogger<ReadCache>.Instance);
            Assert.Equal(4,await fallback.Obtener("down",Query,default));
        }
    }
    [Fact] public async Task ImagenesValidanLimiteOrdenYFormatoHttp()
    {
        var url=await Crear("Imágenes");
        async Task<HttpResponseMessage> Upload(byte[] bytes,string mime)
        {
            using var form=new MultipartFormDataContent(); var content=new ByteArrayContent(bytes); content.Headers.ContentType=new(mime); form.Add(content,"file","photo.jpg");
            return await f.Client.PostAsync(url+"/imagenes",form);
        }
        Assert.Equal(HttpStatusCode.BadRequest,(await Upload([255,216,255],"image/png")).StatusCode);
        var urls=new List<Uri>();
        for(var n=0;n<8;n++)
        {
            var r=await Upload([255,216,255],"image/jpeg"); Assert.Equal(HttpStatusCode.Created,r.StatusCode); urls.Add(r.Headers.Location!);
            Assert.Equal(n,(await r.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("orden").GetInt32());
        }
        Assert.Equal(HttpStatusCode.BadRequest,(await Upload([255,216,255],"image/jpeg")).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent,(await f.Client.DeleteAsync(urls[3])).StatusCode);
        var reused=await Upload([255,216,255],"image/jpeg"); Assert.Equal(HttpStatusCode.Created,reused.StatusCode);
        Assert.Equal(3,(await reused.Content.ReadFromJsonAsync<JsonElement>()).GetProperty("orden").GetInt32());
    }
    [Fact] public async Task RestriccionesV1SeMantienen()
    {
        await using var conn=new MySqlConnection(f.Mysql.GetConnectionString()); await conn.OpenAsync();
        await Assert.ThrowsAsync<MySqlException>(()=>new MySqlCommand("INSERT INTO categorias(id,codigo,nombre) VALUES(UUID(),'TEC','Duplicada')",conn).ExecuteNonQueryAsync());
        await Assert.ThrowsAsync<MySqlException>(()=>new MySqlCommand("INSERT INTO disponibilidades_publicacion(id,publicacion_id,desde,hasta) VALUES(UUID(),'ffffffff-ffff-4fff-8fff-ffffffffffff','2026-12-12','2026-12-13')",conn).ExecuteNonQueryAsync());
        var url=await Crear("Restricciones"); var id=url.Split('/').Last();
        var cmd=new MySqlCommand("INSERT INTO imagenes_publicacion(id,publicacion_id,cloudinary_public_id,url,orden) VALUES(UUID(),@id,'fake','https://example.test/image',0)",conn); cmd.Parameters.AddWithValue("@id",id);
        await cmd.ExecuteNonQueryAsync(); await Assert.ThrowsAsync<MySqlException>(()=>cmd.ExecuteNonQueryAsync());
    }
    [Fact] public async Task CategoriasInactivasOcultanCacheYFKs()
    {
        var url = await Crear("Inactiva"); await Json(url);
        using var scope = f.Factory.Services.CreateScope(); var db = scope.ServiceProvider.GetRequiredService<CatalogoDbContext>();
        var categoria = new CategoriaRow { Id=Guid.NewGuid().ToString(),Codigo=Guid.NewGuid().ToString(),Nombre=Guid.NewGuid().ToString(),Activa=true };
        db.Categorias.Add(categoria); await db.SaveChangesAsync();
        var row = await db.Publicaciones.SingleAsync(p => p.Id==url.Split('/',StringSplitOptions.None).Last()); row.CategoriaId=categoria.Id; await db.SaveChangesAsync();
        await Json(url); categoria.Activa=false; await db.SaveChangesAsync();
        Assert.Equal(HttpStatusCode.NotFound,(await f.Client.GetAsync(url)).StatusCode);
        Assert.DoesNotContain((await Json(Base+"/publicaciones")).EnumerateArray(),j => j.GetProperty("id").GetString()==row.Id);
        row.PropietarioUsuarioId=Guid.NewGuid().ToString(); await Assert.ThrowsAsync<InvalidOperationException>(() => db.SaveChangesAsync());
    }
    [Fact] public async Task RendimientoSinteticoYPlanSQL()
    {
        using var scope = f.Factory.Services.CreateScope(); var db = scope.ServiceProvider.GetRequiredService<CatalogoDbContext>();
        for (var n=0;n<200;n++)
        {
            var p = new Publicacion(Guid.NewGuid(),f.Actor,
                new(f.Categoria,"Sintética "+n,new string('x',500),"Bueno","UPC","Sintético","Campus",null,null),
                new(10,null,"PEN","Campus","Uso","Entrega","Devolución","Aviso"));
            var row = new PublicacionRow { Id=p.Id.ToString(),PropietarioUsuarioId=f.Actor.ToString(),CreadoEn=DateTime.UtcNow };
            row.Aplicar(p); db.Publicaciones.Add(row);
        }
        await db.SaveChangesAsync();
        var repo = scope.ServiceProvider.GetRequiredService<ICatalogoRepository>();
        var distributed = scope.ServiceProvider.GetRequiredService<IDistributedCache>();
        var storage = scope.ServiceProvider.GetRequiredService<IImagenStorage>();
        var filters = new FiltrosPublicaciones(Campus:"Sintético");
        foreach (var enabled in new[]{false,true})
        {
            var service = new CatalogoService(repo,new ReadCache(distributed,new(enabled,TimeSpan.FromSeconds(60)),NullLogger<ReadCache>.Instance),storage);
            Assert.Equal(200,(await service.Publicas(filters,default)).Count);
            var samples = new List<double>();
            for (var i=0;i<30;i++)
            {
                var timer=System.Diagnostics.Stopwatch.StartNew();
                Assert.Equal(200,(await service.Publicas(filters,default)).Count); timer.Stop(); samples.Add(timer.Elapsed.TotalMilliseconds);
            }
            samples.Sort(); output.WriteLine($"SYNTHETIC Redis={enabled}, rows=200, sequential=30, meanMs={samples.Average():F3}, p50Ms={samples[14]:F3}, p95Ms={samples[28]:F3}");
        }
        await using var conn = new MySqlConnection(f.Mysql.GetConnectionString()); await conn.OpenAsync();
        await using var command = new MySqlCommand("EXPLAIN SELECT p.id FROM publicaciones p JOIN categorias c ON c.id=p.categoria_id WHERE p.estado='ACTIVA' AND c.activa=1 AND p.campus='Sintético' ORDER BY p.creado_en DESC,p.id DESC",conn);
        await using var reader = await command.ExecuteReaderAsync();
        while(await reader.ReadAsync()) output.WriteLine($"EXPLAIN table={reader["table"]}, type={reader["type"]}, possible_keys={reader["possible_keys"]}, key={reader["key"]}, rows={reader["rows"]}, Extra={reader["Extra"]}");
    }
}
