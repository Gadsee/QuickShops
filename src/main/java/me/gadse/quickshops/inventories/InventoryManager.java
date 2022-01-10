package me.gadse.quickshops.inventories;

import com.google.common.collect.ImmutableList;
import me.gadse.quickshops.QuickShops;
import me.gadse.quickshops.util.ColorUtil;
import me.gadse.quickshops.util.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InventoryManager implements Listener {

    private final QuickShops plugin;
    private final Map<String, ShopCategory> categoryByNameMap = new HashMap<>();

    private boolean setupCategories = true;
    private final Set<UUID> categoryViewers = new HashSet<>();
    private final Map<Integer, Button> categoryButtonMap = new HashMap<>();

    private final Inventory transactionOptionsInventory;
    private final Map<UUID, ShopCategory> transactionOptionViewerMap = new HashMap<>();
    private final Map<Integer, Button> transactionOptionButtonMap = new HashMap<>();
    private String transactionOptionTitle;
    private int transactionOptionPreviewSlot = 0;

    private final Inventory shopBaseInventory;
    private final Map<UUID, ShopCategory> shopViewers = new HashMap<>();
    private final Map<Integer, Button> shopBaseButtonMap = new HashMap<>();
    private Button shopPreviousPageButton, shopNextPageButton, shopBaseItemStack;

    public InventoryManager(QuickShops plugin) {
        this.plugin = plugin;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        shopBaseInventory = loadCategoryBaseInventory();
        transactionOptionsInventory = loadTransactionOptionsInventory();

        ConfigurationSection shopConfig = plugin.getConfig().getConfigurationSection("shops");
        if (shopConfig == null)
            return;

        shopConfig.getKeys(false).forEach(shopId -> categoryByNameMap.put(shopId, new ShopCategory(plugin, shopId, this)));
    }

    private Inventory loadCategoryBaseInventory() {
        Inventory inventory = prepareBaseInventory("shop-base", shopBaseButtonMap);
        if (inventory == null)
            return null;

        shopPreviousPageButton = plugin.getButtonFromConfig("shop-base.items.previous-page");
        shopPreviousPageButton.setClick(event -> {
            if (event.getCurrentItem() == null || !event.getCurrentItem().equals(shopPreviousPageButton.getItemStack()))
                return;

            ShopCategory shopCategory = shopViewers.get(event.getWhoClicked().getUniqueId());
            if (shopCategory == null)
                return;

            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    shopCategory.openPreviousPage(event.getWhoClicked()), 1L);
        });
        shopPreviousPageButton.getSlots().forEach(slot -> shopBaseButtonMap.put(slot, shopPreviousPageButton));

        shopNextPageButton = plugin.getButtonFromConfig("shop-base.items.next-page");
        shopNextPageButton.setClick(event -> {
            if (event.getCurrentItem() == null || !event.getCurrentItem().equals(shopNextPageButton.getItemStack()))
                return;

            ShopCategory shopCategory = shopViewers.get(event.getWhoClicked().getUniqueId());
            if (shopCategory == null)
                return;

            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    shopCategory.openNextPage(event.getWhoClicked()), 1L);
        });
        shopNextPageButton.getSlots().forEach(slot -> shopBaseButtonMap.put(slot, shopNextPageButton));

        Button backButton = plugin.getButtonFromConfig("shop-base.items.back");
        backButton.setClick(event ->
                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        openCategoryInventory(event.getWhoClicked()), 1L));
        backButton.setToInventory(inventory, shopBaseButtonMap);

        shopBaseItemStack = plugin.getButtonFromConfig("shop-base.items.shop-item");
        shopBaseItemStack.setClick(event -> {
            if (event.getCurrentItem() == null || event.getCurrentItem().equals(shopBaseItemStack.getItemStack()))
                return;

            ShopCategory shopCategory = shopViewers.get(event.getWhoClicked().getUniqueId());
            if (shopCategory == null)
                return;

            if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT)
                shopCategory.attemptPurchase(plugin.getEconomy(), event.getWhoClicked(), event.getCurrentItem(), null);

            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT)
                shopCategory.attemptSellAll(plugin.getEconomy(), event.getWhoClicked(), event.getCurrentItem());

            if (event.getClick() == ClickType.MIDDLE) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    Inventory transactionInventory = plugin.getServer().createInventory(null,
                            transactionOptionsInventory.getSize(),
                            transactionOptionTitle);

                    for (int i = 0; i < transactionOptionsInventory.getSize(); i++) {
                        ItemStack itemStack = transactionOptionsInventory.getItem(i);
                        if (itemStack == null)
                            continue;
                        transactionInventory.setItem(i, itemStack);
                    }

                    transactionInventory.setItem(transactionOptionPreviewSlot, event.getCurrentItem());

                    event.getWhoClicked().openInventory(transactionInventory);
                    transactionOptionViewerMap.put(event.getWhoClicked().getUniqueId(), shopCategory);
                }, 1L);
            }
        });
        shopBaseItemStack.getSlots().forEach(slot -> shopBaseButtonMap.put(slot, shopBaseItemStack));

        return inventory;
    }

    private Inventory loadTransactionOptionsInventory() {
        Inventory inventory = prepareBaseInventory("transaction-options", transactionOptionButtonMap);

        Button backButton = plugin.getButtonFromConfig("transaction-options.items.back");
        backButton.setClick(event -> {
            ShopCategory shopCategory = transactionOptionViewerMap.get(event.getWhoClicked().getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (shopCategory == null)
                    event.getWhoClicked().closeInventory();
                else
                    openShopInventory(event.getWhoClicked(), shopCategory, false);
            }, 1L);
        });
        backButton.setToInventory(inventory, transactionOptionButtonMap);

        transactionOptionPreviewSlot = plugin.getConfig().getInt("transaction-options.items.preview-item.slot");
        transactionOptionTitle = ColorUtil.color(plugin.getConfig().getString("transaction-options.title"));

        ConfigurationSection buyConfig = plugin.getConfig().getConfigurationSection("transaction-options.items.transaction-options.buy");
        if (buyConfig != null) {
            buyConfig.getKeys(false).forEach(buyId -> {
                Button button = plugin.getButtonFromConfig(buyConfig.getCurrentPath() + "." + buyId);
                button.setClick(event -> {
                    ShopCategory shopCategory = transactionOptionViewerMap.get(event.getWhoClicked().getUniqueId());
                    if (shopCategory == null)
                        return;

                    shopCategory.attemptPurchase(plugin.getEconomy(),
                            event.getWhoClicked(),
                            event.getInventory().getItem(transactionOptionPreviewSlot),
                            button.getItemStack().getAmount());
                });
                button.setToInventory(inventory, transactionOptionButtonMap);
            });
        }

        ConfigurationSection sellConfig = plugin.getConfig().getConfigurationSection("transaction-options.items.transaction-options.sell");
        if (sellConfig != null) {
            sellConfig.getKeys(false).forEach(sellId -> {
                Button button = plugin.getButtonFromConfig(sellConfig.getCurrentPath() + "." + sellId);
                button.setClick(event -> {
                    ShopCategory shopCategory = transactionOptionViewerMap.get(event.getWhoClicked().getUniqueId());
                    if (shopCategory == null)
                        return;

                    shopCategory.attemptSell(plugin.getEconomy(),
                            event.getWhoClicked(),
                            event.getInventory().getItem(transactionOptionPreviewSlot),
                            button.getItemStack().getAmount());
                });
                button.setToInventory(inventory, transactionOptionButtonMap);
            });
        }

        return inventory;
    }

    public void openCategoryInventory(HumanEntity player) {
        if (categoryViewers.contains(player.getUniqueId()))
            return;

        Inventory inventory = prepareBaseInventory("category-overview", setupCategories ? categoryButtonMap : null);
        if (inventory == null)
            return;

        Button closeButton = plugin.getButtonFromConfig("category-overview.items.close");
        closeButton.setClick(event ->
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> event.getWhoClicked().closeInventory(), 1L));
        closeButton.setToInventory(inventory, setupCategories ? categoryButtonMap : null);

        ConfigurationSection categories = plugin.getConfig().getConfigurationSection("category-overview.items.categories");
        if (categories != null) {
            categories.getKeys(false).forEach(categoryId -> {
                String categoryName = categories.getString(categoryId + ".category-name");
                if (categoryName == null) {
                    plugin.getLogger().warning("The category-name for " + categoryId + " has not been configured.");
                    return;
                }

                ShopCategory shopCategory = categoryByNameMap.get(categoryName);
                if (shopCategory == null) {
                    plugin.getLogger().warning("The category " + categoryName + " does not exist.");
                    return;
                }

                String option = player.hasPermission(shopCategory.getPermission()) ? "accessible" : "inaccessible";
                ItemStack itemStack = plugin.getItemStackFromConfig(categories.getCurrentPath() +
                        "." + categoryId +
                        "." + option);
                Button button = new Button(itemStack, ImmutableList.of(categories.getInt(categoryId + ".slot")));
                button.setClick(event -> plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        openShopInventory(event.getWhoClicked(), shopCategory, false), 1L));
                button.setToInventory(inventory, setupCategories ? categoryButtonMap : null);
            });
        }
        setupCategories = false;

        player.openInventory(inventory);
        categoryViewers.add(player.getUniqueId());
    }

    private Inventory prepareBaseInventory(String configurationSection, Map<Integer, Button> buttonMap) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(configurationSection);
        if (config == null) {
            plugin.getLogger().severe("Could not load the inventory for " + configurationSection + ".");
            return null;
        }

        Inventory inventory = plugin.getServer().createInventory(null,
                config.getInt("size"),
                ColorUtil.color(config.getString("title")));

        ConfigurationSection itemConfig = config.getConfigurationSection("items");
        if (itemConfig == null)
            return inventory;

        ConfigurationSection placeholders = itemConfig.getConfigurationSection("placeholders");
        if (placeholders != null) {
            placeholders.getKeys(false).forEach(placeholderId -> plugin
                    .getButtonFromConfig(placeholders.getCurrentPath() + "." + placeholderId)
                    .setToInventory(inventory, buttonMap, true));
        }

        return inventory;
    }

    public void openShopInventory(HumanEntity player, ShopCategory shopCategory, boolean bypassPermission) {
        if (!player.hasPermission(shopCategory.getPermission()) && !bypassPermission) {
            Messages.NO_PERMISSION.send(player);
            return;
        }
        player.openInventory(shopCategory.getInventory()[0]);
        shopViewers.put(player.getUniqueId(), shopCategory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null)
            return;

        boolean categoryViewer = categoryViewers.contains(event.getWhoClicked().getUniqueId());
        boolean shopViewer = shopViewers.containsKey(event.getWhoClicked().getUniqueId());
        boolean transactionViewer = transactionOptionViewerMap.containsKey(event.getWhoClicked().getUniqueId());

        if (!categoryViewer && !shopViewer && !transactionViewer)
            return;

        if (event.getView().getBottomInventory().equals(event.getClickedInventory())) {
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                    || (event.isShiftClick() && event.getView().getTopInventory().firstEmpty() != -1))
                event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Button button;
        if (categoryViewer)
            button = categoryButtonMap.get(event.getRawSlot());
        else if (shopViewer)
            button = shopBaseButtonMap.get(event.getRawSlot());
        else
            button = transactionOptionButtonMap.get(event.getRawSlot());

        if (button == null)
            return;

        button.runClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (categoryViewers.contains(event.getWhoClicked().getUniqueId())
                || shopViewers.containsKey(event.getWhoClicked().getUniqueId())
                || transactionOptionViewerMap.containsKey(event.getWhoClicked().getUniqueId()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ShopCategory shopCategory = shopViewers.get(uuid);
        if (shopCategory != null && !shopCategory.skipRemoval(event.getPlayer())) {
            shopCategory.close(event.getPlayer());
            shopViewers.remove(uuid);
        }

        ShopCategory shopCategory1 = transactionOptionViewerMap.get(uuid);
        if (shopCategory1 != null) {
            transactionOptionViewerMap.remove(uuid);
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    openShopInventory(event.getPlayer(), shopCategory1, false), 1L);
        }

        categoryViewers.remove(uuid);
    }

    public void unregister() {
        InventoryCloseEvent.getHandlerList().unregister(this);
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryDragEvent.getHandlerList().unregister(this);

        transactionOptionViewerMap.keySet().forEach(uuid -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null)
                player.closeInventory();
        });
        transactionOptionViewerMap.clear();

        categoryViewers.forEach(uuid -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null)
                player.closeInventory();
        });
        categoryViewers.clear();

        shopViewers.forEach((uuid, shopCategory) -> {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null)
                player.closeInventory();
        });
        shopViewers.clear();

        categoryByNameMap.values().forEach(shopCategory ->
                plugin.getServer().getPluginManager().removePermission(shopCategory.getPermission()));
        categoryByNameMap.clear();

        categoryButtonMap.clear();
        shopBaseButtonMap.clear();
        transactionOptionButtonMap.clear();
    }

    public Button getShopBaseItemStack() {
        return shopBaseItemStack;
    }

    public Inventory getShopBaseInventory() {
        return shopBaseInventory;
    }

    public Button getShopNextPageButton() {
        return shopNextPageButton;
    }

    public Button getShopPreviousPageButton() {
        return shopPreviousPageButton;
    }

    @Nullable
    public ShopCategory getShopCategory(String shopCategoryId) {
        return categoryByNameMap.get(shopCategoryId);
    }

    public Set<String> getShopCategories() {
        return categoryByNameMap.keySet();
    }
}
