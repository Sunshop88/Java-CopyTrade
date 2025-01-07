package net.maku.followcom.util;

import java.security.SecureRandom;

public class CommentGenerator {

    // 定义随机生成字符的集合
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    /**
     * 根据固定注释、注释类型和位数生成注释
     *
     * @param fixedComment 固定注释
     * @param commentType  注释类型 (0-英文, 1-数字, 2-英文+数字+符号)
     * @param digits       位数
     * @return 生成的注释
     */
    public static String generateComment(String fixedComment, Integer commentType, Integer digits) {
        if (fixedComment == null || commentType == null || digits == null || digits <= 0) {
            return "";
        }

        // 随机生成器
        SecureRandom random = new SecureRandom();
        StringBuilder randomPart = new StringBuilder();

        // 根据注释类型生成随机字符
        String characterPool = getCharacterPoolByType(commentType);
        if (characterPool.isEmpty()) {
            throw new IllegalArgumentException("无效的注释类型：" + commentType);
        }

        for (int i = 0; i < digits; i++) {
            int index = random.nextInt(characterPool.length());
            randomPart.append(characterPool.charAt(index));
        }

        // 组合生成的注释
        return fixedComment  + randomPart;
    }

    /**
     * 根据注释类型获取字符池
     *
     * @param commentType 注释类型 (0-英文, 1-数字, 2-英文+数字+符号)
     * @return 字符池
     */
    private static String getCharacterPoolByType(Integer commentType) {
        switch (commentType) {
            case 0: // 英文
                return ALPHABET;
            case 1: // 数字
                return NUMBERS;
            case 2: // 英文+数字+符号
                return ALPHABET + NUMBERS + SYMBOLS;
            default:
                return "";
        }
    }
}
