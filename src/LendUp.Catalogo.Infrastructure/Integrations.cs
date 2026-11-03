using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using CloudinaryDotNet;
using CloudinaryDotNet.Actions;
using FirebaseAdmin.Auth;
using LendUp.Catalogo.Application;

namespace LendUp.Catalogo.Infrastructure;

public sealed class FirebaseTokenVerifier(Func<FirebaseAuth?> auth) : IFirebaseTokenVerifier
{
    public async Task<TokenVerificado> Verificar(string token, CancellationToken ct)
    {
        var firebase = auth() ?? throw CatalogoException.Dependencia();
        try
        {
            var verified = await firebase.VerifyIdTokenAsync(token, true, ct);
            return new(verified.Uid, verified.Claims.TryGetValue("email_verified", out var value) && value is true);
        }
        catch (FirebaseAuthException ex) when (ex.AuthErrorCode is AuthErrorCode.ExpiredIdToken or AuthErrorCode.RevokedIdToken
            or AuthErrorCode.InvalidIdToken or AuthErrorCode.UserNotFound)
        { throw new CatalogoException(401, "NO_AUTORIZADO", "Token inválido"); }
        catch (ArgumentException) { throw new CatalogoException(401, "NO_AUTORIZADO", "Token inválido"); }
        catch (FirebaseAuthException) { throw CatalogoException.Dependencia(); }
    }
}

public sealed class IdentidadClient(HttpClient http) : IIdentidadClient
{
    public static Uri ValidarBase(string url, bool desarrollo)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || !string.IsNullOrEmpty(uri.UserInfo)
            || (uri.Scheme != "https" && !(desarrollo && uri.Scheme == "http" && uri.Host is "localhost" or "127.0.0.1")))
            throw new InvalidOperationException("Identidad requiere HTTPS; HTTP sólo para localhost en desarrollo");
        return uri;
    }
    public async Task<Guid> Resolver(string token, CancellationToken ct)
    {
        try
        {
            using var request = new HttpRequestMessage(System.Net.Http.HttpMethod.Get, "api/v1/estudiantes/me");
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            using var response = await http.SendAsync(request, ct);
            if (response.StatusCode == HttpStatusCode.Unauthorized) throw new CatalogoException(401, "NO_AUTORIZADO", "Token inválido");
            if (response.StatusCode is HttpStatusCode.Forbidden or HttpStatusCode.NotFound) throw CatalogoException.Denegado();
            if (!response.IsSuccessStatusCode) throw CatalogoException.Dependencia();
            using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(ct));
            if (!json.RootElement.TryGetProperty("usuario", out var usuario) || usuario.ValueKind != JsonValueKind.Object
                || !usuario.TryGetProperty("id", out var id) || id.ValueKind != JsonValueKind.String
                || !Guid.TryParseExact(id.GetString(), "D", out var uuid)) throw CatalogoException.Dependencia();
            return uuid;
        }
        catch (HttpRequestException) { throw CatalogoException.Dependencia(); }
        catch (JsonException) { throw CatalogoException.Dependencia(); }
        catch (OperationCanceledException) when (!ct.IsCancellationRequested) { throw CatalogoException.Dependencia(); }
    }
}

public sealed class CloudinaryStorage(Cloudinary? cloudinary) : IImagenStorage
{
    private Cloudinary Exigir() => cloudinary ?? throw CatalogoException.Dependencia("ALMACENAMIENTO_NO_DISPONIBLE");
    public async Task<ImagenAlmacenada> Subir(Guid publicacionId, byte[] bytes, CancellationToken ct)
    {
        try
        {
            using var stream = new MemoryStream(bytes, false);
            var result = await Exigir().UploadAsync(new ImageUploadParams
            {
                File = new FileDescription("imagen", stream), PublicId = $"lendup/publicaciones/{publicacionId}/{Guid.NewGuid()}",
                Overwrite = false
            }, ct);
            if (result.Error is not null || string.IsNullOrWhiteSpace(result.PublicId) || result.SecureUrl?.Scheme != "https")
                throw CatalogoException.Dependencia("ALMACENAMIENTO_NO_DISPONIBLE");
            return new(result.PublicId, result.SecureUrl.ToString());
        }
        catch (Exception ex) when (ex is not CatalogoException and not OperationCanceledException)
        { throw CatalogoException.Dependencia("ALMACENAMIENTO_NO_DISPONIBLE"); }
    }
    public async Task Eliminar(string publicId, CancellationToken ct)
    {
        try
        {
            ct.ThrowIfCancellationRequested();
            var result = await Exigir().DestroyAsync(new DeletionParams(publicId)).WaitAsync(ct);
            if (result.Error is not null || result.Result is not ("ok" or "not found"))
                throw CatalogoException.Dependencia("ALMACENAMIENTO_NO_DISPONIBLE");
        }
        catch (Exception ex) when (ex is not CatalogoException and not OperationCanceledException)
        { throw CatalogoException.Dependencia("ALMACENAMIENTO_NO_DISPONIBLE"); }
    }
}
