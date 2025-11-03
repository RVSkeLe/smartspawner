package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.ColorUtil;
import github.nighter.smartspawner.language.LanguageManager;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SpawnerHologram {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final AtomicReference<TextDisplay> textDisplay = new AtomicReference<>(null);
    private final Location spawnerLocation;
    private int stackSize;
    private EntityType entityType;
    private int currentExp;
    private int maxExp;
    private int currentItems;
    private int maxSlots;
    private static final String HOLOGRAM_IDENTIFIER = "SmartSpawner-Holo";
    private final String uniqueIdentifier;

    private static final Vector3f SCALE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f TRANSLATION = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final AxisAngle4f ROTATION = new AxisAngle4f(0, 0, 0, 0);

    public SpawnerHologram(Location location) {
        this.plugin = SmartSpawner.getInstance();
        this.spawnerLocation = location;
        this.languageManager = plugin.getLanguageManager();
        this.uniqueIdentifier = generateUniqueIdentifier(location);
    }

    private String generateUniqueIdentifier(Location location) {
        return HOLOGRAM_IDENTIFIER + "-" +
                location.getWorld().getName() + "-" +
                location.getBlockX() + "-" +
                location.getBlockY() + "-" +
                location.getBlockZ();
    }

    public void createHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // Clean up any existing hologram for this spawner first
        cleanupExistingHologram();

        double offsetX = plugin.getConfig().getDouble("hologram.offset_x", 0.5);
        double offsetY = plugin.getConfig().getDouble("hologram.offset_y", 0.5);
        double offsetZ = plugin.getConfig().getDouble("hologram.offset_z", 0.5);

        Location holoLoc = spawnerLocation.clone().add(offsetX, offsetY, offsetZ);

        // Use the location scheduler to spawn the entity in the correct region
        Scheduler.runLocationTask(holoLoc, () -> {
            try {
                TextDisplay display = spawnerLocation.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
                    td.setBillboard(Display.Billboard.CENTER);
                    // Get alignment from config with CENTER as default
                    String alignmentStr = plugin.getConfig().getString("hologram.alignment", "CENTER");
                    TextDisplay.TextAlignment alignment;
                    try {
                        alignment = TextDisplay.TextAlignment.valueOf(alignmentStr.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        alignment = TextDisplay.TextAlignment.CENTER;
                        plugin.getLogger().warning("Invalid hologram alignment in config: " + alignmentStr + ". Using CENTER as default.");
                    }
                    td.setAlignment(alignment);
                    td.setViewRange(16.0f);
                    td.setShadowed(plugin.getConfig().getBoolean("hologram.shadowed_text", true));
                    td.setDefaultBackground(false);
                    td.setTransformation(new Transformation(TRANSLATION, ROTATION, SCALE, ROTATION));
                    td.setSeeThrough(plugin.getConfig().getBoolean("hologram.see_through", false));
                    // Add custom name for identification
                    td.setCustomName(uniqueIdentifier);
                    td.setCustomNameVisible(false);
                    // Set persistent to false to prevent hologram from being saved and potentially getting stuck
                    td.setPersistent(false);
                });

                textDisplay.set(display);
                updateText();
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating hologram: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void updateText() {
        TextDisplay display = textDisplay.get();
        if (display == null || entityType == null) return;

        // Don't check isValid() here as it needs to be on the entity thread

        // Prepare the text content outside of the entity thread
        String entityTypeName = languageManager.getFormattedMobName(entityType);

        // Create replacements map
        Map<String, String> replacements = new HashMap<>();
        replacements.put("entity", entityTypeName);
        replacements.put("ᴇɴᴛɪᴛʏ", languageManager.getSmallCaps(entityTypeName));
        replacements.put("stack_size", String.valueOf(stackSize));
        replacements.put("current_exp", languageManager.formatNumber(currentExp));
        replacements.put("max_exp", languageManager.formatNumber(maxExp));
        replacements.put("used_slots", languageManager.formatNumber(currentItems));
        replacements.put("max_slots", languageManager.formatNumber(maxSlots));
        
        // Calculate and add percentage placeholders
        double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
        String formattedPercentStorage = String.format("%.1f", percentStorageDecimal);
        int percentStorageRounded = (int) Math.round(percentStorageDecimal);
        replacements.put("percent_storage_decimal", formattedPercentStorage);
        replacements.put("percent_storage_rounded", String.valueOf(percentStorageRounded));
        
        double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
        String formattedPercentExp = String.format("%.1f", percentExpDecimal);
        int percentExpRounded = (int) Math.round(percentExpDecimal);
        replacements.put("percent_exp_decimal", formattedPercentExp);
        replacements.put("percent_exp_rounded", String.valueOf(percentExpRounded));

        String hologramText = languageManager.getHologramText();

        // Apply replacements
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            hologramText = hologramText.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Apply color codes
        final String finalText = ColorUtil.translateHexColorCodes(hologramText);

        // Schedule the entity update on the entity's thread
        Scheduler.runEntityTask(display, () -> {
            if (display.isValid()) {
                display.setText(finalText);
            }
        });
    }

    public void updateData(int stackSize, EntityType entityType, int currentExp, int maxExp, int currentItems, int maxSlots) {
        // Update data values
        this.stackSize = stackSize;
        this.entityType = entityType;
        this.currentExp = currentExp;
        this.maxExp = maxExp;
        this.currentItems = currentItems;
        this.maxSlots = maxSlots;

        // First, ensure we have a valid hologram
        TextDisplay display = textDisplay.get();
        if (display == null) {
            // If hologram doesn't exist, recreate it
            createHologram();
        } else {
            // Check validity on the entity thread to avoid race conditions
            Scheduler.runEntityTask(display, () -> {
                if (!display.isValid()) {
                    // If invalid, recreate the hologram
                    textDisplay.set(null);
                    createHologram();
                } else {
                    // Update the text display
                    updateText();
                }
            });
        }
    }

    public void remove() {
        TextDisplay display = textDisplay.get();
        if (display != null) {
            // Run on the entity's thread to ensure safe removal
            Scheduler.runEntityTask(display, () -> {
                if (display.isValid()) {
                    display.remove();
                }
            });
            textDisplay.set(null);
        }
        // Also clean up any stuck holograms
        cleanupExistingHologram();
    }

    public void cleanupExistingHologram() {
        if (spawnerLocation == null || spawnerLocation.getWorld() == null) return;

        // First, check if our tracked hologram is still valid
        TextDisplay display = textDisplay.get();
        if (display != null) {
            // Always remove the tracked display, even if it appears invalid
            Scheduler.runEntityTask(display, () -> {
                if (display.isValid()) {
                    display.remove();
                }
            });
            textDisplay.set(null);
        }

        // Use async task to avoid blocking
        Scheduler.runLocationTask(spawnerLocation, () -> {
            // Define a tighter search radius just to catch any potentially duplicated holograms
            // with the same identifier (which shouldn't happen but being safe)
            double searchRadius = 2.0;

            // Look for any entity with our specific unique identifier
            spawnerLocation.getWorld().getNearbyEntities(spawnerLocation, searchRadius, searchRadius, searchRadius)
                    .stream()
                    .filter(entity -> entity instanceof TextDisplay && entity.getCustomName() != null)
                    .filter(entity -> entity.getCustomName().equals(uniqueIdentifier))
                    .forEach(entity -> {
                        Scheduler.runEntityTask(entity, entity::remove);
                    });
        });
    }
}