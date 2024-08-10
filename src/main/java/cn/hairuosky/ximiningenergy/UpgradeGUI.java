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
        String title = config.getString("upgrade-gui.title", "Upgrade Your Energy");
        int size = config.getInt("upgrade-gui.size");

        Inventory inventory = Bukkit.createInventory(null, size, ChatColor.translateAlternateColorCodes('&', title));

        int maxEnergyLimit = config.getInt("max-values.max-energy", 1000);  // 获取能量的最大值
        int maxRegenRateLimit = config.getInt("max-values.max-regen-rate", 10);  // 获取恢复速率的最大值

        for (String key : config.getConfigurationSection("upgrade-gui.buttons").getKeys(false)) {
            int slot = Integer.parseInt(key);
            String itemType = config.getString("upgrade-gui.buttons." + key + ".item");
            Material material = Material.getMaterial(itemType);
            String itemName = config.getString("upgrade-gui.buttons." + key + ".name");
            List<String> lore = config.getStringList("upgrade-gui.buttons." + key + ".lore");
            int customModelData = config.getInt("upgrade-gui.buttons." + key + ".custommodeldata", -1);

            if (material != null) {
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));

                    if (customModelData != -1) {
                        meta.setCustomModelData(customModelData);
                    }

                    try {
                        int currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
                        int maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
                        int regenRate = plugin.getRegenRate(player.getUniqueId());
                        int upgradeCost = (int) Math.ceil(Math.pow(maxEnergy, 2) / 20.0);
                        int regenUpgradeCost = (int) Math.pow(regenRate, 3) * 2;

                        int remainingEnergy = maxEnergy - currentEnergy;
                        int minutesToFull = (remainingEnergy + regenRate - 1) / regenRate;

                        List<String> updatedLore = lore.stream()
                                .map(line -> ChatColor.translateAlternateColorCodes('&', line)
                                        .replace("%miningenergy_current_energy%", String.valueOf(currentEnergy))
                                        .replace("%miningenergy_max_energy%", String.valueOf(maxEnergy))
                                        .replace("%miningenergy_upgrade_cost%", String.valueOf(upgradeCost))
                                        .replace("%miningenergy_time_to_full_energy%", String.valueOf(minutesToFull))
                                        .replace("%miningenergy_rate%", String.valueOf(regenRate))
                                        .replace("%miningenergy_rate_upgrade_cost%", String.valueOf(regenUpgradeCost))
                                        .replace("%miningenergy_max_energy_limit%", String.valueOf(maxEnergyLimit))
                                        .replace("%miningenergy_max_regen_rate_limit%", String.valueOf(maxRegenRateLimit))
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
        if (event.getView().getTitle().equalsIgnoreCase(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("upgrade-gui.title", "Upgrade Your Energy")))) {
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                String command = plugin.getConfig().getString("upgrade-gui.buttons." + event.getSlot() + ".command");

                Player player = (Player) event.getWhoClicked();
                FileConfiguration config = plugin.getConfig();
                String messagePrefix = ChatColor.translateAlternateColorCodes('&', config.getString("messages.prefix", "&7[&eXiMiningEnergy&7]"));
                String upgradeError = ChatColor.translateAlternateColorCodes('&', config.getString("messages.upgrade-error", "&cAn error occurred while upgrading."));
                String upgradeFail = ChatColor.translateAlternateColorCodes('&', config.getString("messages.upgrade-not-enough-money", "&cYou do not have enough money to upgrade!"));
                String maxLimitReached = ChatColor.translateAlternateColorCodes('&', config.getString("messages.max-limit-reached", "&cYou have reached the maximum limit for this upgrade!"));

                int maxEnergyLimit = config.getInt("max-values.max-energy", 1000);
                int maxRegenRateLimit = config.getInt("max-values.max-regen-rate", 10);

                if ("upgrade".equalsIgnoreCase(command)) {
                    try {
                        int currentEnergy = plugin.getCurrentEnergy(player.getUniqueId());
                        int maxEnergy = plugin.getMaxEnergy(player.getUniqueId());
                        int upgradeCost = (int) Math.ceil(Math.pow(currentEnergy, 2) / 20.0);

                        if (maxEnergy >= maxEnergyLimit) {
                            player.sendMessage(messagePrefix + maxLimitReached);
                            return;
                        }

                        if (plugin.getEconomy().getBalance(player) >= upgradeCost) {
                            plugin.getEconomy().withdrawPlayer(player, upgradeCost);

                            int newMaxEnergy = maxEnergy + 10;
                            if (newMaxEnergy > maxEnergyLimit) {
                                newMaxEnergy = maxEnergyLimit;
                            }

                            plugin.updateMaxEnergy(player.getUniqueId(), newMaxEnergy);
                            plugin.setCurrentEnergy(player.getUniqueId(), newMaxEnergy);

                            String energyUpgradeSuccess = ChatColor.translateAlternateColorCodes('&', config.getString("messages.energy-upgrade-success", "&aYour energy has been upgraded to {new_max_energy}!"));
                            player.sendMessage(messagePrefix + energyUpgradeSuccess.replace("{new_max_energy}", String.valueOf(newMaxEnergy)));

                            open(player);
                        } else {
                            player.sendMessage(messagePrefix + upgradeFail);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(messagePrefix + upgradeError);
                    }
                } else if ("increase_regen_rate".equalsIgnoreCase(command)) {
                    try {
                        int regenRate = plugin.getRegenRate(player.getUniqueId());
                        int regenUpgradeCost = (int) Math.pow(regenRate, 3) * 2;

                        if (regenRate >= maxRegenRateLimit) {
                            player.sendMessage(messagePrefix + maxLimitReached);
                            return;
                        }

                        if (plugin.getEconomy().getBalance(player) >= regenUpgradeCost) {
                            plugin.getEconomy().withdrawPlayer(player, regenUpgradeCost);

                            int newRegenRate = regenRate + 1;
                            if (newRegenRate > maxRegenRateLimit) {
                                newRegenRate = maxRegenRateLimit;
                            }

                            plugin.updateRegenRate(player.getUniqueId(), newRegenRate);

                            String regenRateUpgradeSuccess = ChatColor.translateAlternateColorCodes('&', config.getString("messages.regen-rate-upgrade-success", "&aYour energy regen rate has been increased to {new_regen_rate} per minute"));
                            player.sendMessage(messagePrefix + regenRateUpgradeSuccess.replace("{new_regen_rate}", String.valueOf(newRegenRate)));

                            open(player);
                        } else {
                            player.sendMessage(messagePrefix + upgradeFail);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                        player.sendMessage(messagePrefix + upgradeError);
                    }
                }
            }
        }
    }

}
