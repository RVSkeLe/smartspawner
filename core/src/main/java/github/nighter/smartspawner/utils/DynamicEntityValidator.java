package github.nighter.smartspawner.utils;

import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates spawnable entity types across Minecraft versions.
 * Only loads available entities in the current version, not newer entities from later versions.
 */
public class DynamicEntityValidator {
    private static final Set<EntityType> VALID_SPAWNABLE_ENTITIES;
    
    static {
        VALID_SPAWNABLE_ENTITIES = Arrays.stream(EntityType.values())
            .filter(type -> type != EntityType.PLAYER)
            .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Check if an entity type is valid for spawning
     * 
     * @param type Entity type to check
     * @return true if the entity type is valid for spawning
     */
    public static boolean isValidSpawnerEntity(EntityType type) {
        return VALID_SPAWNABLE_ENTITIES.contains(type);
    }
    
    /**
     * Get all valid spawnable entity types
     * 
     * @return Unmodifiable set of valid entity types
     */
    public static Set<EntityType> getValidEntities() {
        return VALID_SPAWNABLE_ENTITIES;
    }
}
