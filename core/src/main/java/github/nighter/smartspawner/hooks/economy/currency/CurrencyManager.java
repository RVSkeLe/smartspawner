package github.nighter.smartspawner.hooks.economy.currency;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.OfflinePlayer;
import su.nightexpress.coinsengine.api.CoinsEngineAPI;
import su.nightexpress.coinsengine.api.currency.Currency;

import java.util.logging.Level;

public class CurrencyManager {
    private final SmartSpawner plugin;

    @Getter
    private boolean currencyAvailable = false;

    @Getter
    private String activeCurrencyProvider = "None";

    private Economy vaultEconomy;

    private Currency coinsEngineCurrency;

    @Getter
    private String configuredCurrencyType;

    @Getter
    private String configuredCoinsEngineCurrency;

    public CurrencyManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfiguration();
        setupCurrency();
    }

    private void loadConfiguration() {
        this.configuredCurrencyType = plugin.getConfig().getString("custom_economy.currency", "VAULT");
        this.configuredCoinsEngineCurrency = plugin.getConfig().getString("custom_economy.coinsengine_currency", "coins");
    }

    private void setupCurrency() {
        currencyAvailable = false;
        activeCurrencyProvider = "None";

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            currencyAvailable = setupVaultEconomy();

        } else if (configuredCurrencyType.equalsIgnoreCase("COINSENGINE")) {
            currencyAvailable = setupCoinsEngineEconomy();

        } else {
            plugin.getLogger().warning("Unsupported currency type: " + configuredCurrencyType + ". Currently only VAULT is supported.");
            plugin.getLogger().warning("Economy features will be disabled.");
        }
    }

    private boolean setupVaultEconomy() {
        // Check if Vault plugin is available
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Selling items from spawner will be disabled.");
            return false;
        }

        try {
            // Get economy service provider
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                plugin.getLogger().warning("No economy provider found for Vault! Selling items from spawner will be disabled.");
                return false;
            }

            vaultEconomy = rsp.getProvider();
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Failed to get economy provider from Vault! Selling items from spawner will be disabled.");
                return false;
            }

            activeCurrencyProvider = "Vault (" + vaultEconomy.getName() + ")";
            plugin.getLogger().info("Successfully connected to Vault & Economy provider: " + vaultEconomy.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up Vault economy integration", e);
            return false;
        }
    }

    private boolean setupCoinsEngineEconomy() {
        // Check if CoinsEngine plugin is available
        if (plugin.getServer().getPluginManager().getPlugin("CoinsEngine") == null) {
            plugin.getLogger().warning("CoinsEngine not found! Selling items from spawner will be disabled.");
            return false;
        }

        try {
            // Try to retrieve the configured currency
            coinsEngineCurrency = CoinsEngineAPI.getCurrency(configuredCoinsEngineCurrency);

            if (coinsEngineCurrency == null) {
                plugin.getLogger().warning("Could not find CoinsEngine currency '" + configuredCoinsEngineCurrency + "'. Selling items from spawner will be disabled.");
                return false;
            }

            activeCurrencyProvider = "CoinsEngine (" + coinsEngineCurrency.getName() + ")";
            plugin.getLogger().info("Successfully connected to CoinsEngine with currency: " + coinsEngineCurrency.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up CoinsEngine economy integration", e);
            return false;
        }
    }

    public boolean deposit(double amount, OfflinePlayer player) {
        if (!currencyAvailable) {
            plugin.getLogger().warning("Currency not available for deposit operation.");
            return false;
        }

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Vault economy is not initialized.");
                return false;
            }
            return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
        }

        if (configuredCurrencyType.equalsIgnoreCase("COINSENGINE")) {
            if (coinsEngineCurrency == null) {
                plugin.getLogger().warning("CoinsEngine currency is not initialized.");
                return false;
            }

            CoinsEngineAPI.addBalance(player.getUniqueId(), coinsEngineCurrency, amount);
            return true;
        }

        plugin.getLogger().warning("Unsupported currency type during deposit: " + configuredCurrencyType);
        return false;
    }

    public void withdraw(double amount, OfflinePlayer player) {
        if (!currencyAvailable) {
            plugin.getLogger().warning("Currency not available for withdraw operation.");
            return;
        }

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Vault economy is not initialized.");
                return;
            }
            vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
            return;
        }

        if (configuredCurrencyType.equalsIgnoreCase("COINSENGINE")) {
            if (coinsEngineCurrency == null) {
                plugin.getLogger().warning("CoinsEngine currency is not initialized.");
                return;
            }

            CoinsEngineAPI.removeBalance(player.getUniqueId(), coinsEngineCurrency, amount);
            return;
        }

        plugin.getLogger().warning("Unsupported currency type during withdraw: " + configuredCurrencyType);
    }

    public void reload() {
        // Clean up existing connections
        cleanup();

        // Reload configuration and reinitialize
        loadConfiguration();
        setupCurrency();
    }

    public void cleanup() {
        vaultEconomy = null;
        coinsEngineCurrency = null;
        currencyAvailable = false;
        activeCurrencyProvider = "None";
    }
}