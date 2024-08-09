package cn.hairuosky.ximiningenergy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.UUID;

public class MiningEnergyPlaceholder extends PlaceholderExpansion {

    private final XiMiningEnergy plugin;

    public MiningEnergyPlaceholder(XiMiningEnergy plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "miningenergy";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // This is required for the placeholder to be persistent between reloads
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();

        try {
            int currentEnergy = plugin.getCurrentEnergy(uuid);
            int maxEnergy = plugin.getMaxEnergy(uuid);

            switch (identifier) {
                case "current_energy":
                    return String.valueOf(currentEnergy);

                case "max_energy":
                    return String.valueOf(maxEnergy);

                case "upgrade_cost":
                    int upgradeCost = (int) Math.ceil(Math.pow(currentEnergy, 2) / 20.0);
                    return String.valueOf(upgradeCost);

                case "time_to_full_energy":
                    int regenRate = plugin.getRegenRate(uuid);

                    if (currentEnergy >= maxEnergy) {
                        return "0 minutes";
                    }

                    int energyNeeded = maxEnergy - currentEnergy;
                    int minutesToFull = (int) Math.ceil((double) energyNeeded / regenRate);
                    return minutesToFull + " minutes";

                default:
                    return null; // 未知的占位符
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "SQL Error";
        }
    }

}
