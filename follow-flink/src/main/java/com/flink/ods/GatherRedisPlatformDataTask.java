package com.flink.ods;

import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;

import java.util.Properties;

/**
 * Author:  zsd
 * Date:  2025/1/9/周四 14:51
 */
public class GatherRedisPlatformDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        //构建监听mysql
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname(Config.MYSQL_HOSTNAME)
                .port(3306)
                .databaseList(Config.MYSQL_DB)
                .tableList(Config.MYSQL_DB+".follow_platform")
                .username(Config.MYSQL_USERNAME)
                .password(Config.MYSQL_PWD)
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();
        FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB_SOURCE).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new RedisDataMapper());
        env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "GatherRedisPlatformData").addSink(redisSink);
        env.execute("GatherRedisPlatformDataTask"+Config.PROFILES);
    }
    public static class RedisDataMapper implements RedisMapper<String> {

        @Override
        public RedisCommandDescription getCommandDescription() {
            return new RedisCommandDescription(RedisCommand.HSET,Config.REDIS_PLATFORM_KEY);
        }

        @Override
        public String getKeyFromData(String s) {
            JSONObject json = JSONObject.parseObject(s);
            JSONObject after = json.getJSONObject("after");
            JSONObject before = json.getJSONObject("before");
            Long id =null;
            if(before!=null ){
                id = before.getLong("id");
            }
            if(after!=null ){
                id = after.getLong("id");
            }

             id = after.getLong("id");
            return id.toString();
        }

        @Override
        public String getValueFromData(String s) {
            JSONObject json = JSONObject.parseObject(s);
            String after = json.getString("after");
            if(after==null || after.length()==0){
                after = json.getString("before");
            }
            return after;
        }
    }

}
