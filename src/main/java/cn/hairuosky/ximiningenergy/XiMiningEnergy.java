package cn.hairuosky.ximiningenergy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("SqlDialectInspection")
public class XiMiningEnergy extends JavaPlugin implements Listener, CommandExecutor {
    private Economy economy;
    private Connection connection;
    private final HashMap<Material, Integer> materialEnergyCost = new HashMap<>();
    private UpgradeGUI upgradeGUI;
    private int defaultEnergy;
    private int defaultMaxEnergy;
    private int defaultRegenRate;
    private String notEnoughEnergyMessage;  //信息提示
    private String messagePrefix;  //插件前缀
    private String currentEnergyMessage;
    private String currentEnergyMessageErr;
    private String onlyPlayerMessage;
    private String databaseActiveMessage,databaseNotActiveMessage,databaseNameMessage,databaseUsername,databaseSizeMessage,databaseSizeErrMessage,databaseLatencyMessage,databaseInformationErrMessages;
    private String vaultStatusMessage,placeholderapiStatusMessage,loadedMessage,notLoadedMessage;
    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            getLogger().info("PlaceholderAPI is found");
        } else {
            getLogger().warning("PlaceholderAPI is not found , it means that you cannot use placeholder");
        }

        if (Bukkit.getPluginManager().getPlugin("Vault") != null){
            getLogger().info("Vault is found , enjoy this plugin!");
        } else {
            getLogger().warning("Vault is not found, you should install it on your server");
            Bukkit.getPluginManager().disablePlugin(this);
        }
        // Load configuration file
        saveDefaultConfig();
        loadConfigValues();
        loadEnergyCostsFromConfig();

        try {
            connectToDatabase();
            createTableIfNotExists();
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Unable to connect to MySQL database, please check the configuration!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        upgradeGUI = new UpgradeGUI(this);
        // Register event listeners and command executors
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(this.getCommand("miningenergy")).setExecutor(this);

        // Vault setup
        if (setupEconomy()) {
            getLogger().info("Vault found and successfully hooked.");
        } else {
            getLogger().warning("Vault not found or failed to hook.");
        }

        // Register Placeholder
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MiningEnergyPlaceholder(this).register();
            getLogger().info("PlaceholderAPI found and successfully registered placeholders.");
        } else {
            getLogger().warning("PlaceholderAPI not found.");
        }

        // Regenerate energy every minute based on the config value
        new BukkitRunnable() {
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
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1200L); // 1200 ticks = 1 minute
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
        Material blockType = event.getBlock().getType();

        if (materialEnergyCost.containsKey(blockType)) {
            int cost = materialEnergyCost.get(blockType);
            UUID uuid = player.getUniqueId();
            try {
                int currentEnergy = getCurrentEnergy(uuid);
                if (currentEnergy >= cost) {
                    reduceEnergy(uuid, cost);
                } else {
                    event.setCancelled(true);
                    player.sendMessage(notEnoughEnergyMessage);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("status")) {
                    try {
                        int energy = getCurrentEnergy(uuid);
                        //player.sendMessage("Your current energy level is: " + energy);
                        //player.sendMessage(messagePrefix + currentEnergyMessage.replace("{current_energy}",String.valueOf(energy)));
                        player.sendMessage(messagePrefix + currentEnergyMessage.replace("%miningenergy_current_energy%",String.valueOf(energy)));
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(messagePrefix + currentEnergyMessageErr);
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("upgrade")) {
                    upgradeGUI.open(player);
                    return true;
                } else if (args[0].equalsIgnoreCase("info")) {
                    showDatabaseInfo(player);
                    return true;
                }
            }
            player.sendMessage(messagePrefix + "Usage: /miningenergy status, /miningenergy upgrade, or /miningenergy info");
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

    private void loadEnergyCostsFromConfig() {
        FileConfiguration config = getConfig();
        for (String key : config.getConfigurationSection("energy-costs").getKeys(false)) {
            Material material = Material.getMaterial(key);
            int cost = config.getInt("energy-costs." + key);
            if (material != null) {
                materialEnergyCost.put(material, cost);
            } else {
                getLogger().warning("Unrecognized material: " + key);
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
    }

    public void reduceEnergy(UUID uuid, int amount) throws SQLException {
        int currentEnergy = getCurrentEnergy(uuid);
        int newEnergy = Math.max(currentEnergy - amount, 0);
        updateEnergy(uuid, newEnergy);
    }

    public void updateMaxEnergy(UUID uuid, int newMaxEnergy) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE mining_energy SET max_energy = ? WHERE uuid = ?");
        ps.setInt(1, newMaxEnergy);
        ps.setString(2, uuid.toString());
        ps.executeUpdate();
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
        }
    }
}
