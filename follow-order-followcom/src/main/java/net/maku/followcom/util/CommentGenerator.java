package net.maku.followcom.util;

import cn.hutool.core.util.ObjectUtil;
import net.maku.followcom.pojo.EaOrderInfo;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommentGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}|;:',.<>?/";

    /**
     * 根据固定注释、注释类型和位数生成注释
     *
     * @param fixedComment 固定注释
     * @param commentType  注释类型 (0-英文, 1-数字, 2-英文+数字+符号)
     * @param digits       位数
     * @return 生成的注释
     */
    public static String generateComment(String fixedComment, Integer commentType, Integer digits, EaOrderInfo orderInfo,Integer serverId) {
        if (ObjectUtil.isEmpty(fixedComment)&&(commentType==99||digits == 0)) {
            return "#"+serverId+"#" + Long.toString(Long.parseLong(orderInfo.getAccount()), 36) + "#" + Long.toString(orderInfo.getTicket(), 36) + "#FO_AUTO";
        }

        SecureRandom random = new SecureRandom();
        StringBuilder randomPart = new StringBuilder();

        if (commentType == 2) {
            randomPart.append(generateComplexRandomPart(digits, random));
        } else {
            String characterPool = getCharacterPoolByType(commentType);
            if (characterPool.isEmpty()) {
                throw new IllegalArgumentException("无效的注释类型：" + commentType);
            }

            for (int i = 0; i < digits; i++) {
                int index = random.nextInt(characterPool.length());
                randomPart.append(characterPool.charAt(index));
            }
        }

        return fixedComment + randomPart;
    }

    /**
     * 生成复杂的随机部分，满足注释类型为2时的规则。
     *
     * @param digits 位数
     * @param random 随机生成器
     * @return 符合要求的随机字符串
     */
    private static String generateComplexRandomPart(int digits, SecureRandom random) {
        List<Character> result = new ArrayList<>();
        if (digits == 1) {
            result.add(randomCharFromPool(ALPHABET, random));
        }else if (digits == 2) {
            result.add(randomCharFromPool(ALPHABET, random));
            result.add(randomCharFromPool(NUMBERS, random));
        } else if (digits >= 3) {
            result.add(randomCharFromPool(ALPHABET, random));
            result.add(randomCharFromPool(NUMBERS, random));
            result.add(randomCharFromPool(SYMBOLS, random));

            for (int i = 3; i < digits; i++) {
                String combinedPool = ALPHABET + NUMBERS + SYMBOLS;
                result.add(randomCharFromPool(combinedPool, random));
            }
        }

        Collections.shuffle(result, random);
        StringBuilder resultBuilder = new StringBuilder();
        for (char c : result) {
            resultBuilder.append(c);
        }

        return resultBuilder.toString();
    }

    /**
     * 从字符池中随机选取一个字符。
     *
     * @param pool   字符池
     * @param random 随机生成器
     * @return 随机字符
     */
    private static char randomCharFromPool(String pool, SecureRandom random) {
        int index = random.nextInt(pool.length());
        return pool.charAt(index);
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