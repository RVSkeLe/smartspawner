package github.nighter.smartspawner.hooks.economy.shops.providers.excellentshop;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import su.nightexpress.excellentshop.ShopPlugin;
import su.nightexpress.excellentshop.api.product.TradeType;
import su.nightexpress.excellentshop.virtualshop.VirtualShopModule;
import su.nightexpress.excellentshop.virtualshop.product.VirtualProduct;

public final class ExcellentShopProvider implements ShopProvider {

    private final SmartSpawner plugin;

    public ExcellentShopProvider(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPluginName() {
        return "ExcellentShop";
    }

    private VirtualShopModule getVirtualShop() {
        Plugin installed = plugin.getServer()
                .getPluginManager()
                .getPlugin("ExcellentShop");

        if (!(installed instanceof ShopPlugin shopPlugin) || !shopPlugin.isEnabled()) {
            return null;
        }

        return shopPlugin.getModuleRegistry().byType(VirtualShopModule.class).orElse(null);
    }

    @Override
    public boolean isAvailable() {
        try {
            return getVirtualShop() != null;
        } catch (RuntimeException | LinkageError ex) {
            plugin.getLogger().warning("Error initializing ExcellentShop integration: " + ex.getMessage());
            return false;
        }
    }

    @Override
    public double getSellPrice(Material material) {
        if (material == null || material == Material.AIR) {
            return 0D;
        }

        try {
            VirtualShopModule shop = getVirtualShop();
            if (shop == null) return 0D;

            VirtualProduct product = shop.getBestProductFor(ItemStack.of(material), TradeType.SELL);

            if (product == null || !product.isValid() || !product.isSellable()) {
                return 0D;
            }

            int unitSize = product.getUnitSize();
            if (unitSize <= 0) {
                return 0D;
            }

            double price = product.getFinalPrice(TradeType.SELL) / unitSize;
            return Double.isFinite(price) && price > 0D ? price : 0D;
        } catch (Exception ex) {
            plugin.getLogger().warning("ExcellentShop price lookup failed: " + ex.getMessage());
            return 0D;
        }
    }
}
