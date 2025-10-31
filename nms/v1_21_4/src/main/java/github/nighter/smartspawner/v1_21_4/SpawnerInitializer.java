package github.nighter.smartspawner.v1_21_4;
import org.bukkit.entity.EntityType;
import github.nighter.smartspawner.nms.SpawnerWrapper;
import java.util.Arrays;

public class SpawnerInitializer {
    public static void init() {
        SpawnerWrapper.SUPPORTED_MOBS = Arrays.stream(EntityType.values())
                .map(EntityType::name)
                .toList();
    }
}