package github.nighter.smartspawner.spawner.data.storage;

/**
 * Enumeration of available storage modes for spawner data.
 */
public enum StorageMode {
    /**
     * File-based YAML storage (default).
     * Spawner data is stored in spawners_data.yml
     */
    YAML,

    /**
     * MariaDB database storage with HikariCP connection pool.
     * Requires database configuration in config.yml
     */
    DATABASE
}
