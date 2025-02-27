package com.flink.ods;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.ods.mapper.MyRedisMapper;
import com.flink.ods.vo.AccountData;
import lombok.extern.slf4j.Slf4j;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.connector.jdbc.JdbcInputFormat;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

/**
 * Author:  zsd
 * Date:  2025/1/3/周五 9:43
 * flink采集数据
 */
@Slf4j
public class GatherDataTask {

    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
      RowTypeInfo rowTypeInfo = new RowTypeInfo(
                BasicTypeInfo.STRING_TYPE_INFO,
                BasicTypeInfo.STRING_TYPE_INFO

        );

        JdbcInputFormat jdbcInputFormat = JdbcInputFormat.buildJdbcInputFormat()
                .setDrivername("com.mysql.cj.jdbc.Driver")
                .setDBUrl("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false")
                .setUsername(Config.MYSQL_USERNAME)
                .setPassword(Config.MYSQL_PWD)
                .setQuery("SELECT server_node,`server` FROM follow_platform") //定义需要查询的sql语句
                .setRowTypeInfo(rowTypeInfo) //这个需要一个rowtypeinfo对象
                .finish();
        HashMap<String, List<String>> map = new HashMap<>();
        env.createInput(jdbcInputFormat).executeAndCollect().forEachRemaining(x->{
            String key = x.getFieldAs(1);
            List<String> vals = map.get(key);
            if(vals==null){
                vals=new ArrayList<>();
            }
            vals.add(x.getFieldAs(0));
            map.put(key,vals);
        });
        //构建监听mysql
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname(Config.MYSQL_HOSTNAME)
                .port(3306)
                .databaseList(Config.MYSQL_DB)
                .tableList(Config.MYSQL_DB+".follow_trader")
                .username(Config.MYSQL_USERNAME)
                .password(Config.MYSQL_PWD)
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();

        FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new MyRedisMapper());
        env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "gatherMysql").map(a-> {
            JSONObject json = JSONObject.parseObject(a);
            JSONObject obj = json.getJSONObject("after");
            Integer account = obj.getInteger("account");
            String password = obj.getString("password");
            String platform = obj.getString("platform");
            Integer type = obj.getInteger("type");
            Integer platformId = obj.getInteger("platform_id");
            Integer vpsId = obj.getInteger("server_id");
            String vpsName = obj.getString("server_name");
            List<String> nodes = map.get(platform);
            QuoteClient client =null;
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    String serverNode = nodes.get(i);
                    String[] split = serverNode.split(":");
                    try {
                         client = new QuoteClient(account, password, split[0], Integer.parseInt(split[1]));
                         client.Connect();
                         continue;
                    } catch (Exception e) {
                    }
                }
            }
            AccountData data =null;
            if(client!=null){
                //持仓订单
                List<Order> orders = Arrays.stream(client.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
                data = AccountData.builder().user(client.User).password(client.Password).credit(client.Credit).freeMargin(client.FreeMargin)
                        .equity(client.Equity).host(client.Host).profit(client.Profit).platform(platform).type(type).platformId(platformId)
                        .vpsId(vpsId).vpsName(vpsName).build();

                if(orders!=null){
                    data.setNum(orders.size());
                    data.setOrders(orders);
                }
            }else{
                data = AccountData.builder().user(account).platform(platform).type(type).platformId(platformId)
                        .vpsId(vpsId).vpsName(vpsName).build();
            }
            String jsonStr = JSON.toJSONString(data);
                    return jsonStr;
                }).addSink(redisSink);

        env.execute("GatherDataTask"+Config.PROFILES);

    }



}
