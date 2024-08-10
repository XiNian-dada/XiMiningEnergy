package cn.hairuosky.ximiningenergy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MiningEnergyTabCompleter implements TabCompleter {

    private final XiMiningEnergy plugin;

    public MiningEnergyTabCompleter(XiMiningEnergy plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("miningenergy")) {
            if (args.length == 1) {
                // First argument: Subcommands
                completions.addAll(Arrays.asList("status", "upgrade", "info", "setmax", "setregen", "setcurrent", "fill", "reload", "givepotion", "addmax", "addregen"));
            } else if (args.length == 2) {
                // Second argument: Player names (for setmax, setregen, setcurrent, fill, givepotion, addmax, addregen)
                if (Arrays.asList("setmax", "setregen", "setcurrent", "fill", "givepotion", "addmax", "addregen").contains(args[0].toLowerCase())) {
                    completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                }
            } else if (args.length == 3) {
                // Third argument: Numeric values (for setmax, setregen, setcurrent, addmax, addregen) or potionKey (for givepotion)
                if (Arrays.asList("setmax", "setregen", "setcurrent").contains(args[0].toLowerCase())) {
                    completions.add("10");  // Example numeric value
                } else if (Arrays.asList("addmax", "addregen").contains(args[0].toLowerCase())) {
                    completions.add("10");  // Example numeric value
                } else if ("givepotion".equalsIgnoreCase(args[0])) {
                    completions.addAll(plugin.getHealingPotions().keySet());  // Add all potion keys from the config
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
