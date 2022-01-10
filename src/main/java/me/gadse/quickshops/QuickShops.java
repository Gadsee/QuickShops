package me.gadse.quickshops;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.gadse.quickshops.command.ShopCommand;
import me.gadse.quickshops.inventories.Button;
import me.gadse.quickshops.inventories.InventoryManager;
import me.gadse.quickshops.util.ColorUtil;
import me.gadse.quickshops.util.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuickShops extends JavaPlugin {

    private InventoryManager inventoryManager;
    private Economy economy;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No economy providing plugin has been identified.");
            return;
        }
        economy = rsp.getProvider();

        if (!getDataFolder().exists() && !getDataFolder().mkdirs())
            getLogger().warning("Could not create plugin folder. Config will be internal only.");
        saveDefaultConfig();

        new ShopCommand(this);

        reloadConfig();
    }

    @Override
    public void reloadConfig() {
        if (inventoryManager != null)
            inventoryManager.unregister();

        super.reloadConfig();

        for (Messages message : Messages.values())
            message.reloadMessage(this);

        inventoryManager = new InventoryManager(this);
    }

    @Override
    public void onDisable() {
        if (inventoryManager != null)
            inventoryManager.unregister();
    }

    public Economy getEconomy() {
        return economy;
    }

    public InventoryManager getInventoryManager() {
        return inventoryManager;
    }

    public ItemStack getItemStackFromConfig(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section == null)
            throw new IllegalArgumentException("Path '" + path + "' does not exist.");

        String materialString = section.getString("type");
        if (materialString == null || materialString.isEmpty()) {
            getLogger().warning(path + ".material was empty or did not exist. Falling back to dirt.");
            materialString = "dirt";
        }

        Material material = Material.getMaterial(materialString.toUpperCase());
        if (material == null) {
            getLogger().warning(materialString + " is not a valid material. Falling back to dirt.");
            material = Material.DIRT;
        }

        ItemStack itemStack = new ItemStack(material, section.getInt("amount", 1));

        if (itemStack.getType() == Material.AIR || itemStack.getItemMeta() == null) {
            getLogger().warning("'" + path + "' is AIR. Make sure that is intentional.");
            return itemStack;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();

        String displayName;
        if ((displayName = section.getString("displayName")) != null && !displayName.isEmpty())
            itemMeta.setDisplayName(ColorUtil.color(section.getString("displayName")));

        itemMeta.setLore(ColorUtil.color(section.getStringList("lore")));

        ConfigurationSection enchants = section.getConfigurationSection("enchantments");
        if (enchants != null) {
            enchants.getKeys(false).forEach((key) -> {
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
                if (enchantment == null) {
                    getLogger().warning("The enchantment for the key '" + key + "' was invalid. Skipping it.");
                } else {
                    itemMeta.addEnchant(enchantment, enchants.getInt(key), true);
                }
            });
        }

        section.getStringList("flags").forEach(flag -> {
            try {
                itemMeta.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                getLogger().warning("The item flag " + flag + " does not exist.");
            }
        });

        if (itemMeta instanceof SkullMeta)
            setProfileToMeta((SkullMeta) itemMeta, section.getString("texture"));

        itemStack.setItemMeta(itemMeta);
        return itemStack.clone();
    }

    private Method setProfileMethod;

    public void setProfileToMeta(SkullMeta skullMeta, String texture) {
        if (texture == null || texture.length() < 100)
            return;

        try {
            // Make setProfile accessible through reflection
            if (setProfileMethod == null) {
                setProfileMethod = skullMeta.getClass()
                        .getDeclaredMethod("setProfile", GameProfile.class);
                setProfileMethod.setAccessible(true);
            }

            // Create game profile based off texture
            UUID uuid = new UUID(
                    texture.substring(texture.length() - 20).hashCode(),
                    texture.substring(texture.length() - 10).hashCode()
            );
            GameProfile gameProfile = new GameProfile(uuid, texture.substring(88, 100));
            gameProfile.getProperties().put("textures", new Property("textures", texture));

            // Set texture to meta
            setProfileMethod.invoke(skullMeta, gameProfile);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            ex.printStackTrace();
        }
    }

    public Button getButtonFromConfig(String path) {
        ItemStack itemStack = getItemStackFromConfig(path);

        List<Integer> slots;
        if (getConfig().contains(path + ".slots"))
            slots = getConfig().getIntegerList(path + ".slots");
        else if (getConfig().contains(path + ".slot"))
            slots = ImmutableList.of(getConfig().getInt(path + ".slot"));
        else
            slots = new ArrayList<>();

        return new Button(itemStack, slots, getConfig().getStringList(path + ".commands"));
    }
}
