using System.Net;
using LendUp.Catalogo.Application;
using LendUp.Catalogo.Infrastructure;
using Xunit;
namespace LendUp.Catalogo.Api.Tests;
public sealed class IntegrationAdapterTests
{
    private sealed class Handler(HttpStatusCode status,string body,string token) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken ct)
        {
            Assert.Equal("/api/v1/estudiantes/me",request.RequestUri!.AbsolutePath);
            Assert.Equal("Bearer",request.Headers.Authorization!.Scheme); Assert.Equal(token,request.Headers.Authorization.Parameter);
            return Task.FromResult(new HttpResponseMessage(status) { Content = new StringContent(body) });
        }
    }
    [Fact] public async Task IdentidadExtraeUuidYReenviaToken()
    {
        var id = Guid.NewGuid(); using var http = new HttpClient(new Handler(HttpStatusCode.OK,$"{{\"usuario\":{{\"id\":\"{id}\"}},\"perfil\":{{}}}}","same-token")) { BaseAddress = new("https://identity.test/") };
        Assert.Equal(id,await new IdentidadClient(http).Resolver("same-token",default));
    }
    [Theory,InlineData(401,"{}",401),InlineData(403,"{}",403),InlineData(404,"{}",403),InlineData(500,"{}",503),
        InlineData(200,"{}",503),InlineData(200,"{\"usuario\":{\"id\":\"firebase-uid\"}}",503),InlineData(200,"invalid json",503)]
    public async Task ErroresIdentidad(int httpStatus,string body,int status)
    {
        using var http = new HttpClient(new Handler((HttpStatusCode)httpStatus,body,"token")) { BaseAddress = new("https://identity.test/") };
        Assert.Equal(status,(await Assert.ThrowsAsync<CatalogoException>(() => new IdentidadClient(http).Resolver("token",default))).Status);
    }
    [Theory,InlineData("http://remote.test",true),InlineData("http://localhost",false),InlineData("https://user:password@identity.test",true)]
    public void RechazaUrlInsegura(string url,bool development) => Assert.Throws<InvalidOperationException>(() => IdentidadClient.ValidarBase(url,development));
    [Fact] public void AceptaLocalSoloEnDesarrollo() => Assert.Equal("localhost",IdentidadClient.ValidarBase("http://localhost:8081",true).Host);
    [Fact] public void ConservaPrefijoDeIdentidad() => Assert.Equal("/prefix/",IdentidadClient.ValidarBase("https://identity.test/prefix",false).AbsolutePath);
    [Fact] public async Task FirebaseYCloudinarySinConfigFallanSeguro()
    {
        Assert.Equal(503,(await Assert.ThrowsAsync<CatalogoException>(() => new FirebaseTokenVerifier(() => null).Verificar("token",default))).Status);
        Assert.Equal(503,(await Assert.ThrowsAsync<CatalogoException>(() => new CloudinaryStorage(null).Subir(Guid.NewGuid(),[255,216,255],default))).Status);
    }
}
