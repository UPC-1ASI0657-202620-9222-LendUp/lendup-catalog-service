using System.Data;
using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using LendUp.Catalogo.Application;
using LendUp.Catalogo.Domain;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata;

namespace LendUp.Catalogo.Infrastructure;

public sealed class CategoriaRow
{
    public string Id { get; set; } = "";
    public string Codigo { get; set; } = "";
    public string Nombre { get; set; } = "";
    public bool Activa { get; set; }
    public Categoria Dominio() => new(Guid.Parse(Id), Codigo, Nombre, Activa);
}
public sealed class PublicacionRow
{
    public string Id { get; set; } = "";
    public string PropietarioUsuarioId { get; set; } = "";
    public string CategoriaId { get; set; } = "";
    public string Titulo { get; set; } = "";
    public string Descripcion { get; set; } = "";
    public string CondicionObjeto { get; set; } = "";
    public string Universidad { get; set; } = "";
    public string Campus { get; set; } = "";
    public string Ubicacion { get; set; } = "";
    public decimal? Latitud { get; set; }
    public decimal? Longitud { get; set; }
    public decimal TarifaDiaria { get; set; }
    public decimal? GarantiaMonetaria { get; set; }
    public string Moneda { get; set; } = "";
    public string LugarIntercambio { get; set; } = "";
    public string CondicionesUso { get; set; } = "";
    public string CondicionesEntrega { get; set; } = "";
    public string CondicionesDevolucion { get; set; } = "";
    public string CondicionesCancelacion { get; set; } = "";
    public string Estado { get; set; } = "ACTIVA";
    public DateTime CreadoEn { get; set; }
    public DateTime ActualizadoEn { get; set; }
    public Publicacion Dominio(IEnumerable<DisponibilidadRow> disponibilidades) => new(Guid.Parse(Id), Guid.Parse(PropietarioUsuarioId),
        new(Guid.Parse(CategoriaId), Titulo, Descripcion, CondicionObjeto, Universidad, Campus, Ubicacion, Latitud, Longitud),
        new(TarifaDiaria, GarantiaMonetaria, Moneda, LugarIntercambio, CondicionesUso, CondicionesEntrega, CondicionesDevolucion, CondicionesCancelacion),
        Enum.Parse<EstadoPublicacion>(Estado), disponibilidades.Select(x => x.Dominio()).ToArray());
    public void Aplicar(Publicacion p)
    {
        if (Id != p.Id.ToString() || PropietarioUsuarioId != p.PropietarioUsuarioId.ToString())
            throw new InvalidOperationException("No puede cambiarse la identidad ni el propietario");
        var d = p.Detalles; var c = p.Condiciones;
        CategoriaId = d.CategoriaId.ToString(); Titulo = d.Titulo; Descripcion = d.Descripcion;
        CondicionObjeto = d.CondicionObjeto; Universidad = d.Universidad; Campus = d.Campus;
        Ubicacion = d.Ubicacion; Latitud = d.Latitud; Longitud = d.Longitud;
        TarifaDiaria = c.TarifaDiaria; GarantiaMonetaria = c.GarantiaMonetaria; Moneda = c.Moneda;
        LugarIntercambio = c.LugarIntercambio; CondicionesUso = c.CondicionesUso;
        CondicionesEntrega = c.CondicionesEntrega; CondicionesDevolucion = c.CondicionesDevolucion;
        CondicionesCancelacion = c.CondicionesCancelacion; Estado = p.Estado.ToString();
        // Strictly increases even when two updates happen within one DATETIME(6) tick.
        ActualizadoEn = new DateTime(Math.Max(DateTime.UtcNow.Ticks / 10 * 10, ActualizadoEn.Ticks + 10), DateTimeKind.Unspecified);
    }
}
public sealed class DisponibilidadRow
{
    public string Id { get; set; } = "";
    public string PublicacionId { get; set; } = "";
    public DateTime Desde { get; set; }
    public DateTime Hasta { get; set; }
    public DateTime CreadoEn { get; set; }
    public Disponibilidad Dominio() => new(Guid.Parse(Id), Guid.Parse(PublicacionId), new(Desde, Hasta));
}
public sealed class ImagenRow
{
    public string Id { get; set; } = "";
    public string PublicacionId { get; set; } = "";
    public string CloudinaryPublicId { get; set; } = "";
    public string Url { get; set; } = "";
    public int Orden { get; set; }
    public DateTime CreadoEn { get; set; }
    public ImagenPublicacion Dominio() => new(Guid.Parse(Id), Guid.Parse(PublicacionId), CloudinaryPublicId, Url, Orden);
}

