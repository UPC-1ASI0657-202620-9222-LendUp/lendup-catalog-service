using System.Security.Claims;
using System.Text.Encodings.Web;
using LendUp.Catalogo.Application;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Options;
namespace LendUp.Catalogo.Api;
public sealed class FirebaseAuthentication(IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger, UrlEncoder encoder, IServiceProvider services)
    : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        if (Context.GetEndpoint()?.Metadata.GetMetadata<IAllowAnonymous>() is not null) return AuthenticateResult.NoResult();
        var header = Request.Headers.Authorization.ToString();
        if (string.IsNullOrEmpty(header)) return AuthenticateResult.NoResult();
        if (!header.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) || string.IsNullOrWhiteSpace(header[7..])) return AuthenticateResult.Fail("Bearer inválido");
        var token = header[7..].Trim();
        var verified = await services.GetRequiredService<IFirebaseTokenVerifier>().Verificar(token, Context.RequestAborted);
        if (string.IsNullOrWhiteSpace(verified.Uid)) return AuthenticateResult.Fail("Token inválido");
        if (!verified.EmailVerified) throw CatalogoException.Denegado();
        var actor = await services.GetRequiredService<IIdentidadClient>().Resolver(token, Context.RequestAborted);
        var principal = new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, actor.ToString())], Scheme.Name));
        return AuthenticateResult.Success(new AuthenticationTicket(principal, Scheme.Name));
    }
}
