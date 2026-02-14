package github.nighter.smartspawner.spawner.gui.layout;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.updates.GuiLayoutUpdater;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class GuiLayoutConfig {
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String STORAGE_GUI_FILE = "storage_gui.yml";
    private static final String MAIN_GUI_FILE = "main_gui.yml";
    private static final String GUI_CONFIG_FILE = "gui_config.yml";
    private static final String DEFAULT_LAYOUT = "default";
    private static final int MIN_SLOT = 1;
    private static final int MAX_SLOT = 9;
    private static final int SLOT_OFFSET = 44;
    private static final int MAIN_GUI_SIZE = 27;

    private final SmartSpawner plugin;
    private final File layoutsDir;
    private final GuiLayoutUpdater layoutUpdater;
    private String currentLayout;
    @Getter
    private GuiLayout currentStorageLayout;
    @Getter
    private GuiLayout currentMainLayout;
    @Getter
    private boolean skipMainGui;

    public GuiLayoutConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        this.layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        this.layoutUpdater = new GuiLayoutUpdater(plugin);
        loadLayout();
    }

    public void loadLayout() {
        this.currentLayout = plugin.getConfig().getString("gui_layout", DEFAULT_LAYOUT);
        initializeLayoutsDirectory();
        
        // Check and update layout files before loading
        layoutUpdater.checkAndUpdateLayouts();
        
        // Load GUI config settings
        loadGuiConfig();

        this.currentStorageLayout = loadCurrentStorageLayout();
        this.currentMainLayout = loadCurrentMainLayout();
    }

    private void initializeLayoutsDirectory() {
        if (!layoutsDir.exists()) {
            layoutsDir.mkdirs();
        }
        autoSaveLayoutFiles();
    }

    private void autoSaveLayoutFiles() {
        try {
            String[] layoutNames = new String[]{DEFAULT_LAYOUT, "DonutSMP"};

            for (String layoutName : layoutNames) {
                File layoutDir = new File(layoutsDir, layoutName);
                if (!layoutDir.exists()) {
                    layoutDir.mkdirs();
                }

                // Save storage GUI layout
                File storageFile = new File(layoutDir, STORAGE_GUI_FILE);
                String storageResourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + STORAGE_GUI_FILE;

                if (!storageFile.exists()) {
                    try {
                        plugin.saveResource(storageResourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to auto-save storage layout resource for " + layoutName + ": " + e.getMessage(), e);
                    }
                }

                // Save main GUI layout
                File mainFile = new File(layoutDir, MAIN_GUI_FILE);
                String mainResourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + MAIN_GUI_FILE;

                if (!mainFile.exists()) {
                    try {
                        plugin.saveResource(mainResourcePath, false);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to auto-save main layout resource for " + layoutName + ": " + e.getMessage(), e);
                    }
                }
            }

            // Save GUI config file (shared across all layouts)
            File guiConfigFile = new File(layoutsDir, GUI_CONFIG_FILE);
            String guiConfigResourcePath = GUI_LAYOUTS_DIR + "/" + GUI_CONFIG_FILE;

            if (!guiConfigFile.exists()) {
                try {
                    plugin.saveResource(guiConfigResourcePath, false);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to auto-save GUI config file: " + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to auto-save layout files", e);
        }
    }

    private void loadGuiConfig() {
        File guiConfigFile = new File(layoutsDir, GUI_CONFIG_FILE);

        if (!guiConfigFile.exists()) {
            plugin.getLogger().warning("GUI config file not found, using defaults");
            this.skipMainGui = false;
            return;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(guiConfigFile);
            this.skipMainGui = config.getBoolean("skip_main_gui", false);

            plugin.getLogger().info("Loaded GUI config - Skip Main GUI: " + skipMainGui);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load GUI config, using defaults: " + e.getMessage(), e);
            this.skipMainGui = false;
        }
    }

    private GuiLayout loadCurrentStorageLayout() {
        return loadLayoutFromFile(STORAGE_GUI_FILE, "storage");
    }

    private GuiLayout loadCurrentMainLayout() {
        return loadLayoutFromFile(MAIN_GUI_FILE, "main");
    }

    private GuiLayout loadLayoutFromFile(String fileName, String layoutType) {
        File layoutDir = new File(layoutsDir, currentLayout);
        File layoutFile = new File(layoutDir, fileName);

        if (layoutFile.exists()) {
            GuiLayout layout = loadLayout(layoutFile, layoutType);
            if (layout != null) {
                plugin.getLogger().info("Loaded " + layoutType + " GUI layout: " + currentLayout);
                return layout;
            }
        }

        if (!currentLayout.equals(DEFAULT_LAYOUT)) {
            plugin.getLogger().warning("Layout '" + currentLayout + "' not found. Attempting to use default layout.");
            File defaultLayoutDir = new File(layoutsDir, DEFAULT_LAYOUT);
            File defaultLayoutFile = new File(defaultLayoutDir, fileName);

            if (defaultLayoutFile.exists()) {
                GuiLayout defaultLayout = loadLayout(defaultLayoutFile, layoutType);
                if (defaultLayout != null) {
                    plugin.getLogger().info("Loaded default " + layoutType + " layout as fallback");
                    return defaultLayout;
                }
            }
        }

        plugin.getLogger().severe("No valid " + layoutType + " layout found! Creating empty layout as fallback.");
        return new GuiLayout();
    }

    private GuiLayout loadLayout(File file, String layoutType) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            GuiLayout layout = new GuiLayout();

            if (!config.contains("buttons")) {
                plugin.getLogger().warning("No buttons section found in GUI layout: " + file.getName());
                return layout;
            }

            for (String buttonKey : config.getConfigurationSection("buttons").getKeys(false)) {
                if (!loadButton(config, layout, buttonKey, layoutType)) {
                    continue;
                }
            }

            return layout;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to load " + layoutType + " layout from " + file.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    private boolean loadButton(FileConfiguration config, GuiLayout layout, String buttonKey, String layoutType) {
        String path = "buttons." + buttonKey;

        if (!config.getBoolean(path + ".enabled", true)) {
            return false;
        }

        int slot = config.getInt(path + ".slot", -1);
        String materialName = config.getString(path + ".material", "STONE");
        String condition = config.getString(path + ".condition", null);

        // Validate slot based on layout type
        if (!isValidSlot(slot, layoutType)) {
            plugin.getLogger().warning(String.format(
                    "Invalid slot %d for button %s in %s layout. Must be between %d and %d.",
                    slot, buttonKey, layoutType, getMinSlot(layoutType), getMaxSlot(layoutType)));
            return false;
        }

        // Check condition if present
        if (condition != null && !evaluateCondition(condition)) {
            return false;
        }

        Material material = parseMaterial(materialName, buttonKey);
        int actualSlot = calculateActualSlot(slot, layoutType);

        // Load actions
        Map<String, String> actions = new HashMap<>();
        ConfigurationSection actionsSection = config.getConfigurationSection(path + ".actions");
        if (actionsSection != null && !actionsSection.getKeys(false).isEmpty()) {
            for (String actionKey : actionsSection.getKeys(false)) {
                String actionValue = actionsSection.getString(actionKey);
                if (actionValue != null && !actionValue.equals("none")) {
                    actions.put(actionKey, actionValue);
                }
            }
        }

        GuiButton button = new GuiButton(buttonKey, actualSlot, material, true, condition, actions);
        layout.addButton(buttonKey, button);
        return true;
    }

    private boolean isValidSlot(int slot, String layoutType) {
        return slot >= getMinSlot(layoutType) && slot <= getMaxSlot(layoutType);
    }

    private int getMinSlot(String layoutType) {
        return "storage".equals(layoutType) ? MIN_SLOT : 1;
    }

    private int getMaxSlot(String layoutType) {
        return "storage".equals(layoutType) ? MAX_SLOT : MAIN_GUI_SIZE;
    }

    private int calculateActualSlot(int slot, String layoutType) {
        if ("storage".equals(layoutType)) {
            return SLOT_OFFSET + slot;
        } else {
            return slot - 1; // Convert 1-based to 0-based indexing for main GUI
        }
    }

    private boolean evaluateCondition(String condition) {
        switch (condition) {
            case "shop_integration":
                return plugin.hasSellIntegration();
            case "no_shop_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown condition: " + condition);
                return true;
        }
    }

    private Material parseMaterial(String materialName, String buttonKey) {
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format(
                    "Invalid material %s for button %s. Using STONE instead.",
                    materialName, buttonKey));
            return Material.STONE;
        }
    }

    public GuiLayout getCurrentLayout() {
        return getCurrentStorageLayout();
    }

    public void reloadLayouts() {
        loadLayout();
    }
}