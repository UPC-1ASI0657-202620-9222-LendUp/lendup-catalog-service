using System.Reflection;
using LendUp.Catalogo.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace LendUp.Catalogo.Api.Tests;

public sealed class InternalContractTests
{
    [Fact]
    public void PublicSnapshotKeepsExistingPublicCatalogRoute()
    {
        var method = typeof(CatalogoController).GetMethod("SnapshotPublico")!;
        Assert.Equal("publicaciones/{id}/snapshot", method.GetCustomAttribute<HttpGetAttribute>()?.Template);
        Assert.NotNull(method.GetCustomAttribute<AllowAnonymousAttribute>());
    }

    [Fact]
    public void PrivateSnapshotRequiresIndependentServiceAuthentication()
    {
        var controller = typeof(InternalCatalogoController);
        Assert.Equal("internal/v1/catalogo", controller.GetCustomAttribute<RouteAttribute>()?.Template);
        Assert.True(controller.GetCustomAttribute<ApiExplorerSettingsAttribute>()?.IgnoreApi);
        var authorization = controller.GetCustomAttribute<AuthorizeAttribute>()!;
        Assert.Equal(CatalogoInternalAuthentication.SchemeName, authorization.AuthenticationSchemes);
        Assert.Equal("ReservationsOnly", authorization.Policy);
        Assert.Equal("publicaciones/{id}/snapshot",
            controller.GetMethod(nameof(InternalCatalogoController.Snapshot))!
                .GetCustomAttribute<HttpGetAttribute>()?.Template);
    }
}
