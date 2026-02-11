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
     * MySQL/MariaDB database storage with HikariCP connection pool.
     * Requires database server configuration in config.yml
     * Supports cross-server spawner management.
     */
    MYSQL,

    /**
     * SQLite database storage with HikariCP connection pool.
     * Local file-based database, no external server required.
     * Good for single-server setups wanting database performance without MariaDB.
     */
    SQLITE
}
