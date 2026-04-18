package github.nighter.smartspawner.config;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.bukkit.configuration.file.FileConfiguration;
import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

@Getter
public final class Config {

    private static volatile Config instance;

    @Accessors(fluent = true)
    private final boolean shouldConvertNaturalSpawners;
    private final EnumSet<EntityType> entityTypeFilter;
    private final SpawnerConverterFilter spawnerConverterFilterMode;

    private Config(FileConfiguration config) {
        this.shouldConvertNaturalSpawners = config.getBoolean("natural_spawner.convert_to_smart_spawner", false);
        this.entityTypeFilter = loadEntityFilter(config, "natural_spawner.convert_filter.spawner_types");
        this.spawnerConverterFilterMode = parseConversionFilterMode(config.getString("natural_spawner.convert_filter.mode"));
    }

    private SpawnerConverterFilter parseConversionFilterMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return SpawnerConverterFilter.ALL;
        }

        String normalized = mode.toUpperCase();
        try {
            return SpawnerConverterFilter.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
                SmartSpawner.getInstance().getLogger().warning("Invalid natural_spawner.convert_filter.mode '" + mode
                        + "'. Supported values: ALL, WHITELIST, BLACKLIST. Falling back to ALL.");
            return SpawnerConverterFilter.ALL;
        }
    }

    public EnumSet<EntityType> loadEntityFilter(FileConfiguration config, String configPath) {
        List<String> list = config.getStringList(configPath);
        EnumSet<EntityType> set = EnumSet.noneOf(EntityType.class);

        for (String raw : list) {
            if (raw == null || raw.isEmpty()) continue;

            // Normalize config entry
            String fixed = raw.toUpperCase().replace(" ", "_").replace("-", "_");

            try {
                set.add(EntityType.valueOf(fixed));
            } catch (IllegalArgumentException ex) {
                SmartSpawner.getInstance().getLogger().warning("Invalid entity type " + raw + " in " + configPath);
            }
        }

        return (EnumSet<EntityType>) Collections.unmodifiableSet(set);
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

    public enum SpawnerConverterFilter {
        ALL,
        WHITELIST,
        BLACKLIST;
    }
}