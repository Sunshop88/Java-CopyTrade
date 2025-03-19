package net.maku.framework.common.utils;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import net.maku.framework.common.exception.ServerException;

/**
 * 校验工具类
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public class AssertUtils {

    public static void isBlank(String str, String variable) {
        if (StrUtil.isBlank(str)) {
            throw new ServerException(variable + "不能为空");
        }
    }

    public static void isNull(Object object, String variable) {
        if (object == null) {
            throw new ServerException(variable + "不能为空");
        }
    }

    public static void isArrayEmpty(Object[] array, String variable) {
        if(ArrayUtil.isEmpty(array)){
            throw new ServerException(variable + "不能为空");
        }
    }

    public static String getLastNumber(String input) {
        if (input == null || !input.contains(":")) {
            return null; // 输入为空或没有冒号
        }
        String[] parts = input.split(":");
        return parts[parts.length - 1]; // 返回最后一部分
    }

    public static String[] getLastTwoNumbers(String input) {
        if (input == null || !input.contains(":")) {
            return null; // 输入为空或不包含冒号
        }

        String[] parts = input.split(":"); // 按冒号分割字符串
        if (parts.length < 3) {
            return null; // 如果不足三个部分，无法获取最后两个数字
        }

        // 获取最后两个部分
        String[] result = new String[2];
        result[0] = parts[parts.length - 2]; // 倒数第二部分
        result[1] = parts[parts.length - 1]; // 倒数第一部分
        return result;
    }


}