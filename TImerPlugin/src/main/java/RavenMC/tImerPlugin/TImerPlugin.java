package RavenMC.tImerPlugin;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TImerPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private BossBar timerBar;
    private double timeRemainingInDays;
    private double MAX_DAYS;
    private int maxAddDays;
    private int maxRemoveDays;
    private double minDaysToRemove;
    private final long DAY_IN_SECONDS = 86400; // Seconds in a day
    private final Map<UUID, UUID> recentKillers = new HashMap<>();
    private String GUI_TITLE;

    // Special items cooldown trackers
    private final Map<UUID, Long> timeControllerCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> timeMaceCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> remainingDashes = new ConcurrentHashMap<>();

    // Lives system
    private final int INITIAL_LIVES = 10;
    private final int REVIVE_LIVES = 8;
    private final Map<UUID, Integer> playerLives = new ConcurrentHashMap<>();
    private File livesFile;
    private YamlConfiguration livesConfig;
    private String REVIVE_GUI_TITLE = ChatColor.RED + "Choose Player to Revive";

    // Revival tracking system
    private final Map<String, UUID> revivedPlayerTracking = new ConcurrentHashMap<>();

    // Config message strings
    private String msgKillerReceivedItem;
    private String msgTimerZero;
    private String msgPlayerAddedTime;
    private String msgPlayerRemovedTime;
    private String msgTimerAtMax;
    private String msgTimerAtMin;
    private String msgNotRecentKiller;
    private String msgPlayerRevivalCoords;
    private String itemName;
    private List<String> itemLore;

    @Override
    public void onEnable() {
        try {
            // Log plugin startup attempt
            System.out.println("[TimerPlugin] Starting plugin initialization...");

            // Create data folder if it doesn't exist
            if (!getDataFolder().exists()) {
                getDataFolder().mkdir();
                System.out.println("[TimerPlugin] Created plugin data folder");
            }

            // Register events
            getServer().getPluginManager().registerEvents(this, this);
            System.out.println("[TimerPlugin] Registered events");

            // Register commands
            getCommand("timerplugin").setExecutor(this);
            getCommand("lives").setExecutor(this); // Register the new lives command
            System.out.println("[TimerPlugin] Registered commands");

            // Create default config
            saveDefaultConfig();
            System.out.println("[TimerPlugin] Created default config");

            // Load configuration
            loadConfigValues();
            System.out.println("[TimerPlugin] Loaded configuration values");

            // Initialize lives system
            initLivesSystem();
            System.out.println("[TimerPlugin] Initialized lives system");

            // Create boss bar for timer display
            timerBar = Bukkit.createBossBar(
                    formatTimeDisplay(timeRemainingInDays),
                    getBarColor(),
                    getBarStyle()
            );
            System.out.println("[TimerPlugin] Created boss bar");

            // Register custom recipes
            registerCustomRecipes();
            System.out.println("[TimerPlugin] Registered custom recipes");

            // Start timer countdown
            startTimerCountdown();
            System.out.println("[TimerPlugin] Started timer countdown");

            // Log that the plugin has been enabled
            getLogger().info("TimerPlugin has been enabled!");
            System.out.println("[TimerPlugin] Plugin has been fully enabled!");
        } catch (Exception e) {
            getLogger().severe("Error enabling TimerPlugin: " + e.getMessage());
            System.out.println("[TimerPlugin] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initLivesSystem() {
        // Create lives data file if it doesn't exist
        livesFile = new File(getDataFolder(), "lives.yml");
        if (!livesFile.exists()) {
            try {
                livesFile.createNewFile();
                System.out.println("[TimerPlugin] Created lives.yml file");
            } catch (IOException e) {
                getLogger().severe("Could not create lives.yml file!");
                e.printStackTrace();
            }
        }

        // Load lives data
        livesConfig = YamlConfiguration.loadConfiguration(livesFile);

        // Load banned players for lives system
        if (livesConfig.contains("banned-players")) {
            for (String uuidStr : livesConfig.getStringList("banned-players")) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    // Ban the player if they're not already banned
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && !player.isBanned()) {
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                                player.getName(),
                                ChatColor.RED + "You ran out of lives!",
                                null,
                                "Timer Plugin"
                        );
                        player.kickPlayer(ChatColor.RED + "You ran out of lives!");
                    }
                } catch (IllegalArgumentException e) {
                    getLogger().warning("Invalid UUID in banned-players list: " + uuidStr);
                }
            }
        }
    }

    private void saveLivesData() {
        try {
            livesConfig.save(livesFile);
        } catch (IOException e) {
            getLogger().severe("Could not save lives.yml file!");
            e.printStackTrace();
        }
    }

    private int getPlayerLives(UUID uuid) {
        // Check cached lives first
        if (playerLives.containsKey(uuid)) {
            return playerLives.get(uuid);
        }

        // Load from config if not cached
        String uuidStr = uuid.toString();
        if (livesConfig.contains("player-lives." + uuidStr)) {
            int lives = livesConfig.getInt("player-lives." + uuidStr);
            playerLives.put(uuid, lives);
            return lives;
        }

        // New player, set initial lives
        playerLives.put(uuid, INITIAL_LIVES);
        livesConfig.set("player-lives." + uuidStr, INITIAL_LIVES);
        saveLivesData();
        return INITIAL_LIVES;
    }

    private void setPlayerLives(UUID uuid, int lives) {
        // Update cache and config
        playerLives.put(uuid, lives);
        livesConfig.set("player-lives." + uuid.toString(), lives);

        // Check if player should be banned
        if (lives <= 0) {
            banPlayer(uuid);
        }

        saveLivesData();
    }

    private void banPlayer(UUID uuid) {
        // Add to banned list
        List<String> bannedPlayers = livesConfig.getStringList("banned-players");
        if (!bannedPlayers.contains(uuid.toString())) {
            bannedPlayers.add(uuid.toString());
            livesConfig.set("banned-players", bannedPlayers);
        }

        // Actually ban the player
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(
                    player.getName(),
                    ChatColor.RED + "You ran out of lives!",
                    null,
                    "Timer Plugin"
            );
            player.kickPlayer(ChatColor.RED + "You ran out of lives!");
        }

        saveLivesData();
    }

    private void revivePlayer(String playerName, Player reviver) {
        // Get player UUID from name
        for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
            if (entry.getTarget().equalsIgnoreCase(playerName)) {
                // Unban the player
                Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(playerName);

                // Find their UUID
                for (org.bukkit.OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                    if (offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(playerName)) {
                        // Remove from banned list
                        List<String> bannedPlayers = livesConfig.getStringList("banned-players");
                        bannedPlayers.remove(offlinePlayer.getUniqueId().toString());
                        livesConfig.set("banned-players", bannedPlayers);

                        // Set to revive lives amount
                        setPlayerLives(offlinePlayer.getUniqueId(), REVIVE_LIVES);

                        // Store the reviver's UUID with the revived player's name
                        if (reviver != null) {
                            revivedPlayerTracking.put(playerName.toLowerCase(), reviver.getUniqueId());
                        }

                        Bukkit.broadcastMessage(ChatColor.GREEN + playerName + " has been revived with " + REVIVE_LIVES + " lives!");
                        return;
                    }
                }

                // If UUID not found but player was unbanned
                // Store the reviver's UUID with the revived player's name
                if (reviver != null) {
                    revivedPlayerTracking.put(playerName.toLowerCase(), reviver.getUniqueId());
                }

                Bukkit.broadcastMessage(ChatColor.GREEN + playerName + " has been revived!");
                return;
            }
        }
    }

    private void registerCustomRecipes() {
        try {
            // Time Controller Recipe
            ItemStack timeController = createTimeController();
            NamespacedKey timeControllerKey = new NamespacedKey(this, "time_controller");
            ShapedRecipe timeControllerRecipe = new ShapedRecipe(timeControllerKey, timeController);

            timeControllerRecipe.shape("DBD", "NEN", "DTD");
            timeControllerRecipe.setIngredient('D', Material.DIAMOND);
            timeControllerRecipe.setIngredient('B', Material.HEART_OF_THE_SEA); // This is any Heart of the Sea, but we'll validate in the crafting event
            timeControllerRecipe.setIngredient('E', Material.DRAGON_EGG);
            timeControllerRecipe.setIngredient('N', Material.NETHERITE_INGOT);
            timeControllerRecipe.setIngredient('T', Material.TOTEM_OF_UNDYING);

            Bukkit.addRecipe(timeControllerRecipe);

            // Time Mace Recipe
            ItemStack timeMace = createTimeMace();
            NamespacedKey timeMaceKey = new NamespacedKey(this, "time_mace");
            ShapedRecipe timeMaceRecipe = new ShapedRecipe(timeMaceKey, timeMace);

            timeMaceRecipe.shape(" B ", "BAB", " B ");
            timeMaceRecipe.setIngredient('B', Material.HEART_OF_THE_SEA);
            timeMaceRecipe.setIngredient('A', Material.MACE);

            Bukkit.addRecipe(timeMaceRecipe);

            // Revive Item Recipe
            ItemStack reviveItem = createReviveItem();
            NamespacedKey reviveKey = new NamespacedKey(this, "revive_item");
            ShapedRecipe reviveRecipe = new ShapedRecipe(reviveKey, reviveItem);

            reviveRecipe.shape("DBD", "NSN", "DBD");
            reviveRecipe.setIngredient('D', Material.DIAMOND_BLOCK);
            reviveRecipe.setIngredient('B', Material.HEART_OF_THE_SEA); // This is any Heart of the Sea, but we'll validate in the crafting event
            reviveRecipe.setIngredient('N', Material.NETHERITE_INGOT);
            reviveRecipe.setIngredient('S', Material.NETHER_STAR);

            Bukkit.addRecipe(reviveRecipe);

            System.out.println("[TimerPlugin] Registered all custom recipes successfully");
        } catch (Exception e) {
            getLogger().severe("Error registering recipes: " + e.getMessage());
            System.out.println("[TimerPlugin] Error registering recipes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ItemStack createTimeController() {
        ItemStack timeController = new ItemStack(Material.CLOCK, 1);
        ItemMeta meta = timeController.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_PURPLE + "Time Controller");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A mystical device that can freeze time");
        lore.add(ChatColor.YELLOW + "Right-click to freeze players around you for 3 seconds");
        lore.add(ChatColor.RED + "Cooldown: 30 seconds");

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setCustomModelData(1001); // Custom model data for resource packs

        timeController.setItemMeta(meta);
        return timeController;
    }

    private ItemStack createTimeMace() {
        ItemStack timeMace = new ItemStack(Material.MACE, 1);
        ItemMeta meta = timeMace.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_PURPLE + "Time Mace");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A powerful weapon forged from time itself");
        lore.add(ChatColor.YELLOW + "Right-click to dash forward (3 charges)");
        lore.add(ChatColor.YELLOW + "Shift + Right-click to create a time explosion");
        lore.add(ChatColor.RED + "Cooldown: 2 minutes");
        lore.add(ChatColor.RED + "Warning: You can take fall damage!");

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setCustomModelData(1002); // Custom model data for resource packs

        // Add enchantment glow effect for 1.21.1+
        try {
            // Try to apply an enchantment and hide it to give the item a glow effect
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
        } catch (Exception e) {
            getLogger().warning("Could not apply glow effect to Time Mace: " + e.getMessage());
        }

        timeMace.setItemMeta(meta);
        return timeMace;
    }

    private ItemStack createReviveItem() {
        ItemStack reviveItem = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
        ItemMeta meta = reviveItem.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "Revive Totem");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A sacred artifact with the power to revive the fallen");
        lore.add(ChatColor.YELLOW + "Place down to choose a player to revive");
        lore.add(ChatColor.AQUA + "Revived players return with " + REVIVE_LIVES + " lives");

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.setCustomModelData(1003); // Custom model data for resource packs

        reviveItem.setItemMeta(meta);
        return reviveItem;
    }

    private void loadConfigValues() {
        try {
            FileConfiguration config = getConfig();

            // Load timer settings
            timeRemainingInDays = config.getDouble("timer.initial-days", 30.0);
            MAX_DAYS = config.getDouble("timer.max-days", 30.0);
            maxAddDays = config.getInt("timer.modification.max-add", 2);
            maxRemoveDays = config.getInt("timer.modification.max-remove", 2);
            minDaysToRemove = config.getDouble("timer.modification.min-days-to-remove", 2.0);

            // Load messages
            msgKillerReceivedItem = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.killer-received-item", "&aYou've killed %victim%! Use the Heart of the Sea to modify the timer."));

            msgTimerZero = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.timer-zero", "&cThe timer has reached zero!"));

            msgPlayerAddedTime = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.player-added-time", "&a%player% added %days% day(s) to the timer!"));

            msgPlayerRemovedTime = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.player-removed-time", "&c%player% removed %days% day(s) from the timer!"));

            msgTimerAtMax = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.timer-at-max", "&cThe timer cannot exceed %max% days!"));

            msgTimerAtMin = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.timer-at-min", "&cThe timer cannot go below 0 days!"));

            msgNotRecentKiller = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.not-recent-killer", "&cYou can only use this item after killing a player."));

            msgPlayerRevivalCoords = ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages.player-revival-coords", "&aPlayer %player% has respawned at coordinates: &e%x%, %y%, %z%"));

            // Load item settings
            itemName = ChatColor.translateAlternateColorCodes('&',
                    config.getString("time-modifier-item.name", "&bTime Modifier"));

            itemLore = new ArrayList<>();
            for (String loreLine : config.getStringList("time-modifier-item.lore")) {
                itemLore.add(ChatColor.translateAlternateColorCodes('&', loreLine));
            }

            // Set GUI title
            GUI_TITLE = ChatColor.DARK_AQUA + "Timer Modification";

            System.out.println("[TimerPlugin] Loaded all configuration values successfully");
        } catch (Exception e) {
            getLogger().severe("Error loading config values: " + e.getMessage());
            System.out.println("[TimerPlugin] Error loading config values: " + e.getMessage());
            e.printStackTrace();

            // Set default values
            timeRemainingInDays = 30.0;
            MAX_DAYS = 30.0;
            maxAddDays = 2;
            maxRemoveDays = 2;
            minDaysToRemove = 2.0;
            msgKillerReceivedItem = ChatColor.GREEN + "You've killed %victim%! Use the Heart of the Sea to modify the timer.";
            msgTimerZero = ChatColor.RED + "The timer has reached zero!";
            msgPlayerAddedTime = ChatColor.GREEN + "%player% added %days% day(s) to the timer!";
            msgPlayerRemovedTime = ChatColor.RED + "%player% removed %days% day(s) from the timer!";
            msgTimerAtMax = ChatColor.RED + "The timer cannot exceed %max% days!";
            msgTimerAtMin = ChatColor.RED + "The timer cannot go below 0 days!";
            msgNotRecentKiller = ChatColor.RED + "You can only use this item after killing a player.";
            msgPlayerRevivalCoords = ChatColor.GREEN + "Player %player% has respawned at coordinates: " + ChatColor.YELLOW + "%x%, %y%, %z%";
            itemName = ChatColor.AQUA + "Time Modifier";
            itemLore = new ArrayList<>();
            itemLore.add(ChatColor.YELLOW + "Right-click to open timer modification menu");
            itemLore.add(ChatColor.GRAY + "Add or remove 1-2 days from the timer");

            System.out.println("[TimerPlugin] Using default values due to config error");
        }
    }

    private BarColor getBarColor() {
        String barColorStr = getConfig().getString("display.bar-color", "BLUE");
        try {
            return BarColor.valueOf(barColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid bar color in config: " + barColorStr + ". Using BLUE instead.");
            return BarColor.BLUE;
        }
    }

    private BarStyle getBarStyle() {
        String barStyleStr = getConfig().getString("display.bar-style", "SOLID");
        try {
            return BarStyle.valueOf(barStyleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            getLogger().warning("Invalid bar style in config: " + barStyleStr + ". Using SOLID instead.");
            return BarStyle.SOLID;
        }
    }

    @Override
    public void onDisable() {
        // Save timer state
        getConfig().set("timer.initial-days", timeRemainingInDays);
        saveConfig();

        // Save lives data
        saveLivesData();

        // Remove boss bar
        timerBar.removeAll();

        // Log that the plugin has been disabled
        getLogger().info("TimerPlugin has been disabled!");
    }

    private void startTimerCountdown() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Decrease time by 1 second
                timeRemainingInDays -= 1.0 / DAY_IN_SECONDS;

                // Update the bar
                updateTimerBar();

                // Check if timer has reached zero
                if (timeRemainingInDays <= 0) {
                    timeRemainingInDays = 0;
                    Bukkit.broadcastMessage(msgTimerZero);
                    // Here you could implement what happens when the timer reaches zero
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Run every second (20 ticks)
    }

    private void updateTimerBar() {
        // Update boss bar progress and title
        double progress = Math.max(0, Math.min(1, timeRemainingInDays / MAX_DAYS));
        timerBar.setProgress(progress);
        timerBar.setTitle(formatTimeDisplay(timeRemainingInDays));

        // Show the boss bar to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            timerBar.addPlayer(player);
        }
    }

    private String formatTimeDisplay(double daysRemaining) {
        int days = (int) daysRemaining;
        int hours = (int) ((daysRemaining - days) * 24);
        int minutes = (int) ((daysRemaining - days - hours / 24.0) * 24 * 60);

        String format = getConfig().getString("display.bar-title",
                "&6Time Remaining: &f%d days, %h hours, %m minutes");

        return ChatColor.translateAlternateColorCodes('&', format
                .replace("%d", String.valueOf(days))
                .replace("%h", String.valueOf(hours))
                .replace("%m", String.valueOf(minutes)));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getName().toLowerCase();

        // Add player to boss bar
        timerBar.addPlayer(player);

        // Initialize player lives if needed
        UUID uuid = player.getUniqueId();
        if (!playerLives.containsKey(uuid)) {
            getPlayerLives(uuid); // This will initialize from config or set default
        }

        // Inform player of their lives
        int lives = playerLives.get(uuid);
        player.sendMessage(ChatColor.YELLOW + "You have " + ChatColor.RED + lives +
                ChatColor.YELLOW + " lives remaining.");

        // Inform about crafting special items
        player.sendMessage(ChatColor.AQUA + "Special recipes require the custom Heart of the Sea obtained from killing players.");

        // Check if this player was recently revived
        if (revivedPlayerTracking.containsKey(playerName)) {
            // Get the revivor player
            UUID revivorUUID = revivedPlayerTracking.get(playerName);
            Player revivor = Bukkit.getPlayer(revivorUUID);

            if (revivor != null && revivor.isOnline()) {
                // Get the location
                Location loc = player.getLocation();
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();

                // Send the message to the revivor
                String coordMessage = msgPlayerRevivalCoords
                        .replace("%player%", player.getName())
                        .replace("%x%", String.valueOf(x))
                        .replace("%y%", String.valueOf(y))
                        .replace("%z%", String.valueOf(z));

                revivor.sendMessage(coordMessage);
            }

            // Remove from tracking to prevent repeating the message
            revivedPlayerTracking.remove(playerName);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Check if the player was killed by another player
        if (killer != null) {
            // Deduct a life from the victim
            UUID victimUUID = victim.getUniqueId();
            int victimLives = getPlayerLives(victimUUID);
            setPlayerLives(victimUUID, victimLives - 1);

            // Notify the victim
            victim.sendMessage(ChatColor.RED + "You lost a life! " + ChatColor.YELLOW +
                    (victimLives - 1) + " lives remaining.");

            // Store the killer-victim pair
            recentKillers.put(killer.getUniqueId(), victimUUID);

            // Give the killer the time modifier item
            giveTimeModifierItem(killer);

            // Notify the killer
            killer.sendMessage(msgKillerReceivedItem.replace("%victim%", victim.getName()));
            killer.sendMessage(ChatColor.YELLOW + "Choose to add a day to gain an extra life!");
        }
    }

    private void giveTimeModifierItem(Player player) {
        // Create the Heart of the Sea item
        ItemStack heartOfTheSea = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = heartOfTheSea.getItemMeta();

        // Set display name and lore
        meta.setDisplayName(itemName);
        meta.setLore(itemLore);

        heartOfTheSea.setItemMeta(meta);

        // Give the item to the player
        player.getInventory().addItem(heartOfTheSea);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Check if the player is placing the Revive Totem
        if (item.getType() == Material.TOTEM_OF_UNDYING &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Revive Totem")) {

            // Cancel the event to prevent block placement
            event.setCancelled(true);

            // Open the revival GUI
            openReviveGUI(player);
        }
    }

    @EventHandler
    public void onItemInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        // Check if the player is using the Time Modifier item
        if (item.getType() == Material.HEART_OF_THE_SEA &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals(itemName)) {

            // Check if the player is a recent killer
            if (!recentKillers.containsKey(player.getUniqueId())) {
                player.sendMessage(msgNotRecentKiller);
                return;
            }

            // Cancel the event to prevent normal interactions
            event.setCancelled(true);

            // Open the GUI for the player
            openTimerModificationGUI(player);
        }
        // Check if the player is using the Time Controller
        else if (item.getType() == Material.CLOCK &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.DARK_PURPLE + "Time Controller")) {

            // Check cooldown
            if (timeControllerCooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = (timeControllerCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                if (timeLeft > 0) {
                    player.sendMessage(ChatColor.RED + "Time Controller is on cooldown. " + timeLeft + " seconds remaining.");
                    event.setCancelled(true);
                    return;
                }
            }

            // Cancel the event to prevent normal interactions
            event.setCancelled(true);

            // Execute time freeze
            freezeTimeAround(player);

            // Set cooldown (30 seconds)
            timeControllerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 30000);
        }
        // Check if the player is using the Time Mace
        else if (item.getType() == Material.MACE &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.DARK_PURPLE + "Time Mace")) {

            // Check cooldown
            if (timeMaceCooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = (timeMaceCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                if (timeLeft > 0) {
                    player.sendMessage(ChatColor.RED + "Time Mace is on cooldown. " + timeLeft + " seconds remaining.");
                    event.setCancelled(true);
                    return;
                }
            }

            // Check if player has remaining dashes
            int dashes = remainingDashes.getOrDefault(player.getUniqueId(), 3);
            if (dashes <= 0) {
                player.sendMessage(ChatColor.RED + "No more dashes remaining. Wait for cooldown to reset.");
                event.setCancelled(true);
                return;
            }

            // Cancel the event to prevent normal interactions
            event.setCancelled(true);

            // Check if player is sneaking (Shift + Right-click) - new in 1.21.1
            if (player.isSneaking()) {
                // Execute time explosion (new feature)
                executeTimeExplosion(player);

                // Use all remaining dashes for this powerful ability
                remainingDashes.put(player.getUniqueId(), 0);

                // Set cooldown (2 minutes)
                timeMaceCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 120000);

                // Schedule a task to reset dashes after cooldown
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        remainingDashes.put(player.getUniqueId(), 3);
                        player.sendMessage(ChatColor.GREEN + "Time Mace has been recharged! You have 3 dashes available.");
                    }
                }.runTaskLater(this, 20 * 120); // 120 seconds (2 minutes)

                // Notify player
                player.sendMessage(ChatColor.GOLD + "TIME EXPLOSION! " + ChatColor.RED + "All charges consumed!");
            } else {
                // Execute normal dash
                executeDash(player);

                // Decrease remaining dashes
                remainingDashes.put(player.getUniqueId(), dashes - 1);

                // If no more dashes, set cooldown (2 minutes)
                if (dashes - 1 <= 0) {
                    timeMaceCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 120000);

                    // Schedule a task to reset dashes after cooldown
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            remainingDashes.put(player.getUniqueId(), 3);
                            player.sendMessage(ChatColor.GREEN + "Time Mace has been recharged! You have 3 dashes available.");
                        }
                    }.runTaskLater(this, 20 * 120); // 120 seconds (2 minutes)
                }

                // Notify player of remaining dashes
                player.sendMessage(ChatColor.YELLOW + "Dash! " + (dashes - 1) + " dashes remaining.");
            }
        }
        // Check if the player is using the Revive Totem
        else if (item.getType() == Material.TOTEM_OF_UNDYING &&
                item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Revive Totem")) {

            // Cancel the event to prevent normal interactions
            event.setCancelled(true);

            // Open the revival GUI
            openReviveGUI(player);
        }
    }

    private void openReviveGUI(Player player) {
        // Get the list of banned players
        List<String> bannedPlayersUUIDs = livesConfig.getStringList("banned-players");
        List<String> bannedPlayerNames = new ArrayList<>();

        // Convert UUIDs to player names
        for (String uuidStr : bannedPlayersUUIDs) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                for (org.bukkit.OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
                    if (offlinePlayer.getUniqueId().equals(uuid) && offlinePlayer.getName() != null) {
                        bannedPlayerNames.add(offlinePlayer.getName());
                        break;
                    }
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid UUID in banned-players list: " + uuidStr);
            }
        }

        // Also add players banned by name
        for (org.bukkit.BanEntry entry : Bukkit.getBanList(org.bukkit.BanList.Type.NAME).getBanEntries()) {
            String playerName = entry.getTarget();
            if (!bannedPlayerNames.contains(playerName)) {
                bannedPlayerNames.add(playerName);
            }
        }

        // Create GUI inventory
        int size = Math.min(54, ((bannedPlayerNames.size() + 8) / 9) * 9); // Round up to nearest multiple of 9, max 54
        if (size < 9) size = 9; // Minimum size

        Inventory gui = Bukkit.createInventory(null, size, REVIVE_GUI_TITLE);

        // Add banned player heads to the GUI
        for (int i = 0; i < bannedPlayerNames.size() && i < size; i++) {
            String playerName = bannedPlayerNames.get(i);
            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) playerHead.getItemMeta();

            meta.setDisplayName(ChatColor.YELLOW + playerName);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to revive this player");
            lore.add(ChatColor.AQUA + "They will return with " + REVIVE_LIVES + " lives");

            meta.setLore(lore);

            // Set the skull owner
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));

            playerHead.setItemMeta(meta);
            gui.setItem(i, playerHead);
        }

        // If no banned players, add an info item
        if (bannedPlayerNames.isEmpty()) {
            ItemStack infoItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = infoItem.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "No Banned Players");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "There are currently no banned players to revive");

            meta.setLore(lore);
            infoItem.setItemMeta(meta);

            gui.setItem(4, infoItem);
        }

        // Open the GUI
        player.openInventory(gui);
    }

    private void freezeTimeAround(Player user) {
        // Get all entities within 10 blocks
        for (Entity entity : user.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof LivingEntity && entity != user) {
                LivingEntity livingEntity = (LivingEntity) entity;

                // Apply extreme slowness effect (basically freezing)
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 100)); // 3 seconds, very high amplifier
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 128)); // Prevents jumping
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 1)); // Add blindness for effect

                // Notify the entity if it's a player
                if (entity instanceof Player) {
                    ((Player) entity).sendMessage(ChatColor.DARK_PURPLE + "Time has been frozen around you by " + user.getName() + "!");
                }
            }
        }

        // Visual and sound effects
        user.getWorld().strikeLightningEffect(user.getLocation());
        user.sendMessage(ChatColor.DARK_PURPLE + "You have frozen time for 3 seconds!");
    }

    private void executeDash(Player player) {
        // Get the direction the player is looking
        Vector direction = player.getLocation().getDirection();

        // Multiply by dash strength (adjust as needed)
        direction.multiply(2.5);

        // Set a maximum y velocity to prevent too much vertical movement
        if (direction.getY() > 0.5) {
            direction.setY(0.5);
        }

        // Apply the velocity to the player
        player.setVelocity(direction);

        // Visual effects - updated for 1.21.1
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Particle effects
        try {
            // More modern particle system (1.21.1)
            world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc, 20, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, loc, 15, 0.1, 0.1, 0.1, 0.03);
            world.playSound(loc, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.4f);
        } catch (Exception e) {
            // Fallback to older effect system
            try {
                player.getWorld().playEffect(player.getLocation(), org.bukkit.Effect.ENDER_SIGNAL, 0);
            } catch (Exception ex) {
                getLogger().warning("Could not create dash particle effects: " + ex.getMessage());
            }
        }
    }

    /**
     * New method for time explosion effect (Shift + Right-click with Time Mace)
     * @param player The player creating the time explosion
     */
    private void executeTimeExplosion(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();

        if (world == null) return;

        try {
            // Visual effects
            world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1, 0, 0, 0, 0);
            world.spawnParticle(org.bukkit.Particle.PORTAL, location, 100, 3, 3, 3, 1);
            world.spawnParticle(org.bukkit.Particle.END_ROD, location, 50, 3, 3, 3, 0.5);

            // Sound effects
            world.playSound(location, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            world.playSound(location, org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.2f);

            // Affect nearby entities (freeze and push away)
            for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                if (entity instanceof LivingEntity && entity != player) {
                    LivingEntity livingEntity = (LivingEntity) entity;

                    // Calculate push direction (away from explosion)
                    Vector pushDir = entity.getLocation().toVector().subtract(location.toVector()).normalize().multiply(2.0);
                    pushDir.setY(Math.min(1.0, pushDir.getY() + 0.5)); // Add some upward motion

                    // Apply velocity
                    entity.setVelocity(pushDir);

                    // Apply effects
                    if (entity instanceof Player) {
                        ((Player) entity).sendMessage(ChatColor.DARK_PURPLE + player.getName() +
                                " has created a time explosion!");
                    }

                    // Freeze effect (slowness + jump prevention)
                    livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3)); // 5 seconds
                    livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 128)); // Prevents jumping
                    livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 1)); // Brief blindness
                    livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 140, 1)); // Nausea effect
                }
            }

            // Create a temporary time distortion zone (marked by particles)
            new BukkitRunnable() {
                int ticksRun = 0;
                final int maxTicks = 100; // 5 seconds

                @Override
                public void run() {
                    ticksRun++;

                    // Create ring of particles
                    double radius = 5.0 + Math.sin(ticksRun * 0.1) * 1.0; // Pulsating radius
                    for (int i = 0; i < 16; i++) {
                        double angle = i * Math.PI / 8;
                        double x = location.getX() + radius * Math.cos(angle);
                        double z = location.getZ() + radius * Math.sin(angle);
                        Location particleLoc = new Location(world, x, location.getY() + 0.1, z);

                        world.spawnParticle(org.bukkit.Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.01);
                    }

                    // Slow entities that enter the zone
                    for (Entity entity : world.getNearbyEntities(location, radius, 5, radius)) {
                        if (entity instanceof LivingEntity && entity != player) {
                            ((LivingEntity) entity).addPotionEffect(
                                    new PotionEffect(PotionEffectType.SLOWNESS, 20, 2));
                        }
                    }

                    if (ticksRun >= maxTicks) {
                        // Final explosion effect when zone disappears
                        world.spawnParticle(org.bukkit.Particle.FLASH, location, 10, 5, 5, 5, 0);
                        world.playSound(location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                        this.cancel();
                    }
                }
            }.runTaskTimer(this, 0L, 1L);

        } catch (Exception e) {
            getLogger().warning("Error executing time explosion: " + e.getMessage());
        }
    }

    private void openTimerModificationGUI(Player player) {
        // Create inventory for GUI (3 rows = 27 slots)
        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Create the options based on configuration
        int slot = 10;

        // Add day options
        for (int i = 1; i <= maxAddDays; i++) {
            ItemStack addDays = createGuiItem(
                    i == 1 ? Material.LIME_WOOL : Material.GREEN_WOOL,
                    ChatColor.GREEN + "Add " + i + " Day" + (i > 1 ? "s" : "") + " (Gain 1 Life)",
                    ChatColor.GRAY + "Click to add " + i + " day" + (i > 1 ? "s" : "") + " to the timer",
                    ChatColor.AQUA + "You will gain 1 life for adding time"
            );
            gui.setItem(slot++, addDays);
        }

        // Skip middle slot
        slot = 14;

        // Remove day options
        for (int i = 1; i <= maxRemoveDays; i++) {
            ItemStack removeDays = createGuiItem(
                    i == 1 ? Material.PINK_WOOL : Material.RED_WOOL,
                    ChatColor.RED + "Remove " + i + " Day" + (i > 1 ? "s" : ""),
                    ChatColor.GRAY + "Click to remove " + i + " day" + (i > 1 ? "s" : "") + " from the timer",
                    ChatColor.RED + "You will NOT gain a life for removing time"
            );
            gui.setItem(slot++, removeDays);
        }

        // Add info panel
        int playerLivesCount = getPlayerLives(player.getUniqueId());
        ItemStack info = createGuiItem(Material.PAPER, ChatColor.GOLD + "Timer Information",
                ChatColor.GRAY + "Current time: " + formatTimeDisplay(timeRemainingInDays),
                ChatColor.GRAY + "Maximum time: " + MAX_DAYS + " days",
                ChatColor.GRAY + "Minimum to remove: " + minDaysToRemove + " days",
                ChatColor.YELLOW + "Your lives: " + ChatColor.RED + playerLivesCount);
        gui.setItem(22, info);

        // Fill empty slots with glass panes
        ItemStack filler = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", "");
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }

        // Open the GUI for the player
        player.openInventory(gui);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);

        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(line);
        }

        meta.setLore(loreList);
        item.setItemMeta(meta);

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if this is the time modification GUI
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            // Cancel the event to prevent item moving
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            // Check if the player clicked on an item
            if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                return;
            }

            // Handle the clicked item
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                String displayName = clickedItem.getItemMeta().getDisplayName();

                // Check if adding days
                for (int i = 1; i <= maxAddDays; i++) {
                    if (displayName.equals(ChatColor.GREEN + "Add " + i + " Day" + (i > 1 ? "s" : "") + " (Gain 1 Life)")) {
                        modifyTimer(player, i, true);
                        player.closeInventory();
                        return;
                    }
                }

                // Check if removing days
                for (int i = 1; i <= maxRemoveDays; i++) {
                    if (displayName.equals(ChatColor.RED + "Remove " + i + " Day" + (i > 1 ? "s" : ""))) {
                        modifyTimer(player, -i, false);
                        player.closeInventory();
                        return;
                    }
                }
            }
        }
        // Check if this is the revive GUI
        else if (event.getView().getTitle().equals(REVIVE_GUI_TITLE)) {
            // Cancel the event to prevent item moving
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            // Check if the player clicked on a valid item
            if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) {
                return;
            }

            // Get the player name from the item
            String playerName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // Revive the player
            revivePlayer(playerName, player);

            // Consume the revive item
            ItemStack reviveItem = null;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.TOTEM_OF_UNDYING &&
                        item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                        item.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Revive Totem")) {
                    reviveItem = item;
                    break;
                }
            }

            if (reviveItem != null) {
                if (reviveItem.getAmount() > 1) {
                    reviveItem.setAmount(reviveItem.getAmount() - 1);
                } else {
                    player.getInventory().remove(reviveItem);
                }
                player.updateInventory();
            }

            // Close the GUI
            player.closeInventory();
        }
    }

    @EventHandler
    public void onCraftItem(PrepareItemCraftEvent event) {
        // Check if any crafting result exists
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;

        // Check if crafting one of our special items that needs custom Heart of the Sea
        if ((result.getType() == Material.CLOCK && result.hasItemMeta() &&
                result.getItemMeta().getDisplayName().equals(ChatColor.DARK_PURPLE + "Time Controller")) ||
                (result.getType() == Material.TOTEM_OF_UNDYING && result.hasItemMeta() &&
                        result.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "Revive Totem")) ||
                (result.getType() == Material.MACE && result.hasItemMeta() &&
                        result.getItemMeta().getDisplayName().equals(ChatColor.GOLD + "time_mace"))) {

            // Check if any of the ingredients are Heart of the Sea
            boolean hasCustomHeartOfSea = false;
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && item.getType() == Material.HEART_OF_THE_SEA) {
                    // Check if this is the custom Heart of the Sea from killing players
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                            item.getItemMeta().getDisplayName().equals(itemName)) {
                        hasCustomHeartOfSea = true;
                        break;
                    }
                }
            }

            // If the recipe doesn't have the custom Heart of the Sea, prevent crafting
            if (!hasCustomHeartOfSea) {
                // Set the result to null to prevent crafting
                event.getInventory().setResult(null);

                // If there's a player doing the crafting, notify them
                if (event.getView().getPlayer() instanceof Player) {
                    Player player = (Player) event.getView().getPlayer();
                    player.sendMessage(ChatColor.RED + "This recipe requires the Heart of the Sea obtained from killing players!");
                }
            }
        }
    }

    private void modifyTimer(Player player, int days, boolean gainLife) {
        double newTime = timeRemainingInDays + days;

        // Check constraints
        if (days > 0) {
            // Adding days
            if (newTime > MAX_DAYS) {
                player.sendMessage(msgTimerAtMax.replace("%max%", String.valueOf((int)MAX_DAYS)));
                return;
            }
        } else {
            // Removing days
            if (timeRemainingInDays < minDaysToRemove && days < 0) {
                player.sendMessage(ChatColor.RED + "Cannot remove days when timer is below " + minDaysToRemove + " days!");
                return;
            }

            if (newTime < 0) {
                player.sendMessage(msgTimerAtMin);
                return;
            }
        }

        // Update the timer
        timeRemainingInDays = newTime;
        updateTimerBar();

        // Handle lives if adding days
        if (gainLife && days > 0) {
            // Give the player an extra life
            UUID playerUUID = player.getUniqueId();
            int currentLives = getPlayerLives(playerUUID);
            setPlayerLives(playerUUID, currentLives + 1);

            // Notify the player
            player.sendMessage(ChatColor.GREEN + "You gained a life! " + ChatColor.YELLOW +
                    "You now have " + (currentLives + 1) + " lives.");
        }

        // Notify the player and the server
        String message;
        if (days > 0) {
            message = msgPlayerAddedTime
                    .replace("%player%", player.getName())
                    .replace("%days%", String.valueOf(days));

            // Play add day effects (green particles and beacon sound)
            playAddDayEffects(player.getLocation());
        } else {
            message = msgPlayerRemovedTime
                    .replace("%player%", player.getName())
                    .replace("%days%", String.valueOf(Math.abs(days)));

            // Play remove day effects (red particles and dragon growl)
            playRemoveDayEffects(player.getLocation());
        }

        player.sendMessage(message);
        Bukkit.broadcastMessage(message);

        // Remove the time modifier item
        removeTimeModifierItem(player);

        // Remove player from recent killers
        recentKillers.remove(player.getUniqueId());
    }

    /**
     * Plays green particles and beacon sound when days are added
     * @param location The location to play the effects at
     */
    private void playAddDayEffects(Location location) {
        // Get world
        org.bukkit.World world = location.getWorld();
        if (world == null) return;

        // Play beacon sound
        world.playSound(location, org.bukkit.Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f);

        // Schedule sound to repeat for 5 seconds
        for (int i = 1; i < 5; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    world.playSound(location, org.bukkit.Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.0f);
                }
            }.runTaskLater(this, i * 20L); // Run every second for 5 seconds
        }

        // Spawn green particles for 5 seconds
        new BukkitRunnable() {
            int ticksRun = 0;
            final int maxTicks = 100; // 5 seconds (20 ticks per second)

            @Override
            public void run() {
                ticksRun++;

                // Spawn particles in a spiral pattern
                for (int i = 0; i < 8; i++) {
                    double angle = (ticksRun * 0.5) + (i * Math.PI / 4);
                    double x = location.getX() + 1.5 * Math.cos(angle);
                    double z = location.getZ() + 1.5 * Math.sin(angle);
                    Location particleLoc = new Location(world, x, location.getY() + 1 + (ticksRun % 10) * 0.1, z);

                    // Spawn green particles (VILLAGER_HAPPY or COMPOSTER for green effect)
                    world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 5, 0.2, 0.2, 0.2, 0.0);
                    world.spawnParticle(org.bukkit.Particle.COMPOSTER, particleLoc, 2, 0.2, 0.2, 0.2, 0.0);
                }

                if (ticksRun >= maxTicks) {
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    /**
     * Plays red particles and dragon growl sound when days are removed
     * @param location The location to play the effects at
     */
    private void playRemoveDayEffects(Location location) {
        // Get world
        org.bukkit.World world = location.getWorld();
        if (world == null) return;

        // Play dragon growl sound
        world.playSound(location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);

        // Schedule sound to repeat for 5 seconds
        for (int i = 1; i < 5; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    world.playSound(location, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.0f);
                }
            }.runTaskLater(this, i * 20L); // Run every second for 5 seconds
        }

        // Spawn red particles for 5 seconds
        new BukkitRunnable() {
            int ticksRun = 0;
            final int maxTicks = 100; // 5 seconds (20 ticks per second)

            @Override
            public void run() {
                ticksRun++;

                // Create a circular pattern of particles
                for (int i = 0; i < 8; i++) {
                    double angle = (ticksRun * 0.5) + (i * Math.PI / 4);
                    double x = location.getX() + 2 * Math.cos(angle);
                    double z = location.getZ() + 2 * Math.sin(angle);
                    Location particleLoc = new Location(world, x, location.getY() + 1 + (ticksRun % 20) * 0.1, z);

                    // Spawn red particles
                    // Using Redstone with Dust options for red color in newer versions
                    try {
                        // Try to use the newer API with dust options (1.13+)
                        org.bukkit.Particle.DustOptions dustOptions = new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.0f);
                        world.spawnParticle(Particle.DUST, particleLoc, 10, 0.3, 0.3, 0.3, 0.0, dustOptions);
                    } catch (Exception e) {
                        // Fallback for older versions
                        world.spawnParticle(org.bukkit.Particle.FLAME, particleLoc, 5, 0.2, 0.2, 0.2, 0.01);
                        world.spawnParticle(org.bukkit.Particle.LAVA, particleLoc, 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }

                if (ticksRun >= maxTicks) {
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void removeTimeModifierItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.HEART_OF_THE_SEA &&
                    item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                    item.getItemMeta().getDisplayName().equals(itemName)) {

                item.setAmount(item.getAmount() - 1);
                player.updateInventory();
                break;
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle /lives command
        if (command.getName().equalsIgnoreCase("lives")) {
            if (args.length > 0 && sender.hasPermission("timerplugin.admin")) {
                // Admin checking another player's lives
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found!");
                    return true;
                }

                int lives = getPlayerLives(target.getUniqueId());
                sender.sendMessage(ChatColor.YELLOW + target.getName() + " has " + ChatColor.RED +
                        lives + ChatColor.YELLOW + " lives remaining.");
            } else if (sender instanceof Player) {
                // Player checking their own lives
                Player player = (Player) sender;
                int lives = getPlayerLives(player.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + "You have " + ChatColor.RED +
                        lives + ChatColor.YELLOW + " lives remaining.");
            } else {
                // Console without player argument
                sender.sendMessage(ChatColor.RED + "Console must specify a player: /lives <player>");
            }
            return true;
        }

        // Handle /timerplugin command
        if (command.getName().equalsIgnoreCase("timerplugin")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("settime") && sender.hasPermission("timerplugin.admin")) {
                    if (args.length > 1) {
                        try {
                            double newTime = Double.parseDouble(args[1]);
                            if (newTime >= 0 && newTime <= MAX_DAYS) {
                                timeRemainingInDays = newTime;
                                updateTimerBar();
                                sender.sendMessage(ChatColor.GREEN + "Timer set to " + newTime + " days!");
                            } else {
                                sender.sendMessage(ChatColor.RED + "Time must be between 0 and " + MAX_DAYS + " days!");
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Invalid number format!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /timerplugin settime <days>");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("timerplugin.admin")) {
                    // Reload the configuration
                    reloadConfig();
                    loadConfigValues();
                    updateTimerBar();
                    sender.sendMessage(ChatColor.GREEN + "Timer Plugin configuration reloaded!");
                    return true;
                } else if (args[0].equalsIgnoreCase("setlives") && sender.hasPermission("timerplugin.admin")) {
                    if (args.length > 2) {
                        Player target = Bukkit.getPlayer(args[1]);
                        if (target == null) {
                            sender.sendMessage(ChatColor.RED + "Player not found!");
                            return true;
                        }

                        try {
                            int lives = Integer.parseInt(args[2]);
                            if (lives < 0) {
                                sender.sendMessage(ChatColor.RED + "Lives cannot be negative!");
                                return true;
                            }

                            setPlayerLives(target.getUniqueId(), lives);
                            sender.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s lives to " + lives + ".");
                            target.sendMessage(ChatColor.YELLOW + "Your lives have been set to " + ChatColor.RED + lives +
                                    ChatColor.YELLOW + " by an admin.");
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ChatColor.RED + "Invalid number format!");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /timerplugin setlives <player> <lives>");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("givespecial") && sender.hasPermission("timerplugin.admin")) {
                    if (args.length > 2) {
                        Player target = Bukkit.getPlayer(args[1]);
                        if (target == null) {
                            sender.sendMessage(ChatColor.RED + "Player not found!");
                            return true;
                        }

                        if (args[2].equalsIgnoreCase("controller")) {
                            target.getInventory().addItem(createTimeController());
                            sender.sendMessage(ChatColor.GREEN + "Gave Time Controller to " + target.getName());
                            target.sendMessage(ChatColor.GREEN + "You received a Time Controller!");
                        } else if (args[2].equalsIgnoreCase("mace")) {
                            target.getInventory().addItem(createTimeMace());
                            sender.sendMessage(ChatColor.GREEN + "Gave Time Mace to " + target.getName());
                            target.sendMessage(ChatColor.GREEN + "You received a Time Mace!");
                        } else if (args[2].equalsIgnoreCase("revive")) {
                            target.getInventory().addItem(createReviveItem());
                            sender.sendMessage(ChatColor.GREEN + "Gave Revive Totem to " + target.getName());
                            target.sendMessage(ChatColor.GREEN + "You received a Revive Totem!");
                        } else if (args[2].equalsIgnoreCase("heartofthesea")) {
                            // Give the killer the time modifier item (the special Heart of the Sea)
                            giveTimeModifierItem(target);
                            sender.sendMessage(ChatColor.GREEN + "Gave custom Heart of the Sea to " + target.getName());
                            target.sendMessage(ChatColor.GREEN + "You received a custom Heart of the Sea!");
                        } else {
                            sender.sendMessage(ChatColor.RED + "Unknown special item! Use 'controller', 'mace', 'revive', or 'heartofthesea'");
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /timerplugin givespecial <player> <controller|mace|revive|heartofthesea>");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("info")) {
                    sender.sendMessage(ChatColor.GOLD + "Timer Plugin Information:");
                    sender.sendMessage(ChatColor.YELLOW + "Current time: " + formatTimeDisplay(timeRemainingInDays));
                    sender.sendMessage(ChatColor.YELLOW + "Maximum days: " + MAX_DAYS);
                    sender.sendMessage(ChatColor.YELLOW + "Min days to remove: " + minDaysToRemove);
                    sender.sendMessage(ChatColor.YELLOW + "Max days to add: " + maxAddDays);
                    sender.sendMessage(ChatColor.YELLOW + "Max days to remove: " + maxRemoveDays);

                    // Lives info
                    List<String> bannedPlayers = livesConfig.getStringList("banned-players");
                    sender.sendMessage(ChatColor.YELLOW + "Banned players: " + bannedPlayers.size());

                    return true;
                }
            }
            // Show help if no args or unknown command
            sender.sendMessage(ChatColor.GOLD + "Timer Plugin Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/timerplugin info - Show plugin information");
            sender.sendMessage(ChatColor.YELLOW + "/lives [player] - Check your lives or another player's lives");

            if (sender.hasPermission("timerplugin.admin")) {
                sender.sendMessage(ChatColor.YELLOW + "/timerplugin settime <days> - Set the timer value");
                sender.sendMessage(ChatColor.YELLOW + "/timerplugin reload - Reload the configuration");
                sender.sendMessage(ChatColor.YELLOW + "/timerplugin setlives <player> <lives> - Set a player's lives");
                sender.sendMessage(ChatColor.YELLOW + "/timerplugin givespecial <player> <controller|mace|revive|heartofthesea> - Give special items");
            }
            return true;
        }
        return false;
    }
}