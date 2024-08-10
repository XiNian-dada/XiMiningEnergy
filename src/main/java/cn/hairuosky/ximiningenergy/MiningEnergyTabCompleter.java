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
                // 第一个参数: 主命令的子命令
                completions.addAll(Arrays.asList("status", "upgrade", "info", "setmax", "setregen", "setcurrent", "fill", "reload", "givepotion"));
            } else if (args.length == 2) {
                // 第二个参数: 玩家名字（适用于 setmax, setregen, setcurrent, fill, givepotion）
                if (Arrays.asList("setmax", "setregen", "setcurrent", "fill", "givepotion").contains(args[0].toLowerCase())) {
                    completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList()));
                }
            } else if (args.length == 3) {
                // 第三个参数: 数值（适用于 setmax, setregen, setcurrent）或 potionKey (适用于 givepotion)
                if (Arrays.asList("setmax", "setregen", "setcurrent").contains(args[0].toLowerCase())) {
                    completions.add("10");  // 示例数值
                } else if ("givepotion".equalsIgnoreCase(args[0])) {
                    completions.addAll(plugin.getHealingPotions().keySet());  // 添加配置文件中的所有药水键值
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
