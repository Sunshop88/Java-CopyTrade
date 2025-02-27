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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

/**
 * Author:  zsd
 * Date:  2025/1/10/周五 9:33
 */
public class GatherRedisListenerDataTask {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
       MyListenerSource myListenerSource = new MyListenerSource();
        FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new MyRedisMapper());
        env.setParallelism(5);
        env.addSource(myListenerSource).addSink(redisSink);
        env.execute("GatherRedisListenerDataTask"+Config.PROFILES+"");
    }

    public static class  MyListenerSource extends RichParallelSourceFunction<String> {
        Map<Integer,QuoteClient> clients = new HashMap<>();
        HashMap<String, List<String>> map = new HashMap<>();
        private Jedis jedis;
        private boolean flag = true;
        Map<String,AccountData> mapData = new ConcurrentHashMap<>();

        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                Map<String, String> traderDataMap = jedis.hgetAll(Config.REDIS_TRADER_KEY);
                AtomicReference<Boolean> platformFlag= new AtomicReference<>(true);
                traderDataMap.forEach((k,v)->{
                  try {
                    JSONObject traderJson = JSONObject.parseObject(v);
                    Integer account = traderJson.getInteger("account");
                    String password = traderJson.getString("password");
                    String platform = traderJson.getString("platform");
                    Integer type = traderJson.getInteger("type");
                    Integer platformId = traderJson.getInteger("platform_id");
                    Integer vpsId = traderJson.getInteger("server_id");
                    String vpsName = traderJson.getString("server_name");
                    QuoteClient client = clients.get(account);
                    if(client!=null){
                        setData(sourceContext, client, platform, type, platformId, vpsId, vpsName);
                    }else{
                        throw  new Exception("未登录");
                    }
                  } catch (Exception e) {
                      e.printStackTrace();
                  }
                });


            }
        }

        private  void setData(SourceContext<String> sourceContext, QuoteClient client, String platform, Integer type, Integer platformId, Integer vpsId, String vpsName) {

            AccountData da = mapData.get(client.User + platformId + "");
            if(da==null){
                    da= AccountData.builder().user(client.User).password(client.Password).credit(client.Credit).freeMargin(client.FreeMargin)
                        .equity(client.Equity).host(client.Host).profit(client.Profit).platform(platform).type(type).platformId(platformId)
                        .vpsId(vpsId).vpsName(vpsName).build();    
            }else{
                da.setUser(client.User);
                da.setPassword(client.Password);
                da.setCredit(client.Credit);
                da.setFreeMargin(client.FreeMargin);
                da.setEquity(client.Equity);
                da.setHost(client.Host);
                da.setProfit(client.Profit);
                da.setType(type);
                da.setPlatformId(platformId);
                da.setVpsId(vpsId);
                da.setVpsName(vpsName);
                da.setPlatform(platform);
            }
            mapData.put(client.User+platformId+"",da);
           
          
            List<Order> orders = Arrays.stream(client.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
            if(orders!=null){
                da.setNum(orders.size());
                da.setOrders(orders);
            }
            String json = JSON.toJSONString(da);
            sourceContext.collect(json);
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
            //获取平台
            Map<String, String> platformMap = jedis.hgetAll(Config.REDIS_PLATFORM_KEY);
            platformMap.forEach((k,v)->{
                JSONObject json = JSON.parseObject(v);
                String serverNode = json.getString("server_node");
                String platform = json.getString("server");
                List<String> vals = map.get(platform);
                if(vals==null){
                    vals=new ArrayList<>();
                }
                vals.add(serverNode);
                map.put(platform,vals);
            });
          //获取
            Map<String, String> traderDataMap = jedis.hgetAll(Config.REDIS_TRADER_KEY);
            if(traderDataMap!=null){
                //执行语句
                traderDataMap.forEach((k,v)->{
                    JSONObject json = JSON.parseObject(v);
                    String platform = json.getString("platform");
                    int account = json.getInteger("account");
                    String password = json.getString("password");
                    List<String> nodes = map.get(platform);
                    if (nodes != null) {
                        for (int i = 0; i < nodes.size(); i++) {
                            String serverNode = nodes.get(i);
                            String[] split = serverNode.split(":");
                            try {
                                QuoteClient   client = new QuoteClient(account, password, split[0], Integer.parseInt(split[1]));
                                client.Connect();
                                clients.put(account,client);
                            } catch (Exception e) {
                                System.out.println("登录失败:"+account);
                                e.printStackTrace();
                            }
                        }
                    }

                });
            }

            super.open(openContext);
        }
    }
}
