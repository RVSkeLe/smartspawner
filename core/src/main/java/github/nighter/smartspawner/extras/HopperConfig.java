package github.nighter.smartspawner.extras;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;

public class HopperConfig {

    @Getter
    private final boolean hopperEnabled;
    @Getter
    private final int stackPerTransfer;

    public HopperConfig(SmartSpawner plugin) {
        this.hopperEnabled = plugin.getConfig().getBoolean("hopper.enabled", false);
        this.stackPerTransfer = plugin.getConfig().getInt("hopper.stack_per_transfer", 5);
    }
}
