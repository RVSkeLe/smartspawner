package github.nighter.smartspawner.commands.give;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.nms.SpawnerWrapper;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.List;

@NullMarked
public class GiveSubCommand extends BaseSubCommand {
    private final SpawnerItemFactory spawnerItemFactory;
    private final List<String> supportedMobs;
    private static final int MAX_AMOUNT = 6400;

    public GiveSubCommand(SmartSpawner plugin) {
        super(plugin);
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
        this.supportedMobs = SpawnerWrapper.SUPPORTED_MOBS;
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.give";
    }

    @Override
    public String getDescription() {
        return "Give spawners to players";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());
        builder.requires(source -> hasPermission(source.getSender()));

        // Add subcommands for regular and vanilla spawners
        builder.then(buildRegularGiveCommand());
        builder.then(buildVanillaGiveCommand());

        return builder;
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildRegularGiveCommand() {
        return Commands.literal("spawner")
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("mobType", StringArgumentType.word())
                                .suggests(createMobSuggestions())
                                .executes(context -> executeGive(context, false, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                        .executes(context -> executeGive(context, false,
                                                IntegerArgumentType.getInteger(context, "amount"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildVanillaGiveCommand() {
        return Commands.literal("vanilla_spawner")
                .then(Commands.argument("player", ArgumentTypes.player())
                        .then(Commands.argument("mobType", StringArgumentType.word())
                                .suggests(createMobSuggestions())
                                .executes(context -> executeGive(context, true, 1))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, MAX_AMOUNT))
                                        .executes(context -> executeGive(context, true,
                                                IntegerArgumentType.getInteger(context, "amount"))))));
    }

    private SuggestionProvider<CommandSourceStack> createMobSuggestions() {
        return (context, builder) -> {
            String input = builder.getRemaining().toLowerCase();
            supportedMobs.stream()
                    .map(String::toLowerCase) // Convert to lowercase for suggestions
                    .filter(mob -> mob.startsWith(input))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        return 0;
    }

    private int executeGive(CommandContext<CommandSourceStack> context, boolean isVanilla, int amount) {
        CommandSender sender = context.getSource().getSender();
        
        // Log command execution
        logCommandExecution(context);

        try {
            // Get the player selector and resolve it
            var playerSelector = context.getArgument("player", io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver.class);
            List<Player> players = playerSelector.resolve(context.getSource());

            if (players.isEmpty()) {
                plugin.getMessageService().sendMessage(sender, "command_give_player_not_found");
                return 0;
            }

            Player target = players.get(0); // Get the first (and typically only) player from the selector
            String mobType = StringArgumentType.getString(context, "mobType");

            // Validate mob type (case insensitive check)
            if (!supportedMobs.contains(mobType.toUpperCase())) {
                plugin.getMessageService().sendMessage(sender, "command_give_invalid_mob_type");
                return 0;
            }

            EntityType entityType = EntityType.valueOf(mobType.toUpperCase());
            ItemStack spawnerItem;

            if (isVanilla) {
                // Use the correct method for vanilla spawners
                spawnerItem = spawnerItemFactory.createVanillaSpawnerItem(entityType, amount);
            } else {
                // Use spawner item factory for smart spawners
                spawnerItem = spawnerItemFactory.createSmartSpawnerItem(entityType, amount);
            }

            // Give the item to the player
            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItem(target.getLocation(), spawnerItem);
                plugin.getMessageService().sendMessage(target, "command_give_inventory_full");
            } else {
                target.getInventory().addItem(spawnerItem);
            }

            // Play sound
            target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            // Get formatted entity names for placeholders
            String entityName = plugin.getLanguageManager().getFormattedMobName(entityType);
            String smallCapsEntityName = plugin.getLanguageManager().getSmallCaps(entityName);

            // Create placeholders for sender message
            HashMap<String, String> senderPlaceholders = new HashMap<>();
            senderPlaceholders.put("player", target.getName());
            senderPlaceholders.put("entity", entityName);
            senderPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);
            senderPlaceholders.put("amount", String.valueOf(amount));

            // Create placeholders for target message
            HashMap<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("amount", String.valueOf(amount));
            targetPlaceholders.put("entity", entityName);
            targetPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);

            // Send messages with placeholders
            String messageKey = "command_give_spawner_";
            plugin.getMessageService().sendMessage(sender, messageKey + "given", senderPlaceholders);
            plugin.getMessageService().sendMessage(target, messageKey + "received", targetPlaceholders);

            return 1;
        } catch (Exception e) {
            plugin.getLogger().severe("Error executing give command: " + e.getMessage());
            return 0;
        }
    }
}