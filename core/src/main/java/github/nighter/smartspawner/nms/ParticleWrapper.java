package github.nighter.smartspawner.nms;

import org.bukkit.Particle;

/**
 * Wrapper for particle types with dynamic detection.
 * Automatically handles particle naming differences across versions.
 */
public class ParticleWrapper {
    public static final Particle VILLAGER_HAPPY;
    public static final Particle SPELL_WITCH;
    
    static {
        // Try to find the correct particle name for the current version
        VILLAGER_HAPPY = findParticle("VILLAGER_HAPPY", "HAPPY_VILLAGER");
        SPELL_WITCH = findParticle("SPELL_WITCH", "WITCH");
    }
    
    /**
     * Find a particle by trying multiple possible names
     * 
     * @param names Possible particle names to try
     * @return The particle, or null if not found
     */
    private static Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException e) {
                // Try next name
            }
        }
        // Return a safe default if none found
        return Particle.CLOUD;
    }
}