public sealed class CatalogoDbContext(DbContextOptions<CatalogoDbContext> options) : DbContext(options)
{
    public DbSet<CategoriaRow> Categorias => Set<CategoriaRow>();
    public DbSet<PublicacionRow> Publicaciones => Set<PublicacionRow>();
    public DbSet<DisponibilidadRow> Disponibilidades => Set<DisponibilidadRow>();
    public DbSet<ImagenRow> Imagenes => Set<ImagenRow>();
    protected override void OnModelCreating(ModelBuilder m)
    {
        m.Entity<CategoriaRow>().ToTable("categorias");
        m.Entity<PublicacionRow>().ToTable("publicaciones");
        m.Entity<DisponibilidadRow>().ToTable("disponibilidades_publicacion");
        m.Entity<ImagenRow>().ToTable("imagenes_publicacion");
        foreach (var entity in m.Model.GetEntityTypes())
        {
            foreach (var p in entity.GetProperties())
            {
                p.SetColumnName(string.Concat(p.Name.Select((c, i) => char.IsUpper(c) && i > 0 ? "_" + char.ToLowerInvariant(c) : char.ToLowerInvariant(c).ToString())));
                if (p.ClrType == typeof(string))
                {
                    p.SetIsUnicode(true);
                    if (p.Name == "Id" || p.Name.EndsWith("Id") && p.Name != "CloudinaryPublicId")
                    {
                        // Oracle reserves the exact store type char(36) for Guid. Using
                        // the base type plus facets keeps string UUIDs mapped as CHAR(36).
                        p.SetColumnType("char"); p.SetMaxLength(36); p.SetIsFixedLength(true);
                    }
                    else p.SetColumnType("text");
                }
                if (p.ClrType == typeof(DateTime)) p.SetColumnType("datetime(6)");
                if (p.ClrType == typeof(decimal) || p.ClrType == typeof(decimal?))
                    p.SetColumnType(p.Name is "Latitud" or "Longitud" ? "decimal(9,6)" : "decimal(12,2)");
            }
        }
        var c = m.Entity<CategoriaRow>();
        c.Property(x => x.Codigo).HasColumnType("varchar(50)"); c.Property(x => x.Nombre).HasColumnType("varchar(100)");
        c.HasIndex(x => x.Codigo).IsUnique().HasDatabaseName("uq_categorias_codigo");
        c.HasIndex(x => x.Nombre).IsUnique().HasDatabaseName("uq_categorias_nombre");
        var pbl = m.Entity<PublicacionRow>();
        foreach (var (name, len) in new[] { ("Titulo",180),("Universidad",150),("Campus",150),("Ubicacion",250),("LugarIntercambio",250),("Estado",20) })
            pbl.Property(name).HasColumnType($"varchar({len})");
        pbl.Property(x => x.Moneda).HasColumnType("char(3)");
        pbl.Property(x => x.PropietarioUsuarioId).Metadata.SetAfterSaveBehavior(PropertySaveBehavior.Throw);
        pbl.Property(x => x.CreadoEn).Metadata.SetAfterSaveBehavior(PropertySaveBehavior.Throw);
        pbl.HasOne<CategoriaRow>().WithMany().HasForeignKey(x => x.CategoriaId).OnDelete(DeleteBehavior.Restrict);
        pbl.HasIndex(x => x.PropietarioUsuarioId).HasDatabaseName("idx_publicaciones_0");
        pbl.HasIndex(x => new { x.Estado, x.CategoriaId, x.Universidad, x.Campus }).HasDatabaseName("idx_publicaciones_1");
        m.Entity<DisponibilidadRow>().HasOne<PublicacionRow>().WithMany().HasForeignKey(x => x.PublicacionId).OnDelete(DeleteBehavior.Restrict);
        m.Entity<DisponibilidadRow>().HasIndex(x => new { x.PublicacionId, x.Desde, x.Hasta }).HasDatabaseName("idx_disponibilidades_publicacion_0");
        foreach (var name in new[] { nameof(DisponibilidadRow.Desde), nameof(DisponibilidadRow.Hasta) })
            m.Entity<DisponibilidadRow>().Property<DateTime>(name).HasPrecision(6)
                .HasConversion(value => FechaLocalMySql.ParaGuardar(value),
                    value => DateTime.SpecifyKind(value, DateTimeKind.Unspecified));
        m.Entity<ImagenRow>().Property(x => x.CloudinaryPublicId).HasColumnType("varchar(255)");
        m.Entity<ImagenRow>().HasOne<PublicacionRow>().WithMany().HasForeignKey(x => x.PublicacionId).OnDelete(DeleteBehavior.Restrict);
        m.Entity<ImagenRow>().HasIndex(x => new { x.PublicacionId, x.Orden }).IsUnique().HasDatabaseName("uq_imagenes_publicacion_0");
        m.Entity<ImagenRow>().HasIndex(x => x.PublicacionId).HasDatabaseName("idx_imagenes_publicacion_0");

    }
}

