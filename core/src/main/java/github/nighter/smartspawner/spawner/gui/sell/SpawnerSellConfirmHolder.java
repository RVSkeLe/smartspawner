package github.nighter.smartspawner.spawner.gui.sell;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import lombok.Getter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

@Getter
public class SpawnerSellConfirmHolder implements InventoryHolder {
    private final SpawnerData spawnerData;
    private final SpawnerSellConfirmUI.PreviousGui previousGui;
    private final boolean collectExp;

    public SpawnerSellConfirmHolder(SpawnerData spawnerData, SpawnerSellConfirmUI.PreviousGui previousGui, boolean collectExp) {
        this.spawnerData = spawnerData;
        this.previousGui = previousGui;
        this.collectExp = collectExp;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}

