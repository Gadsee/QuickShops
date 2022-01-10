package me.gadse.quickshops.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum RegexUtil {
    PLAYER("%player%"),
    VALUE("%value%"),
    ITEM("%item%"),
    AMOUNT("%amount%"),
    PRICE("%price%"),
    TARGET("%target%"),
    BUY_PRICE("%buy-price%"),
    SELL_PRICE("%sell-price%"),
    TRANSACTION_OPTIONS("%transaction-options%");

    private final Pattern pattern;

    RegexUtil(String regex) {
        pattern = Pattern.compile(regex);
    }

    public boolean matches(String input) {
        return pattern.matcher(input).find();
    }

    public String replaceAll(String input, int replacement)  {
        return replaceAll(input, Integer.toString(replacement));
    }

    public String replaceAll(String input, double replacement)  {
        return replaceAll(input, Double.toString(replacement));
    }

    public String replaceAll(String input, String replacement)  {
        return pattern.matcher(input).replaceAll(Matcher.quoteReplacement(replacement));
    }
}
