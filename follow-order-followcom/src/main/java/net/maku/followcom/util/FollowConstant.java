package net.maku.followcom.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
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
        String ipServiceUrl = "http://checkip.amazonaws.com/";
        try {
            URL url = new URL(ipServiceUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String publicIP = in.readLine().trim();
            in.close();

            LOCAL_HOST= publicIP;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        System.out.println("Local host IP: " + LOCAL_HOST);
    }
}
