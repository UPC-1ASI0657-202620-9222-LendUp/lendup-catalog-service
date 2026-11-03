-- V2: categorías iniciales de LendUp. No modifica estructuras de V1.
-- Los UUID/códigos son estables para que la carga sea reproducible.
-- Revisar la taxonomía con Product antes de habilitar el entorno público.
INSERT IGNORE INTO categorias (id, codigo, nombre, activa) VALUES
('c0000000-0000-4000-8000-000000000001', 'CAMARAS', 'Cámaras y fotografía', TRUE),
('c0000000-0000-4000-8000-000000000002', 'CALCULADORAS', 'Calculadoras', TRUE),
('c0000000-0000-4000-8000-000000000003', 'LIBROS', 'Libros', TRUE),
('c0000000-0000-4000-8000-000000000004', 'DIBUJO', 'Instrumentos de dibujo', TRUE),
('c0000000-0000-4000-8000-000000000005', 'LABORATORIO', 'Materiales de laboratorio', TRUE),
('c0000000-0000-4000-8000-000000000006', 'HERRAMIENTAS', 'Herramientas', TRUE),
('c0000000-0000-4000-8000-000000000007', 'ELECTRONICA', 'Electrónica y accesorios', TRUE),
('c0000000-0000-4000-8000-000000000008', 'OTROS', 'Otros objetos', TRUE);
