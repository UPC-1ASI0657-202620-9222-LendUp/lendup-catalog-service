using System.Diagnostics;
using System.Diagnostics.Metrics;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using LendUp.Catalogo.Application;
using Microsoft.Extensions.Caching.Distributed;
using Microsoft.Extensions.Logging;

namespace LendUp.Catalogo.Infrastructure;

public sealed record CacheOptions(bool Enabled, TimeSpan Ttl);
public sealed class ReadCache(IDistributedCache cache, CacheOptions options, ILogger<ReadCache> logger) : ILecturaCache
{
    public const string MeterName = "LendUp.Catalogo.Cache";
    private static readonly Meter Meter = new(MeterName);
    private static readonly Counter<long> Hits = Meter.CreateCounter<long>("catalogo.cache.hit");
    private static readonly Counter<long> Misses = Meter.CreateCounter<long>("catalogo.cache.miss");
    private static readonly Counter<long> Errors = Meter.CreateCounter<long>("catalogo.cache.error");
    private static readonly Histogram<double> Latency = Meter.CreateHistogram<double>("catalogo.cache.duration", "ms");
    // Bounded stripes avoid an unbounded dictionary of synchronization objects.
    private readonly SemaphoreSlim[] stripes = Enumerable.Range(0, 64).Select(_ => new SemaphoreSlim(1)).ToArray();
    public async Task<T> Obtener<T>(string clave, Func<CancellationToken, Task<T>> consultar, CancellationToken ct)
    {
        if (!options.Enabled) return await consultar(ct);
        var hash = SHA256.HashData(Encoding.UTF8.GetBytes(clave));
        var key = "lendup:catalogo:v1:" + Convert.ToHexString(hash);
        var stripe = stripes[hash[0] % stripes.Length];
        await stripe.WaitAsync(ct);
        var start = Stopwatch.GetTimestamp();
        try
        {
            try
            {
                var bytes = await cache.GetAsync(key, ct);
                if (bytes is not null)
                {
                    var value = JsonSerializer.Deserialize<T>(bytes);
                    if (value is not null) { Hits.Add(1); return value; }
                }
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            { Errors.Add(1); logger.LogWarning("Redis read unavailable ({ErrorType})", ex.GetType().Name); }
            Misses.Add(1);
            var result = await consultar(ct);
            try
            {
                await cache.SetAsync(key, JsonSerializer.SerializeToUtf8Bytes(result), new DistributedCacheEntryOptions
                    { AbsoluteExpirationRelativeToNow = options.Ttl }, ct);
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            { Errors.Add(1); logger.LogWarning("Redis write unavailable ({ErrorType})", ex.GetType().Name); }
            return result;
        }
        finally { Latency.Record(Stopwatch.GetElapsedTime(start).TotalMilliseconds); stripe.Release(); }
    }
}
