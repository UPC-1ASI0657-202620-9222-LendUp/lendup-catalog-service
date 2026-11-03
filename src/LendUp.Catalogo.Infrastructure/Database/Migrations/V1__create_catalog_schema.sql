-- V1: Esquema propio del bounded context Catálogo, extraído de lendup-backend/schema.sql.
-- Solo referencias UUID hacia otros contextos: NO crear FK a usuarios ni reservas.

CREATE TABLE `categorias` (
  `id` CHAR(36) NOT NULL,
  `codigo` VARCHAR(50) NOT NULL,
  `nombre` VARCHAR(100) NOT NULL,
  `activa` BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_categorias_codigo` (`codigo`),
  UNIQUE KEY `uq_categorias_nombre` (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `publicaciones` (
  `id` CHAR(36) NOT NULL,
  `propietario_usuario_id` CHAR(36) NOT NULL,
  `categoria_id` CHAR(36) NOT NULL,
  `titulo` VARCHAR(180) NOT NULL,
  `descripcion` TEXT NOT NULL,
  `condicion_objeto` TEXT NOT NULL,
  `universidad` VARCHAR(150) NOT NULL,
  `campus` VARCHAR(150) NOT NULL,
  `ubicacion` VARCHAR(250) NOT NULL,
  `lugar_intercambio` VARCHAR(250) NOT NULL,
  `latitud` DECIMAL(9,6),
  `longitud` DECIMAL(9,6),
  `tarifa_diaria` DECIMAL(12,2) NOT NULL,
  `garantia_monetaria` DECIMAL(12,2),
  `moneda` CHAR(3) NOT NULL,
  `condiciones_uso` TEXT NOT NULL,
  `condiciones_entrega` TEXT NOT NULL,
  `condiciones_devolucion` TEXT NOT NULL,
  `condiciones_cancelacion` TEXT NOT NULL,
  `estado` VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `actualizado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_publicaciones_0` (`propietario_usuario_id`),
  KEY `idx_publicaciones_1` (`estado`, `categoria_id`, `universidad`, `campus`),
  CONSTRAINT `fk_publicaciones_categoria_id` FOREIGN KEY (`categoria_id`) REFERENCES `categorias` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `disponibilidades_publicacion` (
  `id` CHAR(36) NOT NULL,
  `publicacion_id` CHAR(36) NOT NULL,
  `desde` DATETIME(6) NOT NULL,
  `hasta` DATETIME(6) NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_disponibilidades_publicacion_0` (`publicacion_id`, `desde`, `hasta`),
  CONSTRAINT `fk_disponibilidades_publicaci_publicacion_id` FOREIGN KEY (`publicacion_id`) REFERENCES `publicaciones` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `imagenes_publicacion` (
  `id` CHAR(36) NOT NULL,
  `publicacion_id` CHAR(36) NOT NULL,
  `cloudinary_public_id` VARCHAR(255) NOT NULL,
  `url` TEXT NOT NULL,
  `orden` INTEGER NOT NULL,
  `creado_en` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_imagenes_publicacion_0` (`publicacion_id`, `orden`),
  KEY `idx_imagenes_publicacion_0` (`publicacion_id`),
  CONSTRAINT `fk_imagenes_publicacion_publicacion_id` FOREIGN KEY (`publicacion_id`) REFERENCES `publicaciones` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- La tabla flyway_schema_history se crea automáticamente.
-- Los bloqueos de Reservas requieren un contrato de integración y se dejan para una migración posterior.
