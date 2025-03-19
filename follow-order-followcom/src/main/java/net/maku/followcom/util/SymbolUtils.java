package net.maku.followcom.util;

public class SymbolUtils {
    public static String processString(String input) {
        // 如果字符串包含 '.'，截取 . 后面的部分
        if (input.contains(".")) {
            return input.substring(0, input.indexOf("."));
        }

        // 如果不包含 '.', 判断是否包含需要截取的字符
        String[] substringsToRemove = {"-", "'", "zero","+", "dec24","ft","r","#","i","x"};

        for (String substr : substringsToRemove) {
            if (input.contains(substr)) {
                // 一旦找到包含的字符串，截取掉
                input = input.substring(0, input.indexOf(substr));
            }
        }

        return input;
    }
}