public static class FechaLocalMySql
{
    public static DateTime ParaGuardar(DateTime value)
        => value.Kind == DateTimeKind.Unspecified ? value
            : throw new ArgumentException("DATETIME de negocio requiere DateTimeKind.Unspecified");
}

public sealed class CatalogoRepository(CatalogoDbContext db) : ICatalogoRepository
{
    public async Task<IReadOnlyList<Categoria>> Categorias(CancellationToken ct)
        => (await db.Categorias.AsNoTracking().Where(c => c.Activa).OrderBy(c => c.Nombre).ThenBy(c => c.Id).ToListAsync(ct)).Select(c => c.Dominio()).ToArray();
    public async Task<Categoria?> Categoria(Guid id, CancellationToken ct)
        => (await db.Categorias.AsNoTracking().SingleOrDefaultAsync(c => c.Id == id.ToString(), ct))?.Dominio();
    public Task<bool> Visible(Guid id, CancellationToken ct) => PublicasQuery().AnyAsync(p => p.Id == id.ToString(), ct);
    private IQueryable<PublicacionRow> PublicasQuery() => db.Publicaciones.AsNoTracking()
        .Where(p => p.Estado == "ACTIVA" && db.Categorias.Any(c => c.Id == p.CategoriaId && c.Activa));
    public Task<IReadOnlyList<Publicacion>> Publicas(FiltrosPublicaciones f, CancellationToken ct)
    {
        var q = PublicasQuery();
        if (f.Nombre is not null) q = q.Where(p => p.Titulo.Contains(f.Nombre));
        if (f.CategoriaId is not null) q = q.Where(p => p.CategoriaId == f.CategoriaId.ToString());
        if (f.Campus is not null) q = q.Where(p => p.Campus == f.Campus);
        if (f.Universidad is not null) q = q.Where(p => p.Universidad == f.Universidad);
        if (f.Desde is not null) q = q.Where(p => db.Disponibilidades.Any(d => d.PublicacionId == p.Id && d.Desde <= f.Desde && d.Hasta >= f.Hasta));
        return Reconstruir(q, ct);
    }
    public Task<IReadOnlyList<Publicacion>> Propias(Guid actor, CancellationToken ct)
        => Reconstruir(db.Publicaciones.AsNoTracking().Where(p => p.PropietarioUsuarioId == actor.ToString()), ct);
    public async Task<Publicacion?> Obtener(Guid id, CancellationToken ct)
        => (await Reconstruir(db.Publicaciones.AsNoTracking().Where(p => p.Id == id.ToString()), ct)).SingleOrDefault();
    private async Task<IReadOnlyList<Publicacion>> Reconstruir(IQueryable<PublicacionRow> query, CancellationToken ct)
    {
        var rows = await query.OrderByDescending(p => p.CreadoEn).ThenByDescending(p => p.Id).ToListAsync(ct);
        if (rows.Count == 0) return [];
        var ids = rows.Select(x => x.Id).ToArray();
        var slots = (await db.Disponibilidades.AsNoTracking().Where(d => ids.Contains(d.PublicacionId))
            .OrderBy(d => d.Desde).ThenBy(d => d.Id).ToListAsync(ct)).ToLookup(d => d.PublicacionId);
        return rows.Select(p => p.Dominio(slots[p.Id])).ToArray();
    }
    public async Task<SnapshotPublicacion> Snapshot(Guid id, PeriodoDisponibilidad periodo, bool publico, CancellationToken ct)
    {
        // Consistent non-locking snapshot across publication, category and offered intervals.
        // The GET endpoint must never call SaveChanges or create audit records.
        // Reservations owns the accepted contractual snapshot after POST /solicitudes.
        await using var tx = await db.Database.BeginTransactionAsync(IsolationLevel.RepeatableRead, ct);
        var row = await db.Publicaciones.AsNoTracking()
            .SingleOrDefaultAsync(p => p.Id == id.ToString(), ct)
            ?? throw CatalogoException.NoEncontrado();
        var categoriaActiva = await db.Categorias.AsNoTracking()
            .AnyAsync(c => c.Id == row.CategoriaId && c.Activa, ct);
        if (publico && (row.Estado != "ACTIVA" || !categoriaActiva)) throw CatalogoException.NoEncontrado();
        var slots = await db.Disponibilidades.AsNoTracking()
            .Where(d => d.PublicacionId == row.Id).OrderBy(d => d.Desde).ThenBy(d => d.Id).ToListAsync(ct);
        var publicacion = row.Dominio(slots);
        var (snapshot, _) = SnapshotPublicacionFactory.Crear(publicacion, row.ActualizadoEn, categoriaActiva, periodo);
        await tx.CommitAsync(ct);
        return snapshot;
    }

