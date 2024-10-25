package net.maku.framework.common.utils;

import java.security.SecureRandom;
import java.util.UUID;

public class RandomStringUtil {

    // 定义字符池
    private static final String NUMBERS = "0123456789";
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LETTERS_AND_NUMBERS = LETTERS + NUMBERS;

    private static final SecureRandom random = new SecureRandom();

    /**
     * 生成指定长度的纯数字字符串
     *
     * @param length 字符串长度
     * @return 生成的纯数字字符串
     */
    public static String generateNumeric(int length) {
        return generateRandomString(NUMBERS, length);
    }

    /**
     * 生成指定长度的纯英文字符串
     *
     * @param length 字符串长度
     * @return 生成的纯英文字符串
     */
    public static String generateAlphabetic(int length) {
        return generateRandomString(LETTERS, length);
    }

    /**
     * 生成指定长度的字母和数字组合的字符串
     *
     * @param length 字符串长度
     * @return 生成的字母和数字组合字符串
     */
    public static String generateAlphanumeric(int length) {
        return generateRandomString(LETTERS_AND_NUMBERS, length);
    }

    /**
     * 生成Client-ID
     * @return
     */
    public static String generateUUIDClientId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 核心随机字符串生成逻辑
     *
     * @param characterPool 可选字符池
     * @param length        字符串长度
     * @return 生成的随机字符串
     */
    private static String generateRandomString(String characterPool, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("长度必须大于0");
        }
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(characterPool.charAt(random.nextInt(characterPool.length())));
        }
        return result.toString();
    }

}
