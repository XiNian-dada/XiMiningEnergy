package cn.hairuosky.ximiningenergy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class UpgradeGUI {

    private final XiMiningEnergy plugin;

    public UpgradeGUI(XiMiningEnergy plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        FileConfiguration config = plugin.getConfig();
        String title = config.getString("upgrade-gui.title");
        int size = config.getInt("upgrade-gui.size");

        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        for (String key : config.getConfigurationSection("upgrade-gui.buttons").getKeys(false)) {
            int slot = Integer.parseInt(key);
            String itemType = config.getString("upgrade-gui.buttons." + key + ".item");
            Material material = Material.getMaterial(itemType);
            String itemName = config.getString("upgrade-gui.buttons." + key + ".name");
            List<String> lore = config.getStringList("upgrade-gui.buttons." + key + ".lore");

            if (material != null) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));

                    try {
                        int currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
                        int maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
                        int regenRate = plugin.getRegenRate(player.getUniqueId()); // 获取每分钟恢复能量值
                        int upgradeCost = (int) Math.ceil(Math.pow(currentEnergy, 2) / 20.0);
                        int regenUpgradeCost = (int) Math.pow(regenRate, 3) * 2; // 计算回复速率升级费用

                        // 计算恢复满能量所需的时间（分钟）
                        int remainingEnergy = maxEnergy - currentEnergy;
                        int minutesToFull = (remainingEnergy + regenRate - 1) / regenRate; // 向上取整

                        // 替换占位符
                        List<String> updatedLore = lore.stream()
                                .map(line -> ChatColor.translateAlternateColorCodes('&', line)
                                        .replace("{current_energy}", String.valueOf(currentEnergy))
                                        .replace("{max_energy}", String.valueOf(maxEnergy))
                                        .replace("{upgrade_cost}", String.valueOf(upgradeCost))
                                        .replace("{minutes_to_full}", String.valueOf(minutesToFull)) // 添加剩余时间
                                        .replace("{regen_rate}", String.valueOf(regenRate)) // 添加当前回复速率
                                        .replace("{regen_upgrade_cost}", String.valueOf(regenUpgradeCost)) // 添加回复速率升级费用
                                ).collect(Collectors.toList());

                        meta.setLore(updatedLore);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        meta.setLore(lore.stream().map(s -> ChatColor.translateAlternateColorCodes('&', s)).collect(Collectors.toList()));
                    }

                    item.setItemMeta(meta);
                }
                inventory.setItem(slot, item);
            }
        }

        player.openInventory(inventory);
    }



    public void handleClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equalsIgnoreCase(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("upgrade-gui.title")))) {
            event.setCancelled(true); // Prevent item pick up

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                String command = plugin.getConfig().getString("upgrade-gui.buttons." + event.getSlot() + ".command");

                Player player = (Player) event.getWhoClicked();

                if ("upgrade".equalsIgnoreCase(command)) {
                    try {
                        int currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
                        int maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
                        int upgradeCost = (int) Math.ceil(Math.pow(currentEnergy, 2) / 20.0);

                        if (plugin.getEconomy().getBalance(player) >= upgradeCost) {
                            plugin.getEconomy().withdrawPlayer(player, upgradeCost);

                            int newMaxEnergy = maxEnergy + 10;
                            plugin.updateMaxEnergy(player.getUniqueId(), newMaxEnergy);
                            plugin.setCurrentEnergy(player.getUniqueId(), newMaxEnergy);

                            player.sendMessage(ChatColor.GREEN + "Your energy has been upgraded to " + newMaxEnergy + "!");

                            // 重新打开 GUI 以刷新 lore
                            open(player);
                        } else {
                            player.sendMessage(ChatColor.RED + "You do not have enough coins to upgrade!");
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(ChatColor.RED + "An error occurred while upgrading.");
                    }
                } else if ("increase_regen_rate".equalsIgnoreCase(command)) {
                    try {
                        int regenRate = plugin.getRegenRate(player.getUniqueId());
                        int regenUpgradeCost = (int) Math.pow(regenRate, 3) * 2;

                        if (plugin.getEconomy().getBalance(player) >= regenUpgradeCost) {
                            plugin.getEconomy().withdrawPlayer(player, regenUpgradeCost);

                            int newRegenRate = regenRate + 1;
                            plugin.updateRegenRate(player.getUniqueId(), newRegenRate);

                            player.sendMessage(ChatColor.GREEN + "Your energy regen rate has been increased to " + newRegenRate + " per minute!");

                            // 重新打开 GUI 以刷新 lore
                            open(player);
                        } else {
                            player.sendMessage(ChatColor.RED + "You do not have enough coins to upgrade your regen rate!");
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(ChatColor.RED + "An error occurred while upgrading your regen rate.");
                    }
                }
            }
        }
    }


}