    public async Task Crear(Publicacion p, CancellationToken ct)
    {
        var row = new PublicacionRow { Id = p.Id.ToString(), PropietarioUsuarioId = p.PropietarioUsuarioId.ToString(), CreadoEn = DateTime.UtcNow };
        row.Aplicar(p); db.Publicaciones.Add(row); await db.SaveChangesAsync(ct);
    }
    public async Task<T> Modificar<T>(Guid id, Func<Publicacion, CancellationToken, Task<T>> cambio, CancellationToken ct)
    {
        await using var tx = await db.Database.BeginTransactionAsync(IsolationLevel.ReadCommitted, ct);
        // Parameterized SQL; the lock and aggregate reconstruction share this transaction and connection.
        var rows = await db.Publicaciones.FromSqlInterpolated($"SELECT * FROM publicaciones WHERE id = {id.ToString()} FOR UPDATE").ToListAsync(ct);
        var row = rows.SingleOrDefault() ?? throw CatalogoException.NoEncontrado();
        var slots = await db.Disponibilidades.Where(d => d.PublicacionId == row.Id).OrderBy(d => d.Desde).ThenBy(d => d.Id).ToListAsync(ct);
        var p = row.Dominio(slots);
        var result = await cambio(p, ct);
        row.Aplicar(p);
        var deseados = p.Disponibilidades.ToDictionary(d => d.Id.ToString());
        foreach (var anterior in slots)
        {
            if (!deseados.ContainsKey(anterior.Id)) db.Disponibilidades.Remove(anterior);
        }
        foreach (var d in p.Disponibilidades)
        {
            var slot = slots.SingleOrDefault(x => x.Id == d.Id.ToString());
            if (slot is null)
            {
                slot = new() { Id = d.Id.ToString(), PublicacionId = row.Id, CreadoEn = DateTime.UtcNow };
                db.Disponibilidades.Add(slot);
            }
            slot.Desde = d.Periodo.Desde; slot.Hasta = d.Periodo.Hasta;
        }
        await db.SaveChangesAsync(ct); await tx.CommitAsync(ct); return result;
    }
    public async Task<IReadOnlyList<ImagenPublicacion>> Imagenes(Guid id, CancellationToken ct)
        => (await db.Imagenes.AsNoTracking().Where(i => i.PublicacionId == id.ToString()).OrderBy(i => i.Orden).ToListAsync(ct)).Select(i => i.Dominio()).ToArray();
    public Task AgregarImagen(ImagenPublicacion i, CancellationToken ct)
    {
        db.Imagenes.Add(new() { Id = i.Id.ToString(), PublicacionId = i.PublicacionId.ToString(), CloudinaryPublicId = i.CloudinaryPublicId,
            Url = i.Url, Orden = i.Orden, CreadoEn = DateTime.UtcNow }); return Task.CompletedTask;
    }
    public async Task EliminarImagen(ImagenPublicacion i, CancellationToken ct)
    {
        var row = await db.Imagenes.SingleAsync(x => x.Id == i.Id.ToString() && x.PublicacionId == i.PublicacionId.ToString(), ct);
        db.Imagenes.Remove(row);
    }
    public async Task<string> RevisionPublica(CancellationToken ct)
    {
        var publicaciones = await db.Publicaciones.AsNoTracking().OrderBy(p => p.Id)
            .Select(p => new { p.Id, p.ActualizadoEn }).ToListAsync(ct);
        var categorias = await db.Categorias.AsNoTracking().OrderBy(c => c.Id)
            .Select(c => new { c.Id, c.Codigo, c.Nombre, c.Activa }).ToListAsync(ct);
        var datos = System.Text.Json.JsonSerializer.Serialize(new { publicaciones, categorias });
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(datos)));
    }
}
