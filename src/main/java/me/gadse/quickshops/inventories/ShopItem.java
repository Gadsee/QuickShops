package me.gadse.quickshops.inventories;

import org.bukkit.inventory.ItemStack;

public class ShopItem {

    private final ItemStack itemStack;
    private final double buyPrice, sellPrice, buyPricePiece, sellPricePiece;

    public ShopItem(ItemStack itemStack, double buyPrice, double sellPrice) {
        this.itemStack = itemStack;
        this.buyPrice = buyPrice;
        this.buyPricePiece = buyPrice / itemStack.getAmount();
        this.sellPrice = sellPrice;
        this.sellPricePiece = sellPrice / itemStack.getAmount();
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getBuyPricePiece() {
        return buyPricePiece;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public double getSellPricePiece() {
        return sellPricePiece;
    }
}
