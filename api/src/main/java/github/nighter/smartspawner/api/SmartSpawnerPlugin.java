package github.nighter.smartspawner.api;

/**
 * Interface that the main SmartSpawner plugin class implements.
 * Provides access to the API from the plugin instance.
 */
public interface SmartSpawnerPlugin {

    /**
     * Gets the SmartSpawnerAPI instance.
     *
     * @return the API instance
     */
    SmartSpawnerAPI getAPI();
}