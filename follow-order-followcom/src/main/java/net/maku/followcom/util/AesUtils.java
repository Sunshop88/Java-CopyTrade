package net.maku.followcom.util;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.nio.charset.Charset;

/**
 * @author Calorie
 * @date 2025-01-15
 * @describe AES对称加密相关方法
 */
public class AesUtils {

    /**
     * AES对称加密 Hex 模式
     *
     * @param mode    模式 eg: Mode.ECB
     * @param padding 补码方式 eg: Padding.ZeroPadding
     * @param key     密钥，支持三种密钥长度：128、192、256位 eg: JYB1654134212JRY
     * @param content 需要加密的内容
     * @param salt    偏移向量，加盐
     * @return
     */
    public static String aesEncryptHex(Mode mode, Padding padding, String key, Object content, String salt) {

        AES aes = null;
        if (CharSequenceUtil.isBlank(salt)) {
            aes = new AES(mode, padding, key.getBytes());
        } else {
            //加盐的方法
            aes = new AES(mode, padding, key.getBytes(), salt.getBytes());
        }
        // 加密为16进制表示
        return aes.encryptHex(JSON.toJSONString(content));
    }

    /**
     * AES对称加密 Hex 模式
     *
     * @param mode    模式 eg: Mode.ECB
     * @param padding 补码方式 eg: Padding.ZeroPadding
     * @param key     密钥，支持三种密钥长度：128、192、256位 eg: JYB1654134212JRY
     * @param content 需要加密的内容
     * @param salt    偏移向量，加盐
     * @param charset 字符集
     * @return
     */
    public static String aesEncryptHex(Mode mode, Padding padding, String key, Object content, String salt, Charset charset) {

        AES aes = null;
        if (CharSequenceUtil.isBlank(salt)) {
            aes = new AES(mode, padding, key.getBytes());
        } else {
            //加盐的方法
            aes = new AES(mode, padding, key.getBytes(), salt.getBytes());
        }
        // 加密为16进制表示
        return aes.encryptHex(JSON.toJSONString(content), charset);
    }

    /**
     * AES对称解密 Hex 模式
     *
     * @param mode       模式 eg: Mode.ECB
     * @param padding    补码方式 eg: Padding.ZeroPadding
     * @param key        密钥，支持三种密钥长度：128、192、256位 eg: JYB1654134212JRY
     * @param encryptHex 需要解密的内容
     * @param salt       偏移向量，加盐
     * @return
     */
    public static JSONObject aesDecryptHex(Mode mode, Padding padding, String key, String encryptHex, String salt) {

        AES aes = null;
        if (CharSequenceUtil.isBlank(salt)) {
            aes = new AES(mode, padding, key.getBytes());
        } else {
            //加盐的方法
            aes = new AES(mode, padding, key.getBytes(), salt.getBytes());
        }
        // 解密16进制表示的字符串 默认 CharsetUtil.CHARSET_UTF_8
        return JSON.parseObject( aes.decryptStr(encryptHex));
    }

    /**
     * AES对称解密 Hex 模式
     *
     * @param mode       模式 eg: Mode.ECB
     * @param padding    补码方式 eg: Padding.ZeroPadding
     * @param key        密钥，支持三种密钥长度：128、192、256位 eg: JYB1654134212JRY
     * @param encryptHex 需要解密的内容
     * @param salt       偏移向量，加盐
     * @param charset    字符集
     * @return
     */
    public static JSONObject aesDecryptHex(Mode mode, Padding padding, String key, String encryptHex, String salt, Charset charset) {

        AES aes = null;
        if (CharSequenceUtil.isBlank(salt)) {
            aes = new AES(mode, padding, key.getBytes());
        } else {
            //加盐的方法
            aes = new AES(mode, padding, key.getBytes(), salt.getBytes());
        }
        // 解密16进制表示的字符串
        return JSON.parseObject(aes.decryptStr(encryptHex, charset));
    }
    public static String  aesEncryptStr(String password){
        return aesEncryptHex(Mode.ECB, Padding.ZeroPadding,FollowConstant.MT4_KEY,password,null);
    }

    public static String  decryptStr(String password){
        byte[] encryptedBytes = hexStringToByteArray(password);

        // 进行解密
        String decrypted = new AES(Mode.ECB, Padding.ZeroPadding, FollowConstant.MT4_KEY.getBytes()).decryptStr(encryptedBytes);

        // 如果解密后的字符串带有引号，去除引号
        if (decrypted.startsWith("\"") && decrypted.endsWith("\"")) {
            decrypted = decrypted.substring(1, decrypted.length() - 1);
        }
        return decrypted;
    }

    // 十六进制字符串转字节数组
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
