package me.gadse.quickshops.command;

import com.google.common.collect.ImmutableSet;
import me.gadse.quickshops.QuickShops;
import me.gadse.quickshops.inventories.ShopCategory;
import me.gadse.quickshops.util.Messages;
import me.gadse.quickshops.util.Pair;
import me.gadse.quickshops.util.RegexUtil;
import org.bukkit.command.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final QuickShops plugin;
    private final Set<String> args_zero = ImmutableSet.of("reload", "open", "help"),
            args_three = ImmutableSet.of("true", "false");

    public ShopCommand(QuickShops plugin) {
        this.plugin = plugin;

        PluginCommand pluginCommand = plugin.getCommand("shop");
        if (pluginCommand != null)
            pluginCommand.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player)
                plugin.getInventoryManager().openCategoryInventory((HumanEntity) sender);
            return true;
        }

        String subArgument = args[0].toLowerCase();

        if (!args_zero.contains(subArgument))
            subArgument = "help";

        if (!sender.hasPermission("quickshops." + subArgument)) {
            Messages.NO_PERMISSION.send(sender);
            return true;
        }

        switch (subArgument) {
            case "reload": {
                plugin.reloadConfig();
                Messages.RELOAD.send(sender);
                break;
            }
            case "open": {
                if (args.length < 2) {
                    sender.sendMessage("/" + label + " open <target> [category]");
                    break;
                }

                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    Messages.TARGET_NOT_ONLINE.send(sender, new Pair<>(RegexUtil.TARGET, args[1]));
                    break;
                }

                boolean bypassPermission = args.length > 3 && Boolean.parseBoolean(args[3]);

                Messages message;
                if (args.length > 2) {
                    ShopCategory shopCategory = plugin.getInventoryManager().getShopCategory(args[2]);
                    if (shopCategory == null) {
                        Messages.CATEGORY_DOES_NOT_EXIST.send(sender, new Pair<>(RegexUtil.VALUE, args[2]));
                        break;
                    }

                    plugin.getInventoryManager().openShopInventory(target, shopCategory, bypassPermission);
                    message = Messages.CATEGORY_OPENED_FOR_TARGET;
                } else {
                    plugin.getInventoryManager().openCategoryInventory(target);
                    message = Messages.SHOP_OPENED_FOR_TARGET;
                }

                if (!sender.equals(target))
                    message.send(sender,
                            new Pair<>(RegexUtil.VALUE, args[1]),
                            new Pair<>(RegexUtil.TARGET, target.getName()));
                break;
            }
            case "help": {
                sender.sendMessage("/" + label + " - Open the shop.");

                if (sender.hasPermission("quickshops.reload"))
                    sender.sendMessage("/" + label + " reload - Reload the configuration.");

                if (sender.hasPermission("quickshops.open"))
                    sender.sendMessage("/" + label + " open <target> [category] [bypassPermission] " +
                            "- Force open the shop or a category.");
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> args_zero_copy = new ArrayList<>(args_zero);
            args_zero_copy.removeIf(arg -> !sender.hasPermission("quickshops." + arg));
            if (args_zero_copy.isEmpty())
                return null;
            return StringUtil.copyPartialMatches(args[0], args_zero_copy, new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("open") && sender.hasPermission("quickshops.open"))
            return StringUtil.copyPartialMatches(args[2], plugin.getInventoryManager().getShopCategories(), new ArrayList<>());


        if (args.length == 4 && args[0].equalsIgnoreCase("open") && sender.hasPermission("quickshops.open"))
            return StringUtil.copyPartialMatches(args[3], args_three, new ArrayList<>());

        return null;
    }
}
