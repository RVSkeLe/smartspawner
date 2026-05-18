package github.nighter.smartspawner.config;

import github.nighter.smartspawner.SmartSpawner;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
public class Config {
    @Getter(AccessLevel.NONE)
    private static volatile Config instance;

    private final boolean optimizedLootgen;
    private final int approximationThreshold;

    private Config(FileConfiguration config) {
        this.optimizedLootgen = config.getBoolean("loot_generation.optimized_generation", false);
        this.approximationThreshold = config.getInt("loot_generation.approximation_threshold", 1000);
    }

    public static Config get() {
        return instance;
    }

    public static void reload(SmartSpawner plugin) {
        load(plugin);
    }

    public static void load(SmartSpawner plugin) {
        instance = new Config(plugin.getConfig());
    }
}
