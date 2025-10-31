package github.nighter.smartspawner.nms;

import github.nighter.smartspawner.utils.DynamicEntityValidator;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wrapper for supported spawnable mobs with dynamic detection.
 * Automatically detects all valid entity types for the current version.
 */
public class SpawnerWrapper {
    public static final List<String> SUPPORTED_MOBS;
    
    static {
        // Dynamically generate list of supported mobs from valid entity types
        SUPPORTED_MOBS = DynamicEntityValidator.getValidEntities().stream()
                .map(EntityType::name)
                .sorted()
                .collect(Collectors.toUnmodifiableList());
    }
}