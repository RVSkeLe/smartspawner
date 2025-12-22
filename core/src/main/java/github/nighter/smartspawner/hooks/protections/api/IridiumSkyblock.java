package github.nighter.smartspawner.hooks.protections.api;

import com.iridium.iridiumskyblock.api.IridiumSkyblockAPI;
import com.iridium.iridiumskyblock.database.Island;
import com.iridium.iridiumskyblock.database.User;
import com.iridium.iridiumskyblock.dependencies.iridiumcore.Item;
import com.iridium.iridiumskyblock.dependencies.iridiumteams.Permission;
import com.iridium.iridiumskyblock.dependencies.iridiumteams.PermissionType;
import com.iridium.iridiumskyblock.dependencies.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;

public class IridiumSkyblock {
    public static void init() {
        Permission spawnerOpenMenuPermission = new Permission(
                new Item(XMaterial.SPAWNER, 38, 1, "&bSpawner Menu Permission", Arrays.asList(
                        "&7Permission for permit users to open spawners menu.",
                        "", "&b&lPermission",
                        "%permission%")
                ), 1, 1);
        Permission spawnerStackPermission = new Permission(
                new Item(XMaterial.SPAWNER, 39, 1, "&7Spawner Stack Permission", Arrays.asList(
                        "&7Permission for permit users to stack spawners.",
                        "", "&b&lPermission",
                        "%permission%")
                ), 1, 1);
        IridiumSkyblockAPI.getInstance().addPermission(spawnerOpenMenuPermission, "SpawnerOpenMenuPermission");
        IridiumSkyblockAPI.getInstance().addPermission(spawnerStackPermission, "SpawnerStackPermission");
    }

    public static boolean canPlayerStackBlock(@NotNull Player player, @NotNull Location location) {
        return checkPermission(player, location, "SpawnerStackPermission");
    }

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        return checkPermission(player, location, "SpawnerOpenMenuPermission");
    }

    private static boolean checkPermission(@NotNull Player player, @NotNull Location location, String permissionKey) {
        User user = IridiumSkyblockAPI.getInstance().getUser(player);
        Optional<Island> island = IridiumSkyblockAPI.getInstance().getIslandViaLocation(location);
        Optional<Permission> permission = IridiumSkyblockAPI.getInstance().getPermissions(permissionKey);
        if(user == null || island.isEmpty() || permission.isEmpty()) return true;
        return IridiumSkyblockAPI.getInstance().getIslandPermission(island.get(), user, permission.get(), permissionKey);
    }
}
