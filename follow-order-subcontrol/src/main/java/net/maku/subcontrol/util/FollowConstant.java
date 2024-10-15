package net.maku.subcontrol.util;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Shaozz
 * @since  2022/6/6 15:35
 */
@Slf4j
public class FollowConstant {

    /**
     * 本机IP地址
     */
    public static String LOCAL_HOST = "";
    static {
        try {
            LOCAL_HOST = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.error("获取本地IP异常",e);
        }
    }
}
