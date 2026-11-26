import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class ItemStacks extends JavaPlugin implements Listener, TabCompleter {

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
        getCommand("itemstack").setTabCompleter(this);

        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        getLogger().info("ItemStacks enabled at " + time + ", version " + getDescription().getVersion()
                + " " + Bukkit.getBukkitVersion() + " " + getServer().getName());
    }

    private void loadLanguages() {
        languagesDir = new File(getDataFolder(), "languages");
        if (!languagesDir.exists()) languagesDir.mkdirs();
        saveResourceIfNotExists("languages/RU_ru.json");
        saveResourceIfNotExists("languages/US_en.json");
        reloadMessages();
    }

    private void saveResourceIfNotExists(String path) {
        File f = new File(getDataFolder(), path);
        if (!f.exists()) saveResource(path, false);
    }

    private void reloadMessages() {
        messages.clear();
        File langFile = new File(languagesDir, language + ".json");
        if (!langFile.exists()) langFile = new File(languagesDir, "US_en.json");
        if (!langFile.exists()) return;
        try {
            String json = Files.readString(langFile.toPath()).trim();
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
                Files.writeString(itemsFile.toPath(), "#         Add items\n\n# minecraft.tnt = 99,\n");
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
            if (size > 0 && size <= 99) stackSizes.put(material, size);
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
        if (event.getPlayer() instanceof Player p) updateInventory(p);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!getConfig().getBoolean("update-on-close", true)) return;
        if (event.getPlayer() instanceof Player p) updateInventory(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!getConfig().getBoolean("update-on-pickup", true)) return;
        if (event.getEntity() instanceof Player) applyStackSize(event.getItem().getItemStack());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!getConfig().getBoolean("update-on-click", true)) return;
        if (event.getWhoClicked() instanceof Player p) {
            applyStackSize(event.getCurrentItem());
            applyStackSize(event.getCursor());
            getServer().getScheduler().runTask(this, () -> updateInventory(p));
        }
    }

    private void updateInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) applyStackSize(item);
    }

    private void applyStackSize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        Integer targetSize = stackSizes.get(item.getType());
        if (targetSize == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setMaxStackSize(targetSize);
            item.setItemMeta(meta);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp()) {
            sender.sendMessage(msg("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            debug = getConfig().getBoolean("debug", false);
            loadItemsConfig();
            reloadMessages();
            p.sendMessage(msg("reloaded"));
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(p);
            return true;
        }
        if (args[0].equalsIgnoreCase("language")) {
            if (args.length < 2) {
                p.sendMessage(msg("unknown-args"));
                return true;
            }
            String lang = args[1];
            File src = new File(languagesDir, lang + ".json");
            if (!src.exists()) {
                p.sendMessage(msg("language-not-found").replace("{lang}", lang));
                return true;
            }
            language = lang;
            getConfig().set("language", lang);
            saveConfig();
            reloadMessages();
            p.sendMessage(msg("language-switched").replace("{lang}", lang));
            return true;
        }
        if (args[0].equalsIgnoreCase("item")) {
            if (args.length < 2) {
                p.sendMessage(msg("unknown-args"));
                return true;
            }
            switch (args[1].toLowerCase()) {
                case "additem" -> {
                    if (args.length < 4) {
                        p.sendMessage(msg("unknown-args"));
                        return true;
                    }
                    String itemName = args[2].toLowerCase().replace("minecraft.", "").replace('.', '_');
                    Material mat = Material.matchMaterial(itemName.toUpperCase());
                    if (mat == null) {
                        p.sendMessage(msg("item-not-found").replace("{item}", args[2]));
                        return true;
                    }
                    int count;
                    try {
                        count = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        p.sendMessage(msg("invalid-number"));
                        return true;
                    }
                    if (count < 1 || count > 99) {
                        p.sendMessage(msg("invalid-range"));
                        return true;
                    }
                    itemsConfig.set(mat.name().toLowerCase(), count);
                    try {
                        itemsConfig.save(itemsFile);
                    } catch (IOException e) {
                        p.sendMessage(msg("save-failed"));
                        return true;
                    }
                    stackSizes.put(mat, count);
                    p.sendMessage(msg("item-added").replace("{item}", args[2]).replace("{count}", String.valueOf(count)));
                }
                case "delitem", "remitem", "reset" -> {
                    if (args.length < 3) {
                        p.sendMessage(msg("unknown-args"));
                        return true;
                    }
                    String itemName = args[2].toLowerCase().replace("minecraft.", "").replace('.', '_');
                    Material mat = Material.matchMaterial(itemName.toUpperCase());
                    if (mat == null) {
                        p.sendMessage(msg("item-not-found").replace("{item}", args[2]));
                        return true;
                    }
                    itemsConfig.set(mat.name().toLowerCase(), null);
                    try {
                        itemsConfig.save(itemsFile);
                    } catch (IOException e) {
                        p.sendMessage(msg("save-failed"));
                        return true;
                    }
                    stackSizes.remove(mat);
                    p.sendMessage(msg("item-removed").replace("{item}", args[2]));
                }
                case "status" -> {
                    if (args.length < 3) {
                        p.sendMessage(msg("unknown-args"));
                        return true;
                    }
                    String itemName = args[2].toLowerCase().replace("minecraft.", "").replace('.', '_');
                    Material mat = Material.matchMaterial(itemName.toUpperCase());
                    if (mat == null) {
                        p.sendMessage(msg("item-not-found").replace("{item}", args[2]));
                        return true;
                    }
                    Integer size = stackSizes.get(mat);
                    if (size == null) p.sendMessage(msg("item-status-default").replace("{item}", args[2]));
                    else p.sendMessage(msg("item-status").replace("{item}", args[2]).replace("{count}", String.valueOf(size)));
                }
                default -> p.sendMessage(msg("unknown-args"));
            }
            return true;
        }
        p.sendMessage(msg("unknown-args"));
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(msg("help-header"));
        p.sendMessage(msg("help-reload"));
        p.sendMessage(msg("help-additem"));
        p.sendMessage(msg("help-delitem"));
        p.sendMessage(msg("help-status"));
        p.sendMessage(msg("help-language"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp()) return Collections.emptyList();
        if (args.length == 1) return filter(List.of("help", "reload", "item", "language"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("item")) return filter(List.of("additem", "delitem", "remitem", "reset", "status"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            File[] files = languagesDir.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("RU_ru.json") && !name.equals("US_en.json"));
            if (files == null) return Collections.emptyList();
            List<String> names = new ArrayList<>();
            for (File f : files) names.add(f.getName().replace(".json", ""));
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("item") && (args[1].equalsIgnoreCase("additem") || args[1].equalsIgnoreCase("delitem") || args[1].equalsIgnoreCase("remitem") || args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("status"))) {
            List<String> mats = new ArrayList<>();
            for (Material m : Material.values()) if (m.isItem()) mats.add(m.name().toLowerCase());
            return filter(mats, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}