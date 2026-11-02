# lendup-catalog-service

Microservicio de Catálogo de LendUp, basado en Java 21 y Spring Boot 4.

## Estado del proyecto

Proyecto inicial generado con Spring Initializr. La estructura de paquetes DDD ya está preparada;
**las funcionalidades de Catálogo y las integraciones todavía no han sido migradas**.

Consulta [la arquitectura inicial](docs/architecture.md) para conocer responsabilidades,
límites del bounded context, dependencias previstas y siguientes pasos.

## Tecnologías previstas

Java 21, Spring Boot 4, Maven, MySQL, Flyway, Firebase y documentación OpenAPI.
La integración gRPC con Identidad y el contrato REST con Reservas se incorporarán
cuando sus contratos y seguridad estén acordados.
