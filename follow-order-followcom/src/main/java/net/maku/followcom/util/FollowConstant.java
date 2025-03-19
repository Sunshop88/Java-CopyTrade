package net.maku.followcom.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

/**
 * @author Shaozz
 * @since 2022/6/6 15:35
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

          //  LOCAL_HOST = "39.101.133.150";
            LOCAL_HOST =publicIP;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 版本号
     */
    public static String MAS_VERSION = "1.2.0_0002";

    public static String SUB_VERSION = "1.2.0_0002";

    /**
     * 请求端口
     */
    public static String VPS_PORT = "9001";

    public static String REQUEST_PORT = "9000";

    public static String MYSQL_DB = "follow-order-cp";

    /**
     * VPS请求路径
     */
    public static String VPS_TRANSFERVPS = "/subcontrol/follow/transferVps";

    public static String VPS_STARTNEWVPS = "/subcontrol/follow/startNewVps";

    public static String VPS_MEASURE = "/subcontrol/follow/start";

    public static String VPS_RECONNECTION= "/subcontrol/trader/reconnectionServer";

    public static String VPS_RECONNECTION_Trader= "/subcontrol/trader/reconnectionTrader";

    public static String VPS_RECONNECTION_UPDATE_TRADER= "/subcontrol/trader/reconnectionUpdateTrader";

    /**
     * 更新缓存
     */
    public static String VPS_UPDATE_CACHE_VARIETY_CACHE= "/subcontrol/follow/updateVarietyCache";
    public static String VPS_UPDATE_CACHE_FOLLOW_PLAT_CACHE= "/subcontrol/follow/updatePlatCache";

    /**
     * 跟单日志
     */
    public static String FOLLOW_SEND = "【策略下单跟随】";

    public static String FOLLOW_CLOSE = "【策略平仓跟随】";


    public static String FOLLOW_REPAIR_SEND = "【策略补单下单跟随】";
    public static String FOLLOW_REPAIR_CLOSE = "【策略补单平仓跟随】";

    public static String SUBCONTROL_SERVER = "127.0.0.1";
    /**
     * 喊单
     */
    public static String SOURCE_INSERT = "/api/source/insert";
    public static String SOURCE_UPDATE = "/api/source/update";
    public static String SOURCE_DEL = "/api/source/delete";
    public static String FOLLOW_INSERT = "/api/follow/insert";
    public static String FOLLOW_UPDATE = "/api/follow/update";
    public static String FOLLOW_DEL = "/api/follow/delete";
    public static String ORDERHISTORY = "/api/orderCloseList";
    public static String OPENEDORDERS = "/api/openedOrders";

    public static String ORDERSEND = "/api/orderSend";
    public static String ORDERCLOSE = "/api/orderclose";
    public static String ORDERCLOSEALL = "/api/orderCloseAll";
    public static String CHANGEPASSWORD = "/api/changepassword";
    public static String REPAIRORDER = "/api/repairorder";
    public static String ORDERCLOSEPROFIT = "/api/orderCloseProfit";
    public static String ORDERCLOSELOSS = "/api/orderCloseLoss";
    public static String SYMBOLPARAMS = "/api/symbolParams";
    public static String HISTOTY_ORDER_LIST = "/subcontrol/follow/histotyOrderList";
    public static String MASORDERSEND = "/subcontrol/trader/masOrdersend";
    public static String MASORDERCLOSE = "/subcontrol/trader/orderClose";
    public static String ADD_TRADER = "/subcontrol/trader";
    public static String ADD_SLAVE = "/subcontrol/follow/addSlave";
    public static String DEL_TRADER = "/subcontrol/trader";
    public static String SYNCH_DATA = "/subcontrol/trader/synchData/";



    public static String RECONNECTION = "/subcontrol/trader/reconnection";
    public static  String FOLLOW_ORDERCLOSE="/subcontrol/trader/orderClose";
    public static  String FOLLOW_ALL_ORDERCLOSE="/subcontrol/follow/repairOrderClose";

    /**
     * AES key
     */
    public static String MT4_KEY="FOLLOWERSHIP4KEY";

    /**
     * 品种匹配使用
     */
    public static String PROFIT_MODE = "Forex";

}
