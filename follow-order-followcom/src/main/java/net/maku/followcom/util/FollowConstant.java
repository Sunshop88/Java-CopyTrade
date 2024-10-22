package net.maku.followcom.util;

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
     * 本机外网IP地址
     */
    public static String LOCAL_HOST = "";
    static {
        String hostname = "www.google.com";
        try {
            LOCAL_HOST = InetAddress.getByName(hostname).getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        System.out.println("Local host IP: " + LOCAL_HOST);
    }
}