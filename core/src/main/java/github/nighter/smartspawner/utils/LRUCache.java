package github.nighter.smartspawner.utils;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.function.Function;

/**
 * A lightweight LRU-style cache backed by Guava's {@link Cache}.
 *
 * <p>This cache automatically evicts least-recently-used entries
 * when the configured maximum size is exceeded.</p>
 *
 * <p>The preferred access pattern is {@link #get(Object, Function)},
 * which provides atomic lazy-loading behavior similar to
 * {@code computeIfAbsent}.</p>
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class LRUCache<K, V> {

    private final Cache<K, V> cache;
    private final int capacity;

    /**
     * Creates a new cache with the specified maximum capacity.
     *
     * @param capacity maximum number of entries allowed in the cache
     * @throws IllegalArgumentException if capacity is less than or equal to zero
     */
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0");
        }

        this.capacity = capacity;
        this.cache = createCache(capacity);
    }

    /**
     * Creates the underlying Guava cache instance.
     *
     * @param capacity maximum cache size
     * @return configured cache instance
     */
    private Cache<K, V> createCache(int capacity) {
        return CacheBuilder.newBuilder()
                .maximumSize(capacity)
                .build();
    }

    /**
     * Retrieves a cached value if present.
     *
     * @param key cache key
     * @return cached value, or {@code null} if absent
     * @deprecated Prefer {@link #get(Object, Function)} for atomic lazy loading
     */
    @Deprecated
    public V get(K key) {
        return cache.getIfPresent(key);
    }

    /**
     * Stores a value in the cache.
     *
     * @param key cache key
     * @param value value to cache
     * @return previously cached value, or {@code null} if absent
     * @deprecated Prefer {@link #get(Object, Function)} for atomic lazy loading
     */
    @Deprecated
    public V put(K key, V value) {
        V previous = cache.getIfPresent(key);
        cache.put(key, value);
        return previous;
    }

    /**
     * Retrieves a cached value, computing and caching it atomically
     * if it is not already present.
     *
     * <p>The mapping function is only invoked when the key is absent.</p>
     *
     * @param key cache key
     * @param mappingFunction value supplier for cache misses
     * @return cached or newly computed value
     * @throws RuntimeException if the mapping function throws an exception
     */
    public V get(K key, Function<K, V> mappingFunction) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(mappingFunction);

        try {
            return cache.get(key, () -> mappingFunction.apply(key));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Removes all entries from the cache.
     */
    public void clear() {
        cache.invalidateAll();
    }

    /**
     * Returns the approximate number of entries currently stored.
     *
     * @return estimated cache size
     */
    public int size() {
        return Math.toIntExact(cache.size());
    }

    /**
     * Returns the configured maximum capacity of the cache.
     *
     * @return maximum cache size
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key cache key to invalidate
     */
    public void remove(K key) {
        cache.invalidate(key);
    }

    /**
     * Checks whether a key currently exists in the cache.
     *
     * @param key cache key
     * @return {@code true} if the key is cached
     */
    public boolean containsKey(K key) {
        return cache.getIfPresent(key) != null;
    }
}
