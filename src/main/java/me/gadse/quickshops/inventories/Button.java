package me.gadse.quickshops.inventories;

import me.gadse.quickshops.util.RegexUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Button {

    private final ItemStack itemStack;
    private final List<Integer> slots;
    private Consumer<InventoryClickEvent> consumer;
    private final List<String> commands;

    public Button(ItemStack itemStack, List<Integer> slots) {
        this(itemStack, slots, new ArrayList<>());
    }

    public Button(ItemStack itemStack, List<Integer> slots, List<String> commands) {
        this(itemStack, slots, commands, null);
    }

    public Button(ItemStack itemStack, List<Integer> slots, List<String> commands, Consumer<InventoryClickEvent> consumer) {
        this.itemStack = itemStack;
        this.slots = slots;
        this.commands = commands;
        this.consumer = consumer;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public void setToInventory(Inventory inventory, Map<Integer, Button> buttonMap) {
        setToInventory(inventory, buttonMap, false);
    }

    public void setToInventory(Inventory inventory, Map<Integer, Button> buttonMap, boolean fill) {
        if (slots.isEmpty() && fill) {
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, itemStack);
                if (buttonMap != null)
                    buttonMap.put(i, this);
            }
            return;
        }

        slots.forEach(slot -> {
            inventory.setItem(slot, itemStack);
            if (buttonMap != null)
                buttonMap.put(slot, this);
        });
    }

    public List<Integer> getSlots() {
        return slots;
    }

    public void setClick(Consumer<InventoryClickEvent> event) {
        this.consumer = event;
    }

    public void runClick(InventoryClickEvent event) {
        if (consumer != null)
            consumer.accept(event);

        Player player = (Player) event.getWhoClicked();
        commands.forEach(command -> {
            command = RegexUtil.PLAYER.replaceAll(command, event.getWhoClicked().getName());
            if (command.startsWith("[player] "))
                player.performCommand(command.replace("[player] ", ""));
            else
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("[console] ", ""));
        });
    }

    public Button clone() {
        try {
            super.clone();
        } catch (CloneNotSupportedException ignored) {
        }
        return new Button(itemStack.clone(), new ArrayList<>(slots), new ArrayList<>(commands), consumer);
    }
}
