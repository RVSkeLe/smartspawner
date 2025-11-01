package github.nighter.smartspawner.nms;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;

/**
 * VersionInitializer is kept for future compatibility if version-specific
 * initialization is needed. Currently, all initialization is done dynamically
 * through MobHeadConfig.
 */
public class VersionInitializer {
    private final SmartSpawner plugin;
    private final String serverVersion;

    public VersionInitializer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getServer().getBukkitVersion();
    }

    /**
     * Initialize version-specific components.
     * This method is now a no-op as all initialization is handled dynamically.
     * Kept for future compatibility if version-specific logic is needed.
     */
    public void initialize() {
        plugin.debug("Server version: " + serverVersion);
    }
}