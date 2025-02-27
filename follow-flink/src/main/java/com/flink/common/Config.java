package com.flink.common;

/**
 * Author:  zsd
 * Date:  2025/1/6/周一 14:46
 */
public class Config {

/*    public static final String    MYSQL_HOSTNAME="39.99.136.49";
    public static final String   MYSQL_DB="follow-order-cp";
    public static final String   MYSQL_USERNAME="root";
    public static final String   MYSQL_PWD="xizcJWmXFkB5f4fm";

    public static final String    REDIS_HOSTNAME="r-8vb1a249o1w1q605bppd.redis.zhangbei.rds.aliyuncs.com";
    public static final Integer   REDIS_DB=5;
    public static final Integer   REDIS_DB_SOURCE=6;
    public static final Integer   REDIS_DB_CLEAN=1;
    public static final String   REDIS_PWD="diVMn9bMpACrXh79QyYY";
    public static final String   PROFILES="-test";*/

      /****uat*****/
 public static final String    MYSQL_HOSTNAME="39.99.241.16";
    public static final String   MYSQL_DB="follow-order-cp";
    public static final String   MYSQL_USERNAME="root";
    public static final String   MYSQL_PWD="5v07DqL!F7333";
//内网地址
     public static final String    REDIS_HOSTNAME="r-8vb1a249o1w1q605bp.redis.zhangbei.rds.aliyuncs.com";
   //外网地址
  //  public static final String    REDIS_HOSTNAME="r-8vb1a249o1w1q605bppd.redis.zhangbei.rds.aliyuncs.com";
    public static final Integer   REDIS_DB=7;
    public static final Integer   REDIS_DB_SOURCE=8;
    public static final Integer   REDIS_DB_CLEAN=2;
    public static final String   REDIS_PWD="diVMn9bMpACrXh79QyYY";
    public static final String   PROFILES="-uat";
    /****live*****/
   /* public static final String    MYSQL_HOSTNAME="139.196.238.6";
    public static final String   MYSQL_DB="follow-order-cp";
    public static final String   MYSQL_USERNAME="root";
    public static final String   MYSQL_PWD="8dWjmWvJd3JDvkQt";

    public static final String    REDIS_HOSTNAME="r-8vb1xdwunlmzzq4p2spd.redis.zhangbei.rds.aliyuncs.com";
    public static final Integer   REDIS_DB=5;
    public static final Integer   REDIS_DB_SOURCE=6;
    public static final Integer   REDIS_DB_CLEAN=1;
    public static final String   REDIS_PWD="diVMn9bMpACrXh79QyYY";
    public static final String   PROFILES="-live";*/

    /***共用***/
    public static final String   REDIS_PLATFORM_KEY="platform";
   public static final String   REDIS_TRADER_KEY="trader";
   public static final String   REDIS_NEW_TRADER_KEY="new:trader";
   public static final String   REDIS_TIME_OUT_TRADER_KEY="time:out:trader:";
    public static final String   REDIS_CLEAN_KEY="statistics:symbol:info";
    public static final String   REDIS_CLEAN_ACCOUNT_KEY="account:symbol:key";
    public static final String   REDIS_CLEAN_ACCOUNT_KEY_DEL="account:symbol:del";
    public static final String   REDIS_CLEAN_ACCOUNT_KEY_DEL_SQL="account:del:sql";
    public static final String   FOLLOW_REPAIR_SEND="follow:repair:send:*";
    public static final String   FOLLOW_REPAIR_CLOSE="follow:repair:close:*";
    public static final String     TRADER_ACTIVE="trader:active:";
    public static final String     REPAIR_SEND="repair:send:";
    public static final String     REPAIR_CLOSE="repair:close:";
    public static final String     REPAIR_CLOSE_two="repair:two:close:";
    public static final String      REPAIR_SEND_ALL="repair:send:*:*";
}
