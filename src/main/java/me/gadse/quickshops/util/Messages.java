package me.gadse.quickshops.util;

import me.gadse.quickshops.QuickShops;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public enum Messages {

    PREFIX(false),
    NO_PERMISSION,
    RELOAD,
    TARGET_NOT_ONLINE,
    NOT_ENOUGH_MONEY,
    ITEM_PURCHASED,
    ITEM_PURCHASED_LOG,
    ITEM_SOLD,
    ITEM_SOLD_LOG,
    CATEGORY_DOES_NOT_EXIST,
    SHOP_OPENED_FOR_TARGET,
    CATEGORY_OPENED_FOR_TARGET,

    PURCHASEABLE_AND_SELLABLE(false),
    PURCHASEABLE_ONLY(false),
    SELLABLE_ONLY(false),
    NO_PRICE(false),
    CURRENCY(false);

    private final boolean showPrefix;
    private String message = ChatColor.RED + "ERROR LOADING MESSAGE FOR " + name();

    Messages() {
        this(true);
    }

    Messages(boolean showPrefix) {
        this.showPrefix = showPrefix;
    }

    public void reloadMessage(QuickShops plugin) {
        message = ColorUtil.color(plugin.getConfig().getString("messages." + name().toLowerCase()));
    }

    @SafeVarargs
    public final void send(CommandSender sender, Pair<RegexUtil, Object>... placeholders) {
        String tempMessage = message;

        for (Pair<RegexUtil, Object> placeholder : placeholders)
            tempMessage = placeholder.getKey().replaceAll(tempMessage, placeholder.getValue().toString());

        sender.sendMessage((showPrefix ? PREFIX.getMessage() : "") + tempMessage);
    }

    public @NotNull String getMessage() {
        return message;
    }
}
