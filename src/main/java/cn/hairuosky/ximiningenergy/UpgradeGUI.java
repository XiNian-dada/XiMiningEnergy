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
        String title = config.getString("upgrade-gui.title","Upgrade Your Energy");
        int size = config.getInt("upgrade-gui.size");

        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        for (String key : config.getConfigurationSection("upgrade-gui.buttons").getKeys(false)) {
            int slot = Integer.parseInt(key);
            String itemType = config.getString("upgrade-gui.buttons." + key + ".item");
            Material material = Material.getMaterial(itemType);
            String itemName = config.getString("upgrade-gui.buttons." + key + ".name");
            List<String> lore = config.getStringList("upgrade-gui.buttons." + key + ".lore");
            int customModelData = config.getInt("upgrade-gui.buttons." + key + ".custommodeldata", -1); // 获取自定义模型数据

            if (material != null) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));

                    // 设置自定义模型数据
                    if (customModelData != -1) {
                        meta.setCustomModelData(customModelData);
                    }

                    try {
                        int currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
                        int maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
                        int regenRate = plugin.getRegenRate(player.getUniqueId()); // 获取每分钟恢复能量值
                        int upgradeCost = (int) Math.ceil(Math.pow(maxEnergy, 2) / 20.0);
                        int regenUpgradeCost = (int) Math.pow(regenRate, 3) * 2; // 计算回复速率升级费用

                        // 计算恢复满能量所需的时间（分钟）
                        int remainingEnergy = maxEnergy - currentEnergy;
                        int minutesToFull = (remainingEnergy + regenRate - 1) / regenRate; // 向上取整

                        // 替换占位符
                        List<String> updatedLore = lore.stream()
                                .map(line -> ChatColor.translateAlternateColorCodes('&', line)
                                        .replace("%miningenergy_current_energy%", String.valueOf(currentEnergy))
                                        .replace("%miningenergy_max_energy%", String.valueOf(maxEnergy))
                                        .replace("%miningenergy_upgrade_cost%", String.valueOf(upgradeCost))
                                        .replace("%miningenergy_time_to_full_energy%", String.valueOf(minutesToFull)) // 修正占位符
                                        .replace("%miningenergy_rate%", String.valueOf(regenRate)) // 添加当前回复速率
                                        .replace("%miningenergy_rate_upgrade_cost%", String.valueOf(regenUpgradeCost)) // 添加回复速率升级费用
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
        if (event.getView().getTitle().equalsIgnoreCase(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("upgrade-gui.title","Upgrade Your Energy")))) {
            event.setCancelled(true); // Prevent item pick up

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                String command = plugin.getConfig().getString("upgrade-gui.buttons." + event.getSlot() + ".command");

                Player player = (Player) event.getWhoClicked();
                String messagePrefix = ChatColor.translateAlternateColorCodes('&',plugin.getConfig().getString("messages.prefix","&7[&eXiMiningEnergy&7]"));
                String upgradeError = ChatColor.translateAlternateColorCodes('&',plugin.getConfig().getString("messages.upgrade-error","&cAn error occurred while upgrading."));
                String UpgradeFail = ChatColor.translateAlternateColorCodes('&',plugin.getConfig().getString("messages.upgrade-not-enough-money","&cYou do not have enough money to upgrade!"));
                if ("upgrade".equalsIgnoreCase(command)) {

                    try {
                        int currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
                        int maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
                        int upgradeCost = (int) Math.ceil(Math.pow(currentEnergy, 2) / 20.0);
                        String energyUpgradeSuccess = ChatColor.translateAlternateColorCodes('&',plugin.getConfig().getString("messages.energy-upgrade-success","&aYou get it! Your energy has been upgrade to {new_max_energy}!"));

                        if (plugin.getEconomy().getBalance(player) >= upgradeCost) {
                            plugin.getEconomy().withdrawPlayer(player, upgradeCost);

                            int newMaxEnergy = maxEnergy + 10;
                            plugin.updateMaxEnergy(player.getUniqueId(), newMaxEnergy);
                            plugin.setCurrentEnergy(player.getUniqueId(), newMaxEnergy);

                            player.sendMessage(messagePrefix + energyUpgradeSuccess.replace("{new_max_energy}",String.valueOf(newMaxEnergy)));
                            //player.sendMessage(ChatColor.GREEN + "Your energy has been upgraded to " + newMaxEnergy + "!");

                            // 重新打开 GUI 以刷新 lore
                            open(player);
                        } else {

                            player.sendMessage(messagePrefix + UpgradeFail);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(messagePrefix + upgradeError);
                    }
                } else if ("increase_regen_rate".equalsIgnoreCase(command)) {
                    try {
                        int regenRate = plugin.getRegenRate(player.getUniqueId());
                        int regenUpgradeCost = (int) Math.pow(regenRate, 3) * 2;

                        if (plugin.getEconomy().getBalance(player) >= regenUpgradeCost) {
                            plugin.getEconomy().withdrawPlayer(player, regenUpgradeCost);

                            int newRegenRate = regenRate + 1;
                            plugin.updateRegenRate(player.getUniqueId(), newRegenRate);

                            player.sendMessage(messagePrefix + ChatColor.translateAlternateColorCodes('&',plugin.getConfig().getString("messages.regen-rate-upgrade-success","&aYour energy regen rate has been increased to {new_regen_rate} per minute")).replace("{new_regen_rate}",String.valueOf(newRegenRate)));
                            //player.sendMessage(ChatColor.GREEN + "Your energy regen rate has been increased to " + newRegenRate + " per minute!");

                            // 重新打开 GUI 以刷新 lore
                            open(player);
                        } else {
                            player.sendMessage(messagePrefix + UpgradeFail);
                            //player.sendMessage(ChatColor.RED + "You do not have enough coins to upgrade your regen rate!");
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(messagePrefix + upgradeError);
                        //player.sendMessage(ChatColor.RED + "An error occurred while upgrading your regen rate.");
                    }
                }
            }
        }
    }

}
