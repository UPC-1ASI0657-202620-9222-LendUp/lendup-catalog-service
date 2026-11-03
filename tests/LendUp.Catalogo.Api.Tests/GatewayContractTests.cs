using System.Reflection;
using LendUp.Catalogo.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Xunit;

namespace LendUp.Catalogo.Api.Tests;

/// <summary>Prevents breaking the unchanged paths and authentication rules consumed by the LendUp API Gateway.</summary>
public class GatewayContractTests
{
    [Fact]
    public void MantienePrefijoApiV1Catalogo()
    {
        var type = typeof(CatalogoController);
        Assert.Equal("api/v1/catalogo", type.GetCustomAttribute<RouteAttribute>()?.Template);
        Assert.NotNull(type.GetCustomAttribute<AuthorizeAttribute>());
    }

    [Theory]
    [InlineData(nameof(CatalogoController.Categorias), "categorias")]
    [InlineData(nameof(CatalogoController.Publicaciones), "publicaciones")]
    [InlineData(nameof(CatalogoController.Publicacion), "publicaciones/{id}")]
    [InlineData(nameof(CatalogoController.Imagenes), "publicaciones/{publicacionId}/imagenes")]
    public void RutasDeLecturaMantienenAccesoPublico(string method, string path)
    {
        var action = typeof(CatalogoController).GetMethod(method)!;
        Assert.Equal(path, action.GetCustomAttribute<HttpGetAttribute>()?.Template);
        Assert.NotNull(action.GetCustomAttribute<AllowAnonymousAttribute>());
    }

    [Theory]
    [InlineData(nameof(CatalogoController.Crear), "publicaciones")]
    [InlineData(nameof(CatalogoController.SubirImagen), "publicaciones/{publicacionId}/imagenes")]
    public void RutasDeEscrituraRequierenFirebase(string method, string path)
    {
        var action = typeof(CatalogoController).GetMethod(method)!;
        Assert.Equal(path, action.GetCustomAttribute<HttpPostAttribute>()?.Template);
        Assert.Null(action.GetCustomAttribute<AllowAnonymousAttribute>());
        Assert.NotNull(typeof(CatalogoController).GetCustomAttribute<AuthorizeAttribute>());
    }
}
