package me.gadse.quickshops.util;

import org.bukkit.ChatColor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern REPLACE_ALL_PATTERN = Pattern.compile("(&)?&([0-9a-fk-orA-FK-OR])");
    private final static Pattern REPLACE_ALL_RGB_PATTERN = Pattern.compile("(&)?&#([0-9a-fA-F]{6})");

    public static String color(String input) {
        if (input == null || input.isEmpty())
            return "";

        final StringBuffer legacyBuilder = new StringBuffer();
        final Matcher legacyMatcher = REPLACE_ALL_PATTERN.matcher(input);
        legacyLoop:
        while (legacyMatcher.find()) {
            final boolean isEscaped = legacyMatcher.group(1) != null;
            if (!isEscaped) {
                final char code = legacyMatcher.group(2).toLowerCase().charAt(0);
                for (final ChatColor color : ChatColor.values()) {
                    if (color.getChar() == code) {
                        legacyMatcher.appendReplacement(legacyBuilder, ChatColor.COLOR_CHAR + "$2");
                        continue legacyLoop;
                    }
                }
            }
            // Don't change & to section sign (or replace two &'s with one)
            legacyMatcher.appendReplacement(legacyBuilder, "&$2");
        }
        legacyMatcher.appendTail(legacyBuilder);

        final StringBuffer rgbBuilder = new StringBuffer();
        final Matcher rgbMatcher = REPLACE_ALL_RGB_PATTERN.matcher(legacyBuilder.toString());
        while (rgbMatcher.find()) {
            final boolean isEscaped = rgbMatcher.group(1) != null;
            if (!isEscaped) {
                try {
                    final String hexCode = rgbMatcher.group(2);
                    rgbMatcher.appendReplacement(rgbBuilder, parseHexColor(hexCode));
                    continue;
                } catch (final NumberFormatException ignored) {
                }
            }
            rgbMatcher.appendReplacement(rgbBuilder, "&#$2");
        }
        rgbMatcher.appendTail(rgbBuilder);
        return rgbBuilder.toString();
    }

    public static String parseHexColor(String hexColor) throws NumberFormatException {
        if (hexColor.startsWith("#")) {
            hexColor = hexColor.substring(1); //fuck you im reassigning this.
        }
        if (hexColor.length() != 6) {
            throw new NumberFormatException("Invalid hex length");
        }
        Color.decode("#" + hexColor);
        final StringBuilder assembledColorCode = new StringBuilder();
        assembledColorCode.append(ChatColor.COLOR_CHAR + "x");
        for (final char curChar : hexColor.toCharArray()) {
            assembledColorCode.append(ChatColor.COLOR_CHAR).append(curChar);
        }
        return assembledColorCode.toString();
    }

    public static List<String> color(List<String> stringList) {
        List<String> list = new ArrayList<>();
        if (stringList == null || stringList.isEmpty())
            return list;

        stringList.forEach(entry -> list.add(color(entry)));
        return list;
    }
}
