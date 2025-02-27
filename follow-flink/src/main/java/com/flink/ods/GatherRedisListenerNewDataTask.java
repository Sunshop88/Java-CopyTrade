package com.flink.ods;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.ods.mapper.MyRedisMapper;
import com.flink.ods.vo.AccountData;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

/**
 * Author:  zsd
 * Date:  2025/1/22/周三 18:23
 */
public class GatherRedisListenerNewDataTask {
    public static void main(String[] args) throws Exception {
        String id = args[0];
        String name = args[1];
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
       MyListenerSource myListenerSource = new MyListenerSource(Integer.parseInt(id));
        FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new MyRedisMapper());
        env.setParallelism(1);
        Map<Integer, QuoteClient> clients = new HashMap<>();
        HashMap<String, List<String>> map = new HashMap<>();
        env.addSource(myListenerSource).map(o->{
            Jedis jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
            jedis.auth(Config.REDIS_PWD);
            jedis.select(Config.REDIS_DB_SOURCE);
            JSONObject traderJson = JSONObject.parseObject(o);
            Integer account = traderJson.getInteger("account");
            String password = traderJson.getString("password");
            String platform = traderJson.getString("platform");
            Integer type = traderJson.getInteger("type");
            Integer platformId = traderJson.getInteger("platform_id");
            Integer vpsId = traderJson.getInteger("server_id");
            String vpsName = traderJson.getString("server_name");
            QuoteClient client = clients.get(account);
            //登录
            jedis.set("login:"+account,o);
            if(client!=null){
                try {
                    return   sendData(client, platform, type, platformId, vpsId, vpsName);
                } catch (Exception e) {
                    String c1 = login(jedis, account, map, platform, password, clients, type, platformId, vpsId, vpsName);
                    if (c1 != null) return c1;
                }
            }else{
                String c1 = login(jedis, account, map, platform, password, clients, type, platformId, vpsId, vpsName);
                if (c1 != null) return c1;
            }

            return "";
        }).filter(o-> o!=null && o.length()>5).addSink(redisSink);
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+name);
     /*   env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"伦敦2（8.211.207.234）");
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"39.99.145.155-张家口3");
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"39.99.145.155-张家口3");
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"39.99.145.155-张家口3");
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"39.99.145.155-张家口3");
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"39.99.145.155-张家口3");
        env.execute("GatherRedisListenerNewDataTask"+Config.PROFILES+"39.99.145.155-张家口3");*/
    }

    private static String login(Jedis jedis, Integer account, HashMap<String, List<String>> map, String platform, String password, Map<Integer, QuoteClient> clients, Integer type, Integer platformId, Integer vpsId, String vpsName) {
        String s = jedis.get(Config.REDIS_TIME_OUT_TRADER_KEY + account);
        if(s==null){
            Map<String, String> platformMap = jedis.hgetAll(Config.REDIS_PLATFORM_KEY);
            platformMap.forEach((k1,v1)->{
                JSONObject json = JSON.parseObject(v1);
                String serverNode = json.getString("server_node");
                String server = json.getString("server");
                List<String> vals = map.get(server);
                if(vals==null){
                    vals=new ArrayList<>();
                }
                vals.add(serverNode);
                map.put(server,vals);
            });
            List<String> nodes = map.get(platform);
            if (nodes != null) {
                boolean timeFlag = true;
                for (int i = 0; i < nodes.size(); i++) {
                    String serverNode = nodes.get(i);
                    String[] split = serverNode.split(":");
                    try {
                        QuoteClient   c1 = new QuoteClient(account, password, split[0], Integer.parseInt(split[1]));
                        c1.Connect();
                        clients.put(account,c1);
                        return sendData(c1, platform, type, platformId, vpsId, vpsName);
                    } catch (Exception e) {

                    }
                }
                //放到超时连接
                if(timeFlag){
                    jedis.set(Config.REDIS_TIME_OUT_TRADER_KEY+ account, String.valueOf(account), "NX", "EX", 60*60);
                }
            }

        }
        return null;
    }

    private static String sendData( QuoteClient client, String platform, Integer type, Integer platformId, Integer vpsId, String vpsName) {
        AccountData da= AccountData.builder().user(client.User).password(client.Password).credit(client.Credit).freeMargin(client.FreeMargin)
                .equity(client.Equity).host(client.Host).profit(client.Profit).platform(platform).type(type).platformId(platformId)
                .vpsId(vpsId).vpsName(vpsName).build();
        List<Order> orders = Arrays.stream(client.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
        if(orders!=null){
            da.setNum(orders.size());
            da.setOrders(orders);
        }
        String json = JSON.toJSONString(da);
       return json;
    }

    public static class  MyListenerSource extends RichParallelSourceFunction<String> {

        private Jedis jedis;
        private boolean flag = true;

        private Integer id;

        public MyListenerSource(Integer id) {
            this.id = id;
        }

        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                Map<String, String> traderDataMap = jedis.hgetAll(Config.REDIS_TRADER_KEY);
           //     Map<String, String> traderDataMap =new HashMap<>();

                 traderDataMap.forEach((k,v)->{
                     JSONObject traderJson = JSONObject.parseObject(v);
                     Integer vpsId = traderJson.getInteger("server_id");
                     if(vpsId==id){
                         sourceContext.collect(v);
                     }

                });


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
            jedis.select(Config.REDIS_DB_SOURCE);
            super.open(openContext);
        }
    }
}
