package github.nighter.smartspawner.spawner.data.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.storage.StorageMode;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages database connections using HikariCP connection pool.
 * Supports MySQL/MariaDB and SQLite for spawner data storage.
 */
public class DatabaseManager {
    private final SmartSpawner plugin;
    private final Logger logger;
    private final StorageMode storageMode;
    private HikariDataSource dataSource;

    // Configuration values
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String serverName;
    private final String sqliteFile;

    // Pool settings
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long maxLifetime;
    private final long idleTimeout;
    private final long keepaliveTime;
    private final long leakDetectionThreshold;

    // MySQL/MariaDB table creation SQL
    private static final String CREATE_TABLE_MYSQL = """
            CREATE TABLE IF NOT EXISTS smart_spawners (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                spawner_id VARCHAR(64) NOT NULL,
                server_name VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing)
                world_name VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,

                -- Entity data
                entity_type VARCHAR(64) NOT NULL,
                item_spawner_material VARCHAR(64) DEFAULT NULL,

                -- Settings
                spawner_exp INT NOT NULL DEFAULT 0,
                spawner_active BOOLEAN NOT NULL DEFAULT TRUE,
                spawner_range INT NOT NULL DEFAULT 16,
                spawner_stop BOOLEAN NOT NULL DEFAULT TRUE,
                spawn_delay BIGINT NOT NULL DEFAULT 500,
                max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                max_stored_exp INT NOT NULL DEFAULT 1000,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                is_at_capacity BOOLEAN NOT NULL DEFAULT FALSE,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Inventory (JSON blob)
                inventory_data MEDIUMTEXT DEFAULT NULL,

                -- Timestamps
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                -- Indexes
                UNIQUE KEY uk_server_spawner (server_name, spawner_id),
                UNIQUE KEY uk_location (server_name, world_name, loc_x, loc_y, loc_z),
                INDEX idx_server (server_name),
                INDEX idx_world (server_name, world_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

    // SQLite table creation SQL (slightly different syntax)
    private static final String CREATE_TABLE_SQLITE = """
            CREATE TABLE IF NOT EXISTS smart_spawners (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                spawner_id VARCHAR(64) NOT NULL,
                server_name VARCHAR(64) NOT NULL,

                -- Location (separate columns for indexing)
                world_name VARCHAR(128) NOT NULL,
                loc_x INT NOT NULL,
                loc_y INT NOT NULL,
                loc_z INT NOT NULL,

                -- Entity data
                entity_type VARCHAR(64) NOT NULL,
                item_spawner_material VARCHAR(64) DEFAULT NULL,

                -- Settings
                spawner_exp INT NOT NULL DEFAULT 0,
                spawner_active BOOLEAN NOT NULL DEFAULT 1,
                spawner_range INT NOT NULL DEFAULT 16,
                spawner_stop BOOLEAN NOT NULL DEFAULT 1,
                spawn_delay BIGINT NOT NULL DEFAULT 500,
                max_spawner_loot_slots INT NOT NULL DEFAULT 45,
                max_stored_exp INT NOT NULL DEFAULT 1000,
                min_mobs INT NOT NULL DEFAULT 1,
                max_mobs INT NOT NULL DEFAULT 4,
                stack_size INT NOT NULL DEFAULT 1,
                max_stack_size INT NOT NULL DEFAULT 1000,
                last_spawn_time BIGINT NOT NULL DEFAULT 0,
                is_at_capacity BOOLEAN NOT NULL DEFAULT 0,

                -- Player interaction
                last_interacted_player VARCHAR(64) DEFAULT NULL,
                preferred_sort_item VARCHAR(64) DEFAULT NULL,
                filtered_items TEXT DEFAULT NULL,

                -- Inventory (JSON blob)
                inventory_data TEXT DEFAULT NULL,

                -- Timestamps
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                -- Unique constraints
                UNIQUE (server_name, spawner_id),
                UNIQUE (server_name, world_name, loc_x, loc_y, loc_z)
            )
            """;

    // SQLite index creation (separate statements)
    private static final String CREATE_INDEX_SERVER_SQLITE =
            "CREATE INDEX IF NOT EXISTS idx_server ON smart_spawners (server_name)";
    private static final String CREATE_INDEX_WORLD_SQLITE =
            "CREATE INDEX IF NOT EXISTS idx_world ON smart_spawners (server_name, world_name)";

    public DatabaseManager(SmartSpawner plugin, StorageMode storageMode) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.storageMode = storageMode;

        // Load configuration
        this.host = plugin.getConfig().getString("database.sql.host", "localhost");
        this.port = plugin.getConfig().getInt("database.sql.port", 3306);
        this.database = plugin.getConfig().getString("database.database", "smartspawner");
        this.username = plugin.getConfig().getString("database.sql.username", "root");
        this.password = plugin.getConfig().getString("database.sql.password", "");
        this.serverName = plugin.getConfig().getString("database.server_name", "server1");
        this.sqliteFile = plugin.getConfig().getString("database.sqlite.file", "spawners.db");

        // Pool settings
        this.maxPoolSize = plugin.getConfig().getInt("database.sql.pool.maximum-size", 10);
        this.minIdle = plugin.getConfig().getInt("database.sql.pool.minimum-idle", 2);
        this.connectionTimeout = plugin.getConfig().getLong("database.sql.pool.connection-timeout", 10000);
        this.maxLifetime = plugin.getConfig().getLong("database.sql.pool.max-lifetime", 1800000);
        this.idleTimeout = plugin.getConfig().getLong("database.sql.pool.idle-timeout", 600000);
        this.keepaliveTime = plugin.getConfig().getLong("database.sql.pool.keepalive-time", 30000);
        this.leakDetectionThreshold = plugin.getConfig().getLong("database.sql.pool.leak-detection-threshold", 0);
    }

    /**
     * Initialize the database connection pool and create tables.
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            setupDataSource();
            createTables();
            logger.info("Database connection pool initialized successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection pool", e);
            return false;
        }
    }

    private void setupDataSource() {
        HikariConfig config = new HikariConfig();

        if (storageMode == StorageMode.SQLITE) {
            setupSQLiteDataSource(config);
        } else {
            setupMySQLDataSource(config);
        }

        dataSource = new HikariDataSource(config);
    }

    private void setupMySQLDataSource(HikariConfig config) {
        // JDBC URL for MariaDB/MySQL
        String jdbcUrl = String.format("jdbc:mariadb://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                host, port, database);

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("github.nighter.smartspawner.libs.mariadb.Driver");
        config.setUsername(username);
        config.setPassword(password);

        // Pool settings
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setIdleTimeout(idleTimeout);
        config.setKeepaliveTime(keepaliveTime);
        config.setLeakDetectionThreshold(leakDetectionThreshold);

        // Performance settings for MySQL/MariaDB
        config.setPoolName("SmartSpawner-HikariCP");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    private void setupSQLiteDataSource(HikariConfig config) {
        // Create data folder if it doesn't exist
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // JDBC URL for SQLite (file-based)
        File dbFile = new File(dataFolder, sqliteFile);
        String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");

        // SQLite-specific pool settings (SQLite doesn't handle multiple connections well)
        config.setMaximumPoolSize(1);  // SQLite works best with single connection
        config.setMinimumIdle(1);
        config.setConnectionTimeout(connectionTimeout);
        config.setMaxLifetime(0);  // Disable max lifetime for SQLite
        config.setIdleTimeout(0);  // Disable idle timeout for SQLite

        // SQLite performance settings
        config.setPoolName("SmartSpawner-SQLite-HikariCP");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("cache_size", "10000");
        config.addDataSourceProperty("foreign_keys", "ON");
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            if (storageMode == StorageMode.SQLITE) {
                stmt.execute(CREATE_TABLE_SQLITE);
                stmt.execute(CREATE_INDEX_SERVER_SQLITE);
                stmt.execute(CREATE_INDEX_WORLD_SQLITE);
            } else {
                stmt.execute(CREATE_TABLE_MYSQL);
            }

            plugin.debug("Database tables created/verified successfully.");
        }
    }

    /**
     * Get a connection from the pool.
     * @return A database connection
     * @throws SQLException if connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Get the configured server name for this server.
     * @return The server name used to identify spawners
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Get the storage mode this manager is configured for.
     * @return The storage mode (MYSQL or SQLITE)
     */
    public StorageMode getStorageMode() {
        return storageMode;
    }

    /**
     * Check if the database connection pool is active.
     * @return true if the pool is active and accepting connections
     */
    public boolean isActive() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Shutdown the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
