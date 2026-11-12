-- Immutable audit records for offers shown to borrowers and verified by Reservations.
-- Created idempotently at the application level under a publication FOR UPDATE lock.
-- Do not UPDATE/DELETE these rows: older consent references must remain auditable.
CREATE TABLE `snapshots_publicacion` (
  `id` VARCHAR(70) NOT NULL,
  `publicacion_id` CHAR(36) NOT NULL,
  `snapshot_hash` CHAR(64) NOT NULL,
  `snapshot_json` LONGTEXT NOT NULL,
  `creado_en` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_snapshots_publicacion_0` (`publicacion_id`),
  CONSTRAINT `fk_snapshots_publicacion_publicacion_id` FOREIGN KEY (`publicacion_id`)
    REFERENCES `publicaciones` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
