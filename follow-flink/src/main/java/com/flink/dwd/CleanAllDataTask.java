package com.flink.dwd;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.dwd.vo.FollowTraderAnalysis;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Author:  zsd
 * Date:  2025/1/14/周二 18:25
 */
public class CleanAllDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        CustomRedisSource customRedisSource = new CustomRedisSource();
        MysqlSink mysqlSink = new MysqlSink();
        env.addSource(customRedisSource).addSink(mysqlSink);
        env.execute("CleanAllDataTask"+Config.PROFILES);
    }

    public static class MysqlSink  extends RichSinkFunction<String> {
        private    Jedis jedis;
        private Statement statement;
        @Override
        public void invoke(String value, Context context) throws Exception {
            String[] split = value.split(":");
           // String sql="DELETE FROM follow_trader_analysis WHERE symbol='"+split[0]+"'AND account="+split[2]+" AND platform_id="+split[3];
         //   statement.execute(sql);
            jedis.hdel(Config.REDIS_CLEAN_KEY,value);
        }

        @Override
        public void close() throws Exception {
            super.close();
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            //1.加载数据库厂商提供的驱动
            Class.forName("com.mysql.cj.jdbc.Driver");//指定路径

            //2.获取数据库的连接                 固定写法        IP+端口号       数据库               字符集编码
            Connection connection = DriverManager.getConnection("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false",
                    Config.MYSQL_USERNAME, Config.MYSQL_PWD);//通过实现类来以获取数据库连接Connnection是Java中的类
            //3.创建Statement对象
            this.statement = connection.createStatement();
            this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
            jedis.auth(Config.REDIS_PWD);
            jedis.select(Config.REDIS_DB_CLEAN);

            super.open(parameters);
        }
    }
    public static class CustomRedisSource extends RichParallelSourceFunction<String> {
        private boolean flag = true;
        private Jedis jedis;
        private Statement statement;
        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                Map<String, String> vals = jedis.hgetAll(Config.REDIS_CLEAN_ACCOUNT_KEY);
                Set<String> hkeys = jedis.hkeys(Config.REDIS_CLEAN_KEY);
                List<String> allList=new ArrayList<>();

                vals.forEach((k,value)->{
                    if(value!=null){
                        List<String> list = JSONArray.parseArray(value, String.class);
                        allList.addAll(list);
                    }
                });


                hkeys.removeAll(allList);
                //反向删除

                for (String hkey : hkeys) {
                    sourceContext.collect(hkey);
                }

            }
        }

        @Override
        public void cancel() {
            flag = false;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {
            this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
            jedis.auth(Config.REDIS_PWD);
            jedis.select(Config.REDIS_DB_CLEAN);

            super.open(openContext);
        }
    }
}
