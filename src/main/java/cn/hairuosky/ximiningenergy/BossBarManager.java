package cn.hairuosky.ximiningenergy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossBarManager implements Listener {

    private final XiMiningEnergy plugin;
    private final Map<UUID, BossBar> bossBars;
    private final Map<UUID, Integer> taskIds; // Map to store task IDs

    public BossBarManager(XiMiningEnergy plugin) {
        this.plugin = plugin;
        this.bossBars = new HashMap<>();
        this.taskIds = new HashMap<>();
        Bukkit.getPluginManager().registerEvents(this, plugin);  // Register this class as an event listener
    }

    public void showBossBar(Player player) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) {
            plugin.debugModePrint("BossBar is disabled in the config.");
            return;
        }

        UUID playerId = player.getUniqueId();
        BossBar bossBar = bossBars.get(playerId);

        if (bossBar == null) {
            plugin.debugModePrint("Creating a new BossBar for player: " + player.getName());
            bossBar = createBossBar(player);
            bossBars.put(playerId, bossBar);
        }

        bossBar.setVisible(true);

        // If the BossBar is temporary, set a task to hide it after a certain time
        String displayMode = plugin.getConfig().getString("bossbar.display-mode", "permanent");
        int displayTime = plugin.getConfig().getInt("bossbar.display-time", 5) * 20;

        if ("temporary".equalsIgnoreCase(displayMode)) {
            // Cancel previous task if it exists
            Integer taskId = taskIds.get(playerId);
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }

            // Schedule a new task
            int newTaskId = Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    BossBar bar = bossBars.get(playerId);
                    if (bar != null && bar.isVisible()) {
                        bar.setVisible(false);
                        plugin.debugModePrint("Hiding temporary BossBar for player: " + player.getName());
                    }
                }
            }, displayTime).getTaskId();

            // Store the new task ID
            taskIds.put(playerId, newTaskId);
        }
    }

    public void updateBossBar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = bossBars.get(playerId);

        if (bossBar == null) {
            bossBar = createBossBar(player);
            bossBars.put(playerId, bossBar);
        }

        try {
            int currentEnergy = plugin.getCurrentEnergy(playerId);
            int maxEnergy = plugin.getMaxEnergy(playerId);
            String title = ChatColor.translateAlternateColorCodes('&',plugin.getConfig().getString("bossbar.title", "&aCurrent Stamina: {current_stamina}/{max_stamina}")).replace("{current_stamina}", String.valueOf(currentEnergy))
                              .replace("{max_stamina}", String.valueOf(maxEnergy));

            bossBar.setTitle(title);
            bossBar.setProgress((double) currentEnergy / maxEnergy);

            // Ensure BossBar is visible and assigned to the player
            if (!bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }

            String displayMode = plugin.getConfig().getString("bossbar.display-mode", "permanent");
            if ("temporary".equalsIgnoreCase(displayMode)) {
                if (!bossBar.isVisible()) {
                    bossBar.setVisible(true);
                }
                showBossBar(player); // Ensure that BossBar is shown again and timed properly
            } else {
                bossBar.setVisible(true); // Ensure BossBar is visible if in permanent mode
            }

            plugin.debugModePrint("Updated BossBar for player: " + player.getName() + " [Energy: " + currentEnergy + "/" + maxEnergy + "]");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update BossBar for player: " + player.getName() + " due to database error.");
            e.printStackTrace();
        }
    }

    private BossBar createBossBar(Player player) {
        int currentEnergy = 0;
        try {
            currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Error retrieving current energy for player: " + player.getName() + " - " + e.getMessage());
        }
        int maxEnergy = 0;
        try {
            maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger().warning("Error retrieving max energy for player: " + player.getName() + " - " + e.getMessage());
        }

        String title = plugin.getConfig().getString("bossbar.title", "&aCurrent Energy: {current_stamina}/{max_stamina}")
                .replace("{current_stamina}", String.valueOf(currentEnergy))
                .replace("{max_stamina}", String.valueOf(maxEnergy));

        BarColor color = BarColor.valueOf(plugin.getConfig().getString("bossbar.color", "GREEN").toUpperCase());
        BarStyle style = BarStyle.valueOf(plugin.getConfig().getString("bossbar.style", "SOLID").toUpperCase());

        BossBar bossBar = Bukkit.createBossBar(title, color, style);
        bossBar.setProgress((double) currentEnergy / maxEnergy);
        bossBar.addPlayer(player);

        plugin.debugModePrint("Created BossBar with title: " + title + " for player: " + player.getName());

        return bossBar;
    }

    public void removeBossBar(Player player) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = bossBars.remove(playerId);

        if (bossBar != null) {
            bossBar.removeAll();
        }

        // Remove any scheduled task associated with this player
        Integer taskId = taskIds.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        showBossBar(player);
        updateBossBar(player);  // Ensure BossBar is updated with current data
    }
}
