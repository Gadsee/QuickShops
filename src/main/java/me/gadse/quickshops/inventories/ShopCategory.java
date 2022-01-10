package me.gadse.quickshops.inventories;

import me.gadse.quickshops.QuickShops;
import me.gadse.quickshops.util.ColorUtil;
import me.gadse.quickshops.util.Messages;
import me.gadse.quickshops.util.Pair;
import me.gadse.quickshops.util.RegexUtil;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.*;

public class ShopCategory {
    private final QuickShops plugin;
    private final String id;
    private final Inventory[] inventory;
    private final Map<UUID, Integer> pageMap = new HashMap<>();

    private final Map<ItemStack, ShopItem> itemStackPrices = new HashMap<>();

    private final Permission permission;

    public ShopCategory(QuickShops plugin, String id, InventoryManager inventoryManager) {
        this.plugin = plugin;
        this.id = id;

        ConfigurationSection shopConfig = plugin.getConfig().getConfigurationSection("shops." + id);
        if (shopConfig == null)
            throw new IllegalArgumentException("Shop " + id + " not configured.");

        ConfigurationSection itemConfig = shopConfig.getConfigurationSection("items");
        if (itemConfig == null)
            throw new IllegalArgumentException("The shop " + id + " does not have any items configured.");

        Inventory baseInventory = inventoryManager.getShopBaseInventory();
        List<String> items = new ArrayList<>(itemConfig.getKeys(false));
        List<Integer> baseSlots = inventoryManager.getShopBaseItemStack().getSlots();
        int baseSlotSize = baseSlots.size();
        int pages = (int) Math.ceil(
                items.size() / (double) baseSlotSize
        );

        inventory = new Inventory[pages];
        for (int page = 0; page < pages; page++) {
            inventory[page] = plugin.getServer().createInventory(null,
                    baseInventory.getSize(),
                    ColorUtil.color(shopConfig.getString("title")));

            for (int i = 0; i < baseInventory.getSize(); i++) {
                ItemStack itemStack = baseInventory.getItem(i);
                if (itemStack == null || itemStack.getType() == Material.AIR)
                    continue;
                inventory[page].setItem(i, itemStack);
            }

            if (page > 0)
                inventoryManager.getShopPreviousPageButton().setToInventory(inventory[page], null);
            else if (page + 1 < pages)
                inventoryManager.getShopNextPageButton().setToInventory(inventory[page], null);

            ItemStack baseItem = inventoryManager.getShopBaseItemStack().getItemStack();
            ItemMeta baseMeta = baseItem.getItemMeta();

            for (int i = 0; i < baseSlotSize; i++) {
                int currentIndex = (page * baseSlotSize) + i;
                if (items.size() < currentIndex + 1)
                    break;

                String itemId = items.get(currentIndex);

                ItemStack itemStack = plugin.getItemStackFromConfig(itemConfig.getCurrentPath() + "." + itemId);
                double buyPrice = itemConfig.getDouble(itemId + ".buy", -1);
                double sellPrice = itemConfig.getDouble(itemId + ".sell", -1);

                ShopItem shopItem = new ShopItem(itemStack.clone(), buyPrice, sellPrice);

                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null && baseMeta != null) {
                    if (baseMeta.getLore() != null) {
                        List<String> lore = itemMeta.getLore() != null ? itemMeta.getLore() : new ArrayList<>();

                        baseMeta.getLore().forEach(line -> {
                            line = RegexUtil.BUY_PRICE.replaceAll(line, buyPrice >= 0
                                    ? Messages.CURRENCY.getMessage() + buyPrice
                                    : Messages.NO_PRICE.getMessage());

                            line = RegexUtil.SELL_PRICE.replaceAll(line, sellPrice >= 0
                                    ? Messages.CURRENCY.getMessage() + sellPrice
                                    : Messages.NO_PRICE.getMessage());

                            Messages transactionOption = buyPrice >= 0 && sellPrice >= 0
                                    ? Messages.PURCHASEABLE_AND_SELLABLE
                                    : buyPrice >= 0
                                    ? Messages.PURCHASEABLE_ONLY
                                    : Messages.SELLABLE_ONLY;
                            lore.add(RegexUtil.TRANSACTION_OPTIONS.replaceAll(line, transactionOption.getMessage()));
                        });

                        itemMeta.setLore(lore);
                    }

                    itemStack.setItemMeta(itemMeta);
                }
                inventory[page].setItem(baseSlots.get(i), itemStack);

                itemStackPrices.put(itemStack, shopItem);
            }
        }

