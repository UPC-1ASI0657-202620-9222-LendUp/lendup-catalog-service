using Microsoft.AspNetCore.Authorization;
using Microsoft.OpenApi;
using Swashbuckle.AspNetCore.SwaggerGen;
namespace LendUp.Catalogo.Api;
public sealed class CatalogoOpenApi : IOperationFilter
{
    public void Apply(OpenApiOperation operation,OperationFilterContext context)
    {
        var route=context.ApiDescription.RelativePath ?? "";
        var tag=route.Contains("imagenes")?"Imágenes":route.Contains("disponibilidades")?"Disponibilidades":route.Contains("categorias")?"Categorías":"Publicaciones";
        context.Document.Tags ??= new HashSet<OpenApiTag>();
        if (!context.Document.Tags.Any(t=>t.Name==tag)) context.Document.Tags.Add(new OpenApiTag { Name=tag });
        operation.Tags=new HashSet<OpenApiTagReference> { new(tag,context.Document) };
        if (!context.MethodInfo.IsDefined(typeof(AllowAnonymousAttribute),true))
            operation.Security=[new OpenApiSecurityRequirement { [new OpenApiSecuritySchemeReference("firebaseBearer",context.Document)] = [] }];
    }
}
