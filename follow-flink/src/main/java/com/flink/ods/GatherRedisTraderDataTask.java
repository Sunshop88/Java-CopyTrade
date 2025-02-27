package com.flink.ods;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;
import redis.clients.jedis.Jedis;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Author:  zsd
 * Date:  2025/1/9/周四 13:59
 */
public class GatherRedisTraderDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
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
       /* FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB_SOURCE).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new RedisDataMapper());*/
        env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "GatherRedisData").addSink(new MySink());
      //  env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "GatherRedisData").print();
        env.execute("GatherRedisTraderDataTask"+Config.PROFILES);
    }
      public static class MySink extends   RichSinkFunction<String>{
          private Jedis jedis;
          private Statement statement;
          private Map<Integer,Integer> map=new HashMap<>();
          @Override
          public void open(Configuration parameters) throws Exception {
              this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
              jedis.auth(Config.REDIS_PWD);
              jedis.select(Config.REDIS_DB_SOURCE);
              //1.加载数据库厂商提供的驱动
              Class.forName("com.mysql.cj.jdbc.Driver");//指定路径
              //2.获取数据库的连接                 固定写法        IP+端口号       数据库               字符集编码
              Connection connection = DriverManager.getConnection("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false",
                      Config.MYSQL_USERNAME, Config.MYSQL_PWD);//通过实现类来以获取数据库连接Connnection是Java中的类
              //3.创建Statement对象
              this.statement = connection.createStatement();
              String sql="SELECT * FROM follow_vps";
              ResultSet resultSet = statement.executeQuery(sql);
              while(resultSet.next()){
                  int id = resultSet.getInt("id");
                  int isActive = resultSet.getInt("is_flink");
                  map.put(id,isActive);
              }
              super.open(parameters);
          }

          @Override
          public void close() throws Exception {
              super.close();
          }

          @Override
          public void invoke(String value, Context context) throws Exception {
              //查询mysql
              jedis.select(Config.REDIS_DB_SOURCE);
              JSONObject json = JSONObject.parseObject(value);
              String op = json.getString("op");
              JSONObject after = json.getJSONObject("after");
              JSONObject before = json.getJSONObject("before");
              if(op.equals("d")){
                  jedis.hdel(Config.REDIS_TRADER_KEY,before.getString("id"));
                  Integer vpsId = before.getInteger("server_id");
                  Integer account = before.getInteger("account");
                  Integer platformId = before.getInteger("platform_id");
                  String id = before.getString("id");
                  //刪除
                  String accountKey = vpsId + ":" + account + ":" + platformId;
                  //删除
                  jedis.hdel(Config.REDIS_NEW_TRADER_KEY,id);
                  jedis.select(Config.REDIS_DB_CLEAN);
                  JSONArray array = new JSONArray();
                  jedis.hset(Config.REDIS_CLEAN_ACCOUNT_KEY_DEL,accountKey,before.toJSONString());
                  jedis.hset(Config.REDIS_CLEAN_ACCOUNT_KEY,accountKey,array.toJSONString());
                  Map<String, String>  keyMaps = jedis.hgetAll(Config.REDIS_CLEAN_KEY);
                  keyMaps.forEach((k,v)->{
                          if(k.contains(accountKey)){
                              jedis.hdel(Config.REDIS_CLEAN_KEY,k);
                          }
                  });
                  //删除mysql

              }else if(op.equals("c")){
                  Integer vpsId = after.getInteger("server_id");
                  Integer isActive = map.get(vpsId);
                  if(isActive==0){
                      jedis.hset(Config.REDIS_TRADER_KEY,after.getString("id"),after.toJSONString());
                      jedis.hset(Config.REDIS_NEW_TRADER_KEY,after.getString("id"),after.toJSONString());
                  }

              }else{
                  Integer vpsId = after.getInteger("server_id");
                  Integer isActive = map.get(vpsId);
                  if(isActive==0) {
                      jedis.hset(Config.REDIS_TRADER_KEY, after.getString("id"), after.toJSONString());
                  }
              }
              super.invoke(value, context);
          }
      }
    public static class RedisDataMapper implements RedisMapper<String> {

        @Override
        public RedisCommandDescription getCommandDescription() {
            return new RedisCommandDescription(RedisCommand.HSET,Config.REDIS_TRADER_KEY);
        }

        @Override
        public String getKeyFromData(String s) {
            JSONObject json = JSONObject.parseObject(s);

            JSONObject after = json.getJSONObject("after");
            JSONObject before = json.getJSONObject("before");
            Long id=null;
            if(before!=null){
                 id = before.getLong("id");
            }
            if(after!=null){
                 id = after.getLong("id");
            }

            return id.toString();
        }

        @Override
        public String getValueFromData(String s) {
            JSONObject json = JSONObject.parseObject(s);
            String before = json.getString("before");
            String jsonData=null;
            if(before!=null){
                jsonData =before;
            }
            String after = json.getString("after");
            if(after!=null){
                jsonData = after;
            }

            return jsonData;
        }
    }


}
