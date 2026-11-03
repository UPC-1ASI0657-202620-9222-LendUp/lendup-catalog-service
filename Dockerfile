FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /source
COPY src/LendUp.Catalogo.Domain/ src/LendUp.Catalogo.Domain/
COPY src/LendUp.Catalogo.Application/ src/LendUp.Catalogo.Application/
COPY src/LendUp.Catalogo.Infrastructure/ src/LendUp.Catalogo.Infrastructure/
COPY src/LendUp.Catalogo.Api/ src/LendUp.Catalogo.Api/
RUN dotnet restore src/LendUp.Catalogo.Api/LendUp.Catalogo.Api.csproj
RUN dotnet publish src/LendUp.Catalogo.Api/LendUp.Catalogo.Api.csproj -c Release --no-restore -o /app
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
COPY --from=build /app .
USER $APP_UID
ENV SERVER_PORT=8082
EXPOSE 8082
ENTRYPOINT ["dotnet","LendUp.Catalogo.Api.dll"]
