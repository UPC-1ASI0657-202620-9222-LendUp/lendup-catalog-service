using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace LendUp.Catalogo.Api;

/// <summary>
/// An isolated S2S scheme. The public Firebase scheme cannot authenticate internal callers.
/// Access is denied if the configured secret is absent or weaker than 32 characters.
/// Deploy /internal/** only behind private networking and TLS.
/// </summary>
public sealed class CatalogoInternalAuthentication(
    IOptionsMonitor<AuthenticationSchemeOptions> options, ILoggerFactory logger,
    UrlEncoder encoder, IConfiguration config)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    public const string SchemeName = "CatalogInternal";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var secret = config["CATALOG_RESERVATIONS_SERVICE_TOKEN"];
        var header = Request.Headers.Authorization.ToString();
        if (string.IsNullOrWhiteSpace(header))
            return Task.FromResult(AuthenticateResult.NoResult());

        if (string.IsNullOrWhiteSpace(secret) || secret.Length < 32 || secret.Length > 512 ||
            !header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            return Task.FromResult(AuthenticateResult.Fail("Credencial de servicio inválida"));

        var received = header[7..].Trim();
        if (received.Length == 0 || received.Length > 512)
            return Task.FromResult(AuthenticateResult.Fail("Credencial de servicio inválida"));

        var expectedDigest = SHA256.HashData(Encoding.UTF8.GetBytes(secret));
        var actualDigest = SHA256.HashData(Encoding.UTF8.GetBytes(received));
        if (!CryptographicOperations.FixedTimeEquals(actualDigest, expectedDigest))
            return Task.FromResult(AuthenticateResult.Fail("Credencial de servicio inválida"));

        var claims = new[] { new Claim(ClaimTypes.NameIdentifier, "reservations"), new Claim("service", "reservations") };
        var identity = new ClaimsIdentity(claims, SchemeName);
        return Task.FromResult(AuthenticateResult.Success(
            new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName)));
    }
}
