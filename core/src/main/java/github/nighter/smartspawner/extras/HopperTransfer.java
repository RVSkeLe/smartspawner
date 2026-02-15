package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.data.SpawnerManager;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.utils.BlockPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class HopperTransfer {

    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerGuiViewManager guiManager;

    public HopperTransfer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.guiManager = plugin.getSpawnerGuiViewManager();
    }

    public void process(BlockPos hopperPos) {

        Location hopperLoc = hopperPos.toLocation();
        Block hopperBlock = hopperLoc.getBlock();

        if (hopperBlock.getType() != Material.HOPPER) return;

        Block spawnerBlock = hopperBlock.getRelative(BlockFace.UP);
        if (spawnerBlock.getType() != Material.SPAWNER) return;

        transferItems(hopperLoc, spawnerBlock.getLocation());
    }

    private void transferItems(Location hopperLoc, Location spawnerLoc) {

        SpawnerData spawner = spawnerManager.getSpawnerByLocation(spawnerLoc);
        if (spawner == null) return;

        VirtualInventory virtualInv = spawner.getVirtualInventory();
        if (virtualInv == null) return;

        Hopper hopper = (Hopper) hopperLoc.getBlock().getState(false);
        if (hopper == null) return;

        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
        if (displayItems == null || displayItems.isEmpty()) return;

        Inventory hopperInv = hopper.getInventory();

        int maxTransfers = plugin.getConfig().getInt("hopper.stack_per_transfer", 5);
        int transferred = 0;

        List<ItemStack> removed = new ArrayList<>();

        for (ItemStack item : displayItems.values()) {
            if (transferred >= maxTransfers) break;
            if (item == null || item.getType() == Material.AIR) continue;

            HashMap<Integer, ItemStack> leftovers = hopperInv.addItem(item.clone());

            if (leftovers.isEmpty()) {
                removed.add(item.clone());
                transferred++;
            }
        }

        if (!removed.isEmpty()) {
            spawner.removeItemsAndUpdateSellValue(removed);
            guiManager.updateSpawnerMenuViewers(spawner);
        }
    }
}