        permission = new Permission("quickshops.category." + id, PermissionDefault.OP);
        plugin.getServer().getPluginManager().addPermission(permission);
    }

    public String getId() {
        return id;
    }

    public Permission getPermission() {
        return permission;
    }

    public Inventory[] getInventory() {
        return inventory;
    }

    private final Set<UUID> skipInventoryClose = new HashSet<>();

    public void openPreviousPage(HumanEntity player) {
        int page = pageMap.getOrDefault(player.getUniqueId(), 0);
        if (page > 0) {
            pageMap.put(player.getUniqueId(), page - 1);
            skipInventoryClose.add(player.getUniqueId());
            player.openInventory(inventory[page - 1]);
            skipInventoryClose.remove(player.getUniqueId());
        }
    }

    public void openNextPage(HumanEntity player) {
        int page = pageMap.getOrDefault(player.getUniqueId(), 0);
        if (inventory.length >= page + 2) {
            pageMap.put(player.getUniqueId(), page + 1);
            skipInventoryClose.add(player.getUniqueId());
            player.openInventory(inventory[page + 1]);
            skipInventoryClose.remove(player.getUniqueId());
        }
    }

    public boolean skipRemoval(HumanEntity player) {
        return skipInventoryClose.contains(player.getUniqueId());
    }

    public void attemptPurchase(Economy economy, HumanEntity humanEntity, ItemStack itemStack, Integer amount) {
        ShopItem shopItem = itemStackPrices.get(itemStack);
        if (shopItem == null)
            return;

        if (shopItem.getBuyPrice() < 0)
            return;

        if (!(humanEntity instanceof Player))
            return;
        Player player = (Player) humanEntity;

        double price = amount == null ? shopItem.getBuyPrice() : shopItem.getBuyPricePiece() * amount;
        if (!economy.has(player, price)) {
            Messages.NOT_ENOUGH_MONEY.send(player);
            return;
        }
        economy.withdrawPlayer(player, price);

        ItemStack shopItemStack = shopItem.getItemStack();
        if (amount != null)
            shopItemStack.setAmount(amount);

        while (shopItemStack.getAmount() >= shopItemStack.getMaxStackSize()) {
            shopItemStack.setAmount(shopItemStack.getAmount() - shopItemStack.getMaxStackSize());
            ItemStack clone = shopItemStack.clone();
            clone.setAmount(shopItemStack.getMaxStackSize());
            player.getInventory().addItem(clone)
                    .forEach(((integer, leftOver) -> player.getWorld().dropItem(player.getLocation(), leftOver)));
        }

        if (shopItemStack.getAmount() > 0)
            player.getInventory().addItem(shopItemStack)
                    .forEach(((integer, leftOver) -> player.getWorld().dropItem(player.getLocation(), leftOver)));

        Messages.ITEM_PURCHASED.send(player,
                new Pair<>(RegexUtil.ITEM, itemNameToHumanFriendly(itemStack)),
                new Pair<>(RegexUtil.AMOUNT, amount == null ? shopItem.getItemStack().getAmount() : amount),
                new Pair<>(RegexUtil.PRICE, price));

        if (plugin.getConfig().getBoolean("log_transactions")) {
            Messages.ITEM_PURCHASED_LOG.send(plugin.getServer().getConsoleSender(),
                    new Pair<>(RegexUtil.PLAYER, player.getName()),
                    new Pair<>(RegexUtil.ITEM, itemNameToHumanFriendly(itemStack)),
                    new Pair<>(RegexUtil.AMOUNT, amount == null ? shopItem.getItemStack().getAmount() : amount),
                    new Pair<>(RegexUtil.PRICE, price));
        }
    }

    public void attemptSell(Economy economy, HumanEntity humanEntity, ItemStack itemStack, Integer amount) {
        ShopItem shopItem = itemStackPrices.get(itemStack);
        if (shopItem == null)
            return;

        if (shopItem.getSellPrice() < 0)
            return;

        if (!(humanEntity instanceof Player))
            return;
        Player player = (Player) humanEntity;

        int sellAmount = amount == null ? shopItem.getItemStack().getAmount() : amount;
        Pair<RegexUtil, Object> itemName = new Pair<>(RegexUtil.ITEM, itemNameToHumanFriendly(shopItem.getItemStack()));
        for (ItemStack content : humanEntity.getInventory().getStorageContents()) {
            if (content == null || !content.isSimilar(shopItem.getItemStack()))
                continue;

            double price;
            double contentAmount;
            if (content.getAmount() >= sellAmount) {
                content.setAmount(content.getAmount() - sellAmount);
                price = sellAmount * shopItem.getSellPricePiece();
                contentAmount = sellAmount;
            } else {
                contentAmount = content.getAmount();
                content.setAmount(0);
                price = contentAmount * shopItem.getSellPricePiece();
            }

            economy.depositPlayer(player, price);
            Messages.ITEM_SOLD.send(player, itemName,
                    new Pair<>(RegexUtil.AMOUNT, contentAmount),
                    new Pair<>(RegexUtil.PRICE, price));

            if (plugin.getConfig().getBoolean("log_transactions")) {
                Messages.ITEM_SOLD_LOG.send(plugin.getServer().getConsoleSender(),
                        new Pair<>(RegexUtil.PLAYER, player.getName()),
                        new Pair<>(RegexUtil.ITEM, itemNameToHumanFriendly(itemStack)),
                        new Pair<>(RegexUtil.AMOUNT, contentAmount),
                        new Pair<>(RegexUtil.PRICE, price));
            }
            break;
        }
    }

    public void attemptSellAll(Economy economy, HumanEntity humanEntity, ItemStack itemStack) {
        ShopItem shopItem = itemStackPrices.get(itemStack);
        if (shopItem == null)
            return;

        if (shopItem.getSellPrice() < 0)
            return;

        if (!(humanEntity instanceof Player))
            return;
        Player player = (Player) humanEntity;

        Pair<RegexUtil, Object> itemName = new Pair<>(RegexUtil.ITEM, itemNameToHumanFriendly(shopItem.getItemStack()));
        int totalAmount = 0;
        double totalPrice = 0;
        for (ItemStack content : humanEntity.getInventory().getStorageContents()) {
            if (content == null || !content.isSimilar(shopItem.getItemStack()))
                continue;

            while (content.getAmount() >= shopItem.getItemStack().getAmount()) {
                content.setAmount(content.getAmount() - shopItem.getItemStack().getAmount());
                economy.depositPlayer(player, shopItem.getSellPrice());

                totalAmount += shopItem.getItemStack().getAmount();
                totalPrice += shopItem.getSellPrice();
            }

            if (content.getAmount() > 0) {
                humanEntity.getInventory().removeItem(content);
                economy.depositPlayer(player, shopItem.getSellPricePiece() * content.getAmount());

                totalAmount += content.getAmount();
                totalPrice += shopItem.getSellPrice() * content.getAmount();
            }
        }

        if (totalAmount == 0)
            return;

        Messages.ITEM_SOLD.send(player, itemName,
                new Pair<>(RegexUtil.AMOUNT, totalAmount),
                new Pair<>(RegexUtil.PRICE, totalPrice));
    }

    private String itemNameToHumanFriendly(ItemStack itemStack) {
        return WordUtils.capitalizeFully(String.join(" ", itemStack.getType().toString().split("_")));
    }

    public ShopItem getShopItem(ItemStack itemStack) {
        return itemStackPrices.get(itemStack);
    }

    public void close(HumanEntity humanEntity) {
        pageMap.remove(humanEntity.getUniqueId());
    }
}
