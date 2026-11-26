import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemStacks extends JavaPlugin implements Listener {

    private final Map<Material, Integer> stackSizes = new HashMap<>();
    private File itemsFile;
    private FileConfiguration itemsConfig;
    private File languagesDir;
    private String language;
    private final Map<String, String> messages = new HashMap<>();
    private boolean debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        language = getConfig().getString("language", "en");
        loadLanguages();
        loadItemsConfig();
        getServer().getPluginManager().registerEvents(this, this);

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            LiteralCommandNode<CommandSourceStack> node = buildCommandTree();
            commands.register(node, "ItemStacks admin command", List.of("is"));
        });

        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String ansiLime = "\u001B[38;5;154m";
        String ansiReset = "\u001B[0m";

        getLogger().info(ansiLime + "ItemStacks enabled at " + time + ", version " + getPluginMeta().getVersion()
                + " " + getServer().getMinecraftVersion() + " " + getServer().getName() + ansiReset);
    }

    private LiteralCommandNode<CommandSourceStack> buildCommandTree() {
        return Commands.literal("itemstack")
                .requires(source -> source.getSender().isOp())
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            sendHelp(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            reloadConfig();
                            debug = getConfig().getBoolean("debug", false);
                            loadItemsConfig();
                            reloadMessages();
                            ctx.getSource().getSender().sendMessage(msg("reloaded"));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("language")
                        .then(Commands.argument("lang", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String remaining = builder.getRemaining().toLowerCase();
                                    File[] files = languagesDir.listFiles((dir, name) ->
                                            name.endsWith(".json") && !name.equals("RU_ru.json") && !name.equals("US_en.json"));
                                    if (files != null) {
                                        for (File f : files) {
                                            String name = f.getName().replace(".json", "");
                                            if (name.toLowerCase().startsWith(remaining)) {
                                                builder.suggest(name);
                                            }
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String lang = StringArgumentType.getString(ctx, "lang");
                                    File src = new File(languagesDir, lang + ".json");
                                    if (!src.exists()) {
                                        ctx.getSource().getSender().sendMessage(msg("language-not-found").replace("{lang}", lang));
                                        return 0;
                                    }
                                    language = lang;
                                    getConfig().set("language", lang);
                                    saveConfig();
                                    reloadMessages();
                                    ctx.getSource().getSender().sendMessage(msg("language-switched").replace("{lang}", lang));
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(Commands.literal("item")
                        .then(Commands.literal("additem")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemaining().toLowerCase();
                                            for (Material m : Material.values()) {
                                                if (m.isItem()) {
                                                    String name = m.name().toLowerCase();
                                                    if (name.startsWith(remaining)) {
                                                        builder.suggest(name);
                                                    }
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                                                .executes(ctx -> {
                                                    String itemName = StringArgumentType.getString(ctx, "item").toLowerCase().replace("minecraft.", "");
                                                    Material mat = Material.matchMaterial(itemName.toUpperCase());
                                                    if (mat == null) {
                                                        ctx.getSource().getSender().sendMessage(msg("item-not-found").replace("{item}", itemName));
                                                        return 0;
                                                    }
                                                    int count = IntegerArgumentType.getInteger(ctx, "count");
                                                    itemsConfig.set(mat.name().toLowerCase(), count);
                                                    try {
                                                        itemsConfig.save(itemsFile);
                                                    } catch (IOException e) {
                                                        ctx.getSource().getSender().sendMessage(msg("save-failed"));
                                                        return 0;
                                                    }
                                                    stackSizes.put(mat, count);
                                                    ctx.getSource().getSender().sendMessage(msg("item-added").replace("{item}", itemName).replace("{count}", String.valueOf(count)));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("delitem")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemaining().toLowerCase();
                                            for (Material m : stackSizes.keySet()) {
                                                String name = m.name().toLowerCase();
                                                if (name.startsWith(remaining)) {
                                                    builder.suggest(name);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String itemName = StringArgumentType.getString(ctx, "item").toLowerCase().replace("minecraft.", "");
                                            Material mat = Material.matchMaterial(itemName.toUpperCase());
                                            if (mat == null) {
                                                ctx.getSource().getSender().sendMessage(msg("item-not-found").replace("{item}", itemName));
                                                return 0;
                                            }
                                            itemsConfig.set(mat.name().toLowerCase(), null);
                                            try {
                                                itemsConfig.save(itemsFile);
                                            } catch (IOException e) {
                                                ctx.getSource().getSender().sendMessage(msg("save-failed"));
                                                return 0;
                                            }
                                            stackSizes.remove(mat);
                                            ctx.getSource().getSender().sendMessage(msg("item-removed").replace("{item}", itemName));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("status")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            String remaining = builder.getRemaining().toLowerCase();
                                            for (Material m : stackSizes.keySet()) {
                                                String name = m.name().toLowerCase();
                                                if (name.startsWith(remaining)) {
                                                    builder.suggest(name);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            String itemName = StringArgumentType.getString(ctx, "item").toLowerCase().replace("minecraft.", "");
                                            Material mat = Material.matchMaterial(itemName.toUpperCase());
                                            if (mat == null) {
                                                ctx.getSource().getSender().sendMessage(msg("item-not-found").replace("{item}", itemName));
                                                return 0;
                                            }
                                            Integer size = stackSizes.get(mat);
                                            if (size == null) {
                                                ctx.getSource().getSender().sendMessage(msg("item-status-default").replace("{item}", itemName));
                                            } else {
                                                ctx.getSource().getSender().sendMessage(msg("item-status").replace("{item}", itemName).replace("{count}", String.valueOf(size)));
                                            }
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .build();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(msg("help-header"));
        sender.sendMessage(msg("help-reload"));
        sender.sendMessage(msg("help-additem"));
        sender.sendMessage(msg("help-delitem"));
        sender.sendMessage(msg("help-status"));
        sender.sendMessage(msg("help-language"));
    }

    private void loadLanguages() {
        languagesDir = new File(getDataFolder(), "languages");
        if (!languagesDir.exists()) {
            languagesDir.mkdirs();
        }
        saveResourceIfNotExists("languages/RU_ru.json");
        saveResourceIfNotExists("languages/US_en.json");
        reloadMessages();
    }

    private void saveResourceIfNotExists(String path) {
        File f = new File(getDataFolder(), path);
        if (!f.exists()) {
            saveResource(path, false);
        }
    }

    private void reloadMessages() {
        messages.clear();
        File langFile = new File(languagesDir, language + ".json");
        if (!langFile.exists()) {
            langFile = new File(languagesDir, "US_en.json");
        }
        if (!langFile.exists()) return;
        try {
            String json = Files.readString(langFile.toPath());
            json = json.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
            for (String pair : json.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("^\"|\"$", "");
                    messages.put(k, v);
                }
            }
        } catch (IOException e) {
            getLogger().warning("Could not load language file: " + e.getMessage());
        }
    }

    private String msg(String key) {
        return messages.getOrDefault(key, key);
    }

    private void loadItemsConfig() {
        itemsFile = new File(getDataFolder(), "ItemStacks.yml");
        if (!itemsFile.exists()) {
            getDataFolder().mkdirs();
            try {
                itemsFile.createNewFile();
                Files.writeString(itemsFile.toPath(),
                        "#         Add items\n\n# minecraft.tnt = 99,\n");
            } catch (IOException e) {
                getLogger().severe("Could not create ItemStacks.yml: " + e.getMessage());
                return;
            }
        }

        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        stackSizes.clear();

        for (String key : itemsConfig.getKeys(false)) {
            String materialName = key.toUpperCase().replace('.', '_').replace("MINECRAFT_", "");
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                getLogger().warning("Unknown material: " + key);
                continue;
            }
            int size = itemsConfig.getInt(key, -1);
            if (size > 0 && size <= 99) {
                stackSizes.put(material, size);
            } else {
                getLogger().warning("Invalid stack size for " + key + ": " + size);
            }
        }

        if (debug) {
            getLogger().info("Loaded " + stackSizes.size() + " item stack overrides.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("update-on-join", true)) return;
        updateInventory(event.getPlayer());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (!getConfig().getBoolean("update-on-open", true)) return;
        if (event.getPlayer() instanceof Player p) {
            updateInventory(p);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!getConfig().getBoolean("update-on-close", true)) return;
        if (event.getPlayer() instanceof Player p) {
            updateInventory(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!getConfig().getBoolean("update-on-pickup", true)) return;
        if (event.getEntity() instanceof Player) {
            ItemStack item = event.getItem().getItemStack();
            applyStackSize(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!getConfig().getBoolean("update-on-click", true)) return;
        if (event.getWhoClicked() instanceof Player p) {
            applyStackSize(event.getCurrentItem());
            applyStackSize(event.getCursor());
            p.getScheduler().run(this, (task) -> updateInventory(p), null);
        }
    }

    private void updateInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            applyStackSize(item);
        }
    }

    private void applyStackSize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Integer targetSize = stackSizes.get(item.getType());
        if (targetSize == null) return;

        if (!item.hasData(DataComponentTypes.MAX_STACK_SIZE)) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, targetSize);
        } else if (item.getData(DataComponentTypes.MAX_STACK_SIZE) != targetSize) {
            item.setData(DataComponentTypes.MAX_STACK_SIZE, targetSize);
        }
    }
}