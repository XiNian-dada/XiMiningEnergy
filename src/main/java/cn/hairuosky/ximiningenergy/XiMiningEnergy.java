package cn.hairuosky.ximiningenergy;

import dev.lone.itemsadder.api.CustomBlock;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@SuppressWarnings("SqlDialectInspection")
public class XiMiningEnergy extends JavaPlugin implements Listener, CommandExecutor {
    private Economy economy;
    private BukkitTask energyRegenTask;
    private Connection connection;
    private BossBarManager bossBarManager;
    //private final HashMap<Material, Integer> materialEnergyCost = new HashMap<>();
    // 将 Map<Material, Integer> 改为 Map<String, Integer>
    private Map<String, Integer> materialEnergyCost = new HashMap<>();
    private Map<String, HealingPotion> healingPotions = new HashMap<>();
    private UpgradeGUI upgradeGUI;
    private int defaultEnergy;
    private int defaultMaxEnergy;
    private int defaultRegenRate;
    private String notEnoughEnergyMessage;  //信息提示
    private String messagePrefix;  //插件前缀
    private String currentEnergyMessage;
    private String currentEnergyMessageErr;
    private String onlyPlayerMessage,playerNotFoundMessage;
    private String databaseActiveMessage,databaseNotActiveMessage,databaseNameMessage,databaseUsername,databaseSizeMessage,databaseSizeErrMessage,databaseLatencyMessage,databaseInformationErrMessages;
    private String vaultStatusMessage,placeholderapiStatusMessage,loadedMessage,notLoadedMessage;
    private String setMaxMessage,setMaxErrMessage,setRegenMessage,setRegenErrMessage,setCurrentMessage,setCurrentErrMessage,fillMessage,fillErrMessage,permissionDenyMessage;
    private String usageMessage,unknownCommandMessage;
    private String potionGiveMessage,potionNotFoundMessage,usePotionMessage;
    private String lowWarningMessage,deathThresholdMessage,deathMessage,deathBroadcastMessage;
    @Override
    public void onEnable() {
        // 从配置文件中加载use-itemsadder选项
        saveDefaultConfig();
        boolean useItemsAdder = getConfig().getBoolean("use-itemsadder", false);

        // 检查 PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            getLogger().info("PlaceholderAPI is found");
        } else {
            getLogger().warning("PlaceholderAPI is not found, it means that you cannot use placeholder");
        }

        // 检查 Vault
        if (Bukkit.getPluginManager().getPlugin("Vault") != null){
            getLogger().info("Vault is found, enjoy this plugin!");
        } else {
            getLogger().warning("Vault is not found, you should install it on your server");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 检查 ItemsAdder
        if (useItemsAdder) {
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
                getLogger().info("ItemsAdder is found, You can add your custom blocks in this plugin");
            } else {
                getLogger().warning("ItemsAdder is not found but use-itemsadder is set to true in the config.");
                getLogger().warning("Please install ItemsAdder or set use-itemsadder to false.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            getLogger().info("ItemsAdder support is disabled by configuration.");
        }

        // 加载配置值
        loadConfigValues();
        loadEnergyCostsFromConfig();
        loadHealingPotions();
        bossBarManager = new BossBarManager(this);
        // 注册命令Tab补全器
        this.getCommand("miningenergy").setTabCompleter(new MiningEnergyTabCompleter(this));

        // 连接数据库
        try {
            connectToDatabase();
            createTableIfNotExists();
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Unable to connect to MySQL database, please check the configuration!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 初始化升级GUI
        upgradeGUI = new UpgradeGUI(this);

        // 注册事件监听器和命令执行器
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(this.getCommand("miningenergy")).setExecutor(this);

        // 设置Vault经济系统
        if (setupEconomy()) {
            getLogger().info("Vault found and successfully hooked.");
        } else {
            getLogger().warning("Vault not found or failed to hook.");
        }

        // 注册PlaceholderAPI的占位符
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MiningEnergyPlaceholder(this).register();
            getLogger().info("PlaceholderAPI found and successfully registered placeholders.");
        } else {
            getLogger().warning("PlaceholderAPI not found.");
        }

        // 每分钟基于配置中的值恢复能量
        // 启动能量恢复任务
        startEnergyRegenTask();
    }
    private void reloadPlugin(Player player) {

        // 移除所有现有的 BossBar
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            bossBarManager.removeBossBar(onlinePlayer);
        }

        // 重新加载配置文件
        reloadConfig();

        // 重新加载配置值
        loadConfigValues();
        loadEnergyCostsFromConfig();
        loadHealingPotions();
        bossBarManager = new BossBarManager(this); // 重新初始化 BossBar 管理器

        // 检查 PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MiningEnergyPlaceholder(this).register(); // 重新注册 PlaceholderAPI 占位符
            getLogger().info("PlaceholderAPI found and successfully registered placeholders.");
        } else {
            getLogger().warning("PlaceholderAPI not found.");
        }

        // 检查 Vault
        if (setupEconomy()) {
            getLogger().info("Vault found and successfully hooked.");
        } else {
            getLogger().warning("Vault not found or failed to hook.");
        }

        // 检查 ItemsAdder
        boolean useItemsAdder = getConfig().getBoolean("use-itemsadder", false);
        if (useItemsAdder) {
            if (getServer().getPluginManager().getPlugin("ItemsAdder") != null) {
                getLogger().info("ItemsAdder is found, You can add your custom blocks in this plugin");
            } else {
                getLogger().warning("ItemsAdder is not found but use-itemsadder is set to true in the config.");
                getLogger().warning("Please install ItemsAdder or set use-itemsadder to false.");
            }
        } else {
            getLogger().info("ItemsAdder support is disabled by configuration.");
        }

        // 重新连接数据库
        try {
            connectToDatabase();
            createTableIfNotExists();
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Unable to connect to MySQL database, please check the configuration!");
            player.sendMessage(messagePrefix + "Failed to reload plugin due to database issues.");
            return;
        }

        // 初始化升级GUI
        upgradeGUI = new UpgradeGUI(this);

        // 重新注册命令Tab补全器
        this.getCommand("miningenergy").setTabCompleter(new MiningEnergyTabCompleter(this));

        // 重新启动每分钟恢复能量的任务
        startEnergyRegenTask();

        player.sendMessage(messagePrefix + "Plugin reloaded successfully.");
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        defaultEnergy = config.getInt("default-energy", 300);
        defaultMaxEnergy = config.getInt("default-max-energy", 300);
        defaultRegenRate = config.getInt("default-regen-rate", 10);
        notEnoughEnergyMessage = ChatColor.translateAlternateColorCodes('&', config.getString("messages.not-enough-energy", "&cYou don't have enough energy to mine this block!"));
        messagePrefix = ChatColor.translateAlternateColorCodes('&',config.getString("messages.prefix","&7[&eXiMiningEnergy&7]"));
        currentEnergyMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.current-energy","&aYour current energy level is: &e{current_energy}"));
        currentEnergyMessageErr = ChatColor.translateAlternateColorCodes('&',config.getString("messages.current-energy-err","&cUnable to retrieve energy level, please try again later."));
        onlyPlayerMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.only-player","&cOnly players can use this command."));
        databaseActiveMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-active","&aDatabase connection is active."));
        databaseNotActiveMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-not-active","&cDatabase connection is not active."));
        databaseNameMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-name","&aDatabase name: &b{database_name}"));
        databaseUsername = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-username","&aDatabase username: &b{database_username}"));
        databaseSizeMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-size","&aDatabase size: &b{database_size}"));
        databaseSizeErrMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-size-err","&cUnable to retrieve database size."));
        databaseLatencyMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.database-latency","&aDatabase connection latency &b{latency} &ams."));
        databaseInformationErrMessages = ChatColor.translateAlternateColorCodes('&',config.getString("messages.information-err","&cError while retrieving database information."));
        vaultStatusMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.vault-status","Vault status: {vault_status}"));
        placeholderapiStatusMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.placeholderapi-status","PlaceholderAPI status: {placeholderapi_status}"));
        loadedMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.loaded","&aLoaded"));
        notLoadedMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.not-loaded","&cNot Loaded"));
        setMaxMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.set-max","&aMax energy set to {new_max_energy} for player {player}."));
        setMaxErrMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.set-max-err","&cAn error occurred while setting max energy."));
        setRegenMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.set-regen","&aRegen rate set to {new_regen_rate} for player {player}."));
        setRegenErrMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.set-regen-err","&cAn error occurred while setting regen rate"));
        setCurrentMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.set-current","&aCurrent energy set to {new_current_energy} for player {player}."));
        setCurrentErrMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.set-current-err","&cAn error occurred while setting current energy"));
        fillMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.fill","&aEnergy filled to max ({max_energy}) for player {player}."));
        fillErrMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.fill-err","&cAn error occurred while filling energy."));
        permissionDenyMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.permission-deny","&c You do not have permission to use this command!"));

        playerNotFoundMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.player-not-found","&cThis player not online or not exist"));
        usageMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.usage","&cUsage: {command}."));
        unknownCommandMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.unknown-command","&cUnknown command."));
        potionGiveMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.potion-give","You give player {player} a {potion}!"));
        potionNotFoundMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.potion-not-found","Unknown potion {potion}"));
        usePotionMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.use-potion","You used {potion}, and your energy has been restored to {current_energy}"));
        lowWarningMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.low-warning","&cWarning: Your energy is very low!"));
        deathThresholdMessage = ChatColor.translateAlternateColorCodes('&',getConfig().getString("messages.death-threshold","&cYou will die due to exhaustion of energy! You should take a break!"));
        deathMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.death","&cYou have died due to energy exhaustion!"));
        deathBroadcastMessage = ChatColor.translateAlternateColorCodes('&',config.getString("messages.death-broadcast","&c{player} has died due to energy exhaustion!"));


    }
    private void loadHealingPotions() {
        FileConfiguration config = getConfig();
        ConfigurationSection potionsSection = config.getConfigurationSection("healing-potions");

        if (potionsSection != null) {
            for (String key : potionsSection.getKeys(false)) {
                String name = potionsSection.getString(key + ".name");
                int customModelData = potionsSection.getInt(key + ".custom-model-data");
                String type = potionsSection.getString(key + ".type");
                int amount = potionsSection.getInt(key + ".amount");

                HealingPotion potion = new HealingPotion(name, customModelData, type, amount);
                healingPotions.put(key, potion);

                getLogger().info("Loaded healing potion: " + key + " -> " + potion);
            }
        } else {
            getLogger().warning("No healing potions found in the config!");
        }
    }
    public Map<String, HealingPotion> getHealingPotions() {
        return Collections.unmodifiableMap(healingPotions);
    }
    @EventHandler
    public void onPlayerUseHealingPotion(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.POTION && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();

            if (meta != null && meta.hasCustomModelData()) {
                int customModelData = meta.getCustomModelData();

                for (HealingPotion potion : healingPotions.values()) {
                    if (potion.getCustomModelData() == customModelData) {
                        event.setCancelled(true);

                        UUID uuid = player.getUniqueId();
                        try {
                            int currentEnergy = getCurrentEnergy(uuid);
                            int maxEnergy = getMaxEnergy(uuid);

                            if (potion.getType().equalsIgnoreCase("points")) {
                                currentEnergy = Math.min(currentEnergy + potion.getAmount(), maxEnergy);
                            } else if (potion.getType().equalsIgnoreCase("percentage")) {
                                currentEnergy = Math.min(currentEnergy + (int) (maxEnergy * (potion.getAmount() / 100.0)), maxEnergy);
                            }

                            updateEnergy(uuid, currentEnergy);
                            player.sendMessage(messagePrefix + usePotionMessage.replace("{potion}",potion.getName()).replace("{current_energy}",String.valueOf(currentEnergy)));
                            //player.sendMessage(ChatColor.GREEN + "You used " + potion.getName() + ", and your energy has been restored to " + currentEnergy);


                            // 消耗药水
                            item.setAmount(item.getAmount() - 1);
                            if (player != null) {
                                bossBarManager.updateBossBar(player);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
        }
    }
    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
        return (economy != null);
    }

    public Economy getEconomy() {
        return economy;
    }

    @Override
    public void onDisable() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        bossBarManager.removeBossBar(player);
        try {
            saveLastLogoutTime(uuid);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveLastLogoutTime(UUID uuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE mining_energy SET last_logout = ? WHERE uuid = ?");
        ps.setLong(1, System.currentTimeMillis());
        ps.setString(2, uuid.toString());
        ps.executeUpdate();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        try {
            calculateOfflineEnergyRegen(uuid);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void calculateOfflineEnergyRegen(UUID uuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT last_logout, current_energy, max_energy, regen_rate FROM mining_energy WHERE uuid = ?");
        ps.setString(1, uuid.toString());
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            long lastLogout = rs.getLong("last_logout");
            int currentEnergy = rs.getInt("current_energy");
            int maxEnergy = rs.getInt("max_energy");
            int regenRate = rs.getInt("regen_rate");

            long currentTime = System.currentTimeMillis();
            long timeElapsed = currentTime - lastLogout;
            int minutesElapsed = (int) (timeElapsed / (1000 * 60));  // Convert milliseconds to minutes

            int energyRegen = minutesElapsed * regenRate;
            int newEnergy = Math.min(currentEnergy + energyRegen, maxEnergy);

            updateEnergy(uuid, newEnergy);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Material blockType = event.getBlock().getType();

        // 默认使用原版方块类型作为key
        String blockKey = blockType.name();
        int cost = 0;

        // 检查是否为ItemsAdder自定义方块
        CustomBlock customBlock = CustomBlock.byAlreadyPlaced(event.getBlock());
        if (customBlock != null) {
            // 使用 itemsadder-命名空间:方块名称 的格式作为key
            blockKey = "itemsadder-" + customBlock.getNamespace() + ":" + customBlock.getId();
        }

        // 输出blockKey以进行调试
        debugModePrint("Block broken: " + blockKey);

        // 检查方块能量消耗
        if (materialEnergyCost.containsKey(blockKey)) {
            cost = materialEnergyCost.get(blockKey);
        } else {
            debugModePrint("No energy cost found for block: " + blockKey);
            return; // 如果没有找到该方块的能量消耗值，不进行任何处理
        }

        try {
            int currentEnergy = getCurrentEnergy(uuid);
            int maxEnergy = getMaxEnergy(uuid);
            int warningThreshold = (int) (maxEnergy * 0.05); // 最大能量的5%
            int deathThreshold = (int) (maxEnergy * 0.02); // 最大能量的2%

            // 如果当前能量小于或等于最大能量的5%，发送警告
            if (currentEnergy <= warningThreshold) {
                player.sendMessage(messagePrefix + lowWarningMessage);
                //player.sendMessage(ChatColor.RED + "Warning: Your energy is very low!");
            } else if (currentEnergy <= deathThreshold) {
                player.sendMessage(messagePrefix + deathThresholdMessage);
                //player.sendMessage("You will die due to exhaustion of energy! You should take a break!");
            }

            // If energy is less than or equal to 2% and the cost is greater than the current energy, player will die
            if (currentEnergy <= deathThreshold) {
                if (currentEnergy < cost) {
                    // If energy is depleted, cancel the event and kill the player
                    event.setCancelled(true);
                    player.setHealth(0.0); // Kill the player
                    player.sendMessage(messagePrefix + deathMessage);
                    //player.sendMessage(ChatColor.RED + "You have died due to energy exhaustion!");
                    Bukkit.broadcastMessage(messagePrefix + deathBroadcastMessage.replace("{player}",player.getName()));
                    //Bukkit.broadcastMessage(ChatColor.RED + player.getName() + " has died due to energy exhaustion!");
                    return;
                }
            }
            if (currentEnergy >= cost) {
                reduceEnergy(uuid, cost);
                debugModePrint("Reduced energy by: " + cost);
            } else {
                event.setCancelled(true);
                player.sendMessage(notEnoughEnergyMessage);
                debugModePrint("Not enough energy: " + currentEnergy + " required: " + cost);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            if (args.length > 0) {
                String subCommand = args[0].toLowerCase();

                switch (subCommand) {
                    case "status":
                        if (player.hasPermission("ximiningenergy.status")) {
                            try {
                                int energy = getCurrentEnergy(uuid);
                                player.sendMessage(messagePrefix + currentEnergyMessage.replace("%miningenergy_current_energy%", String.valueOf(energy)));
                            } catch (SQLException e) {
                                e.printStackTrace();
                                player.sendMessage(messagePrefix + currentEnergyMessageErr);
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;

                    case "upgrade":
                        if (player.hasPermission("ximiningenergy.upgrade")) {
                            upgradeGUI.open(player);
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;

                    case "info":
                        if (player.hasPermission("ximiningenergy.info")) {
                            showDatabaseInfo(player);
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;

                    case "setmax":
                        if (player.hasPermission("ximiningenergy.setmax")) {
                            if (args.length >= 3) {
                                try {
                                    UUID targetUuid = Bukkit.getPlayer(args[1]).getUniqueId();
                                    int newMaxEnergy = Integer.parseInt(args[2]);

                                    // 获取配置中的最大值
                                    int maxEnergyLimit = getConfig().getInt("max-values.max-energy", 1000);

                                    // 检查设置的值是否超过最大值
                                    if (newMaxEnergy > maxEnergyLimit) {
                                        player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.setmax-exceeds-limit", "&cCannot set max energy above the limit of {max_energy_limit}!")).replace("{max_energy_limit}", String.valueOf(maxEnergyLimit)));
                                        return true;
                                    }

                                    updateMaxEnergy(targetUuid, newMaxEnergy);
                                    player.sendMessage(messagePrefix + setMaxMessage.replace("{new_max_energy}", String.valueOf(newMaxEnergy)).replace("{player}", String.valueOf(args[1])));
                                } catch (SQLException | NumberFormatException e) {
                                    e.printStackTrace();
                                    player.sendMessage(messagePrefix + setMaxErrMessage);
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy setmax <player> <value>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;

                    case "setregen":
                        if (player.hasPermission("ximiningenergy.setregen")) {
                            if (args.length >= 3) {
                                try {
                                    UUID targetUuid = Bukkit.getPlayer(args[1]).getUniqueId();
                                    int newRegenRate = Integer.parseInt(args[2]);

                                    // 获取配置中的最大恢复速率值
                                    int maxRegenRateLimit = getConfig().getInt("max-values.max-regen-rate", 10);

                                    // 检查设置的恢复速率值是否超过最大限制
                                    if (newRegenRate > maxRegenRateLimit) {
                                        player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.setregen-exceeds-limit", "&cCannot set regen rate above the limit of {max_regen_rate_limit}!")).replace("{max_regen_rate_limit}", String.valueOf(maxRegenRateLimit)));
                                        return true;
                                    }

                                    updateRegenRate(targetUuid, newRegenRate);
                                    player.sendMessage(messagePrefix + setRegenMessage.replace("{new_regen_rate}", String.valueOf(newRegenRate)).replace("{player}", args[1]));
                                } catch (SQLException | NumberFormatException e) {
                                    e.printStackTrace();
                                    player.sendMessage(messagePrefix + setRegenErrMessage);
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy setregen <player> <value>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;

                    case "setcurrent":
                        if (player.hasPermission("ximiningenergy.setcurrent")) {
                            if (args.length >= 3) {
                                try {
                                    UUID targetUuid = Bukkit.getPlayer(args[1]).getUniqueId();
                                    int newCurrentEnergy = Integer.parseInt(args[2]);
                                    int maxEnergy = getMaxEnergy(targetUuid);

                                    if (newCurrentEnergy > maxEnergy) {
                                        newCurrentEnergy = maxEnergy;
                                    }

                                    setCurrentEnergy(targetUuid, newCurrentEnergy);
                                    player.sendMessage(messagePrefix + setCurrentMessage.replace("{new_current_energy}", String.valueOf(newCurrentEnergy)).replace("{player}", String.valueOf(args[1])));

                                    // 更新 BossBar
                                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                                    if (targetPlayer != null) {
                                        bossBarManager.updateBossBar(targetPlayer);
                                    }
                                } catch (SQLException | NumberFormatException e) {
                                    e.printStackTrace();
                                    player.sendMessage(messagePrefix + setCurrentErrMessage);
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy setcurrent <player> <value>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;
                    case "addmax":
                        if (player.hasPermission("ximiningenergy.addmax")) {
                            if (args.length >= 3) {
                                try {
                                    UUID targetUuid = Bukkit.getPlayer(args[1]).getUniqueId();
                                    int addValue = Integer.parseInt(args[2]);

                                    int currentMaxEnergy = getMaxEnergy(targetUuid);
                                    int newMaxEnergy = currentMaxEnergy + addValue;

                                    // 获取配置中的最大值
                                    int maxEnergyLimit = getConfig().getInt("max-values.max-energy", 1000);

                                    // 检查增加后的值是否超过最大值
                                    if (newMaxEnergy > maxEnergyLimit) {
                                        player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.addmax-exceeds-limit", "&cCannot increase max energy above the limit of {max_energy_limit}!")).replace("{max_energy_limit}", String.valueOf(maxEnergyLimit)));
                                        return true;
                                    }

                                    updateMaxEnergy(targetUuid, newMaxEnergy);
                                    player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.addmax-success", "&aMax energy increased by {add_value}. New max energy: {new_max_energy}")).replace("{add_value}", String.valueOf(addValue)).replace("{new_max_energy}", String.valueOf(newMaxEnergy)).replace("{player}", String.valueOf(args[1])));
                                } catch (SQLException | NumberFormatException e) {
                                    e.printStackTrace();
                                    player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.addmax-error", "&cError occurred while increasing max energy.")));
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy addmax <player> <value>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;
                    case "addregen":
                        if (player.hasPermission("ximiningenergy.addregen")) {
                            if (args.length >= 3) {
                                try {
                                    UUID targetUuid = Bukkit.getPlayer(args[1]).getUniqueId();
                                    int addValue = Integer.parseInt(args[2]);

                                    int currentRegenRate = getRegenRate(targetUuid);
                                    int newRegenRate = currentRegenRate + addValue;

                                    // 获取配置中的最大恢复速率
                                    int maxRegenRateLimit = getConfig().getInt("max-values.max-regen-rate", 10);

                                    // 检查增加后的值是否超过最大恢复速率
                                    if (newRegenRate > maxRegenRateLimit) {
                                        player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.addregen-exceeds-limit", "&cCannot increase regen rate above the limit of {max_regen_rate_limit}!")).replace("{max_regen_rate_limit}", String.valueOf(maxRegenRateLimit)));
                                        return true;
                                    }

                                    updateRegenRate(targetUuid, newRegenRate);
                                    player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.addregen-success", "&aRegen rate increased by {add_value}. New regen rate: {new_regen_rate}")).replace("{add_value}", String.valueOf(addValue)).replace("{new_regen_rate}", String.valueOf(newRegenRate)).replace("{player}", String.valueOf(args[1])));
                                } catch (SQLException | NumberFormatException e) {
                                    e.printStackTrace();
                                    player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.addregen-error", "&cError occurred while increasing regen rate.")));
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy addregen <player> <value>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;
                    case "fill":
                        if (player.hasPermission("ximiningenergy.fill")) {
                            if (args.length >= 2) {
                                try {
                                    UUID targetUuid = Bukkit.getPlayer(args[1]).getUniqueId();
                                    int maxEnergy = getMaxEnergy(targetUuid);
                                    setCurrentEnergy(targetUuid, maxEnergy);
                                    player.sendMessage(messagePrefix + fillMessage.replace("{player}", String.valueOf(args[1])).replace("{max_energy}", String.valueOf(maxEnergy)));

                                    // 更新 BossBar
                                    Player targetPlayer = Bukkit.getPlayer(args[1]);
                                    if (targetPlayer != null) {
                                        bossBarManager.updateBossBar(targetPlayer);
                                    }
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                    player.sendMessage(messagePrefix + fillErrMessage);
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy fill <player>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;
                    case "reload":
                        if (player.hasPermission("ximiningenergy.reload")) {
                            reloadPlugin(player);
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;

                    default:
                        player.sendMessage(messagePrefix + unknownCommandMessage + usageMessage.replace("{command}", "/miningenergy status, upgrade, info, setmax, setregen, setcurrent, fill, givepotion, reload"));
                        return true;
                    case "givepotion":
                        if (player.hasPermission("ximiningenergy.givepotion")) {
                            if (args.length >= 3) {
                                Player targetPlayer = Bukkit.getPlayer(args[1]);
                                if (targetPlayer != null) {
                                    String potionKey = args[2].toLowerCase();
                                    giveHealingPotion((Player) sender,targetPlayer, potionKey);
                                } else {
                                    player.sendMessage(messagePrefix + playerNotFoundMessage);
                                }
                            } else {
                                player.sendMessage(messagePrefix + usageMessage.replace("{command}", "/miningenergy givepotion <player> <potionKey>"));
                            }
                        } else {
                            player.sendMessage(messagePrefix + permissionDenyMessage);
                        }
                        return true;
                }
            } else {
                player.sendMessage(messagePrefix + unknownCommandMessage + usageMessage.replace("{command}", "/miningenergy status, upgrade, info, setmax, setregen, setcurrent, fill, givepotion, reload"));
            }
        } else {
            sender.sendMessage(messagePrefix + onlyPlayerMessage);
        }
        return false;
    }

    private void showDatabaseInfo(Player player) {
        try {
            if (connection != null && !connection.isClosed()) {
                player.sendMessage(messagePrefix + databaseActiveMessage);

                // 获取数据库名和用户名
                String databaseName = getConfig().getString("mysql.database");
                String username = getConfig().getString("mysql.username");

                // 显示数据库名和用户名
                player.sendMessage(messagePrefix + databaseNameMessage.replace("{database_name}", Objects.requireNonNull(databaseName)));
                player.sendMessage(messagePrefix + databaseUsername.replace("{database_username}", Objects.requireNonNull(username)));
                //player.sendMessage(ChatColor.GREEN + "Database name: " + ChatColor.AQUA + databaseName);
                //player.sendMessage(ChatColor.GREEN + "Database username: " + ChatColor.AQUA + username);

                // 获取数据库大小并测量查询延迟
                long startTime = System.currentTimeMillis();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT table_schema AS `Database`, " +
                                "SUM(data_length + index_length) / 1024 / 1024 AS `Size (MB)` " +
                                "FROM information_schema.TABLES WHERE table_schema = ?"
                );
                ps.setString(1, databaseName);
                ResultSet rs = ps.executeQuery();
                long endTime = System.currentTimeMillis();
                long latency = endTime - startTime;

                if (rs.next()) {
                    double dbSize = rs.getDouble("Size (MB)");
                    player.sendMessage(messagePrefix + databaseSizeMessage.replace("{database_size}",String.format("%.2f MB", dbSize)));
                    //player.sendMessage(ChatColor.GREEN + "Database size: " + ChatColor.AQUA + String.format("%.2f MB", dbSize));
                } else {
                    //player.sendMessage(ChatColor.RED + "Unable to retrieve database size.");
                    player.sendMessage(messagePrefix + databaseSizeErrMessage);
                }

                // 显示查询延迟
                //player.sendMessage(ChatColor.GREEN + "Connection latency: " + ChatColor.AQUA + latency + " ms");
                player.sendMessage(messagePrefix + databaseLatencyMessage.replace("{latency}",String.valueOf(latency)));

                // 检查插件是否已正确加载
                checkPluginStatus(player);
            } else {
                //player.sendMessage(ChatColor.RED + "Database connection is not active.");
                player.sendMessage(messagePrefix + databaseNotActiveMessage);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            //player.sendMessage(ChatColor.RED + "Error while retrieving database information.");
            player.sendMessage(messagePrefix + databaseInformationErrMessages);
        }
    }
    private void startEnergyRegenTask() {
        // 取消之前的能量恢复任务（如果有）
        if (energyRegenTask != null && !energyRegenTask.isCancelled()) {
            energyRegenTask.cancel();
        }

        // 启动新的能量恢复任务
        energyRegenTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    try {
                        int currentEnergy = getCurrentEnergy(uuid);
                        int regenRate = getRegenRate(uuid);
                        int maxEnergy = getMaxEnergy(uuid);
                        int newEnergy = Math.min(currentEnergy + regenRate, maxEnergy);
                        updateEnergy(uuid, newEnergy);

                        // 更新 BossBar
                        bossBarManager.updateBossBar(player);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1200L); // 1200 ticks = 1 minute
    }

    private void checkPluginStatus(Player player) {
        boolean isVaultLoaded = getServer().getPluginManager().getPlugin("Vault") != null;
        boolean isPlaceholderAPILoaded = getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        player.sendMessage(messagePrefix + vaultStatusMessage.replace("{vault_status}",(isVaultLoaded ? loadedMessage : ChatColor.RED + notLoadedMessage)));
        player.sendMessage(messagePrefix + placeholderapiStatusMessage.replace("{placeholderapi_status}",(isVaultLoaded ? loadedMessage : ChatColor.RED + notLoadedMessage)));
        //player.sendMessage(ChatColor.GREEN + "Vault status: " + (isVaultLoaded ? ChatColor.AQUA + "Loaded" : ChatColor.RED + "Not Loaded"));
        //player.sendMessage(ChatColor.GREEN + "PlaceholderAPI status: " + (isPlaceholderAPILoaded ? ChatColor.AQUA + "Loaded" : ChatColor.RED + "Not Loaded"));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        upgradeGUI.handleClick(event);
    }
    private void giveHealingPotion(Player sender, Player targetPlayer, String potionKey) {
        if (healingPotions.containsKey(potionKey)) {
            HealingPotion potion = healingPotions.get(potionKey);

            // 构建药水的物品
            ItemStack potionItem = new ItemStack(Material.POTION, 1);
            ItemMeta meta = potionItem.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(ChatColor.RESET + potion.getName());
                meta.setCustomModelData(potion.getCustomModelData());
                potionItem.setItemMeta(meta);

                targetPlayer.getInventory().addItem(potionItem);

                // 给执行指令的玩家发送消息，告知药水已给予目标玩家
                String message = messagePrefix + potionGiveMessage
                        .replace("{potion}", potion.getName())
                        .replace("{player}", targetPlayer.getName());
                sender.sendMessage(message);
            }
        } else {
            // 药水不存在时发送错误消息
            sender.sendMessage(messagePrefix + potionNotFoundMessage.replace("{potion}", potionKey));
        }
    }
    private void loadEnergyCostsFromConfig() {
        FileConfiguration config = getConfig();
        boolean useItemsAdder = config.getBoolean("use-itemsadder", false);

        // 清空 materialEnergyCost 以防止重新加载时重复添加数据
        materialEnergyCost.clear();

        for (String key : config.getConfigurationSection("energy-costs").getKeys(false)) {
            int cost = config.getInt("energy-costs." + key);

            if (key.startsWith("itemsadder-") && useItemsAdder) {
                // 对于 ItemsAdder 自定义方块，直接保存字符串 key 和能量消耗
                materialEnergyCost.put(key, cost);
                debugModePrint("Loaded ItemsAdder custom block energy cost: " + key + " -> " + cost);
            } else {
                // 处理原版方块
                Material material = Material.getMaterial(key);
                if (material != null) {
                    materialEnergyCost.put(material.name(), cost);
                    debugModePrint("Loaded vanilla block energy cost: " + key + " -> " + cost);
                } else {
                    getLogger().warning("Unrecognized material: " + key);
                }
            }
        }
    }

    private void connectToDatabase() throws SQLException {
        FileConfiguration config = getConfig();
        String host = config.getString("mysql.host");
        int port = config.getInt("mysql.port");
        String database = config.getString("mysql.database");
        String username = config.getString("mysql.username");
        String password = config.getString("mysql.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        connection = DriverManager.getConnection(url, username, password);
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS mining_energy (" +
                "player_name VARCHAR(16) NOT NULL," +
                "uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                "current_energy INT NOT NULL DEFAULT 300," +
                "max_energy INT NOT NULL DEFAULT 300," +
                "regen_rate INT NOT NULL DEFAULT 10," +
                "last_logout BIGINT" +  // Used to record the last logout timestamp
                ")";
        connection.createStatement().execute(sql);
    }

    public int getCurrentEnergy(UUID uuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT current_energy FROM mining_energy WHERE uuid = ?");
        ps.setString(1, uuid.toString());
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getInt("current_energy");
        } else {
            initializePlayerData(uuid);
            return defaultEnergy;
        }
    }

    public int getMaxEnergy(UUID uuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT max_energy FROM mining_energy WHERE uuid = ?");
        ps.setString(1, uuid.toString());
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getInt("max_energy");
        } else {
            initializePlayerData(uuid);
            return defaultMaxEnergy;
        }
    }

    public int getRegenRate(UUID uuid) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT regen_rate FROM mining_energy WHERE uuid = ?");
        ps.setString(1, uuid.toString());
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getInt("regen_rate");
        } else {
            initializePlayerData(uuid);
            return defaultRegenRate;
        }
    }

    private void updateEnergy(UUID uuid, int newEnergy) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE mining_energy SET current_energy = ? WHERE uuid = ?");
        ps.setInt(1, newEnergy);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();
    }

    public void setCurrentEnergy(UUID uuid, int newEnergy) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE mining_energy SET current_energy = ? WHERE uuid = ?");
        ps.setInt(1, newEnergy);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();

        // 更新 BossBar
        Player player = getServer().getPlayer(uuid);
        if (player != null) {
            bossBarManager.updateBossBar(player);
        }
    }


    public void reduceEnergy(UUID uuid, int amount) throws SQLException {
        int currentEnergy = getCurrentEnergy(uuid);
        int newEnergy = Math.max(currentEnergy - amount, 0);
        updateEnergy(uuid, newEnergy);

        // 调试日志
        debugModePrint("Reduced energy for player " + uuid + ": " + currentEnergy + " -> " + newEnergy);

        // 更新 BossBar
        Player player = getServer().getPlayer(uuid);
        if (player != null) {
            debugModePrint("Updating BossBar for player " + player.getName());
            bossBarManager.updateBossBar(player);
        } else {
            debugModePrint("Player with UUID " + uuid + " is not online. BossBar update skipped.");
        }
    }

    public void updateMaxEnergy(UUID uuid, int newMaxEnergy) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE mining_energy SET max_energy = ? WHERE uuid = ?");
        ps.setInt(1, newMaxEnergy);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();

        // 更新 BossBar
        Player player = getServer().getPlayer(uuid);
        if (player != null) {
            bossBarManager.updateBossBar(player);
        }
    }
    public void updateRegenRate(UUID uuid, int newRegenRate) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE mining_energy SET regen_rate = ? WHERE uuid = ?");
        ps.setInt(1, newRegenRate);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();
    }

    private void initializePlayerData(UUID uuid) throws SQLException {
        Player player = getServer().getPlayer(uuid);
        if (player != null) {
            String playerName = player.getName();
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO mining_energy (player_name, uuid, current_energy, max_energy, regen_rate) VALUES (?, ?, ?, ?, ?)"
            );
            ps.setString(1, playerName);
            ps.setString(2, uuid.toString());
            ps.setInt(3, defaultEnergy);  // Use default energy value from config
            ps.setInt(4, defaultMaxEnergy);  // Use default max energy value from config
            ps.setInt(5, defaultRegenRate);   // Use default regen rate from config
            ps.executeUpdate();

            // 显示 BossBar
            debugModePrint("Showing BossBar for player " + player.getName());
            bossBarManager.showBossBar(player);
        } else {
            debugModePrint("Player with UUID " + uuid + " is not online during data initialization.");
        }
    }

    public void debugModePrint(String text){
        getLogger().info(text);
    }
}
