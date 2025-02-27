package com.flink.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Author:  zsd
 * Date:  2025/1/21/周二 16:04
 */
public class CleanRepairTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
       CustomRedisSource customRedisSource = new CustomRedisSource();
        env.addSource(customRedisSource).addSink(new MysqlSink());
        env.execute("CleanRepairTask"+Config.PROFILES);
    }

    public static class CustomRedisSource extends RichParallelSourceFunction<JSONObject> {
        private boolean flag = true;
        private Jedis jedis;

        @Override
        public void run(SourceContext<JSONObject> sourceContext) throws Exception {
            while (flag) {
                jedis.select(Config.REDIS_DB_CLEAN);
                Set<String> keys = jedis.keys(Config.FOLLOW_REPAIR_SEND);
                //删除不需keys要的数据
                Set<String> keyAlls = jedis.keys(Config.REPAIR_SEND_ALL);
                keyAlls.forEach(key->{
                    Map<String, String> allMap = jedis.hgetAll(key);
                    allMap.forEach((k,v)->{
                        String[] split = key.split(":");
                        if(keys==null || !keys.toString().contains(k+"#"+split[2])){
                            JSONObject json =new JSONObject();
                            json.put("key",key);
                            json.put("val",k);
                            sourceContext.collect(json);
                        }
                    });
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
            jedis.select(Config.REDIS_DB_CLEAN);
            super.open(openContext);
        }
    }

    public static class MysqlSink  extends RichSinkFunction<JSONObject> {
        private Jedis jedis;
        @Override
        public void open(Configuration parameters) throws Exception {
            this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
            jedis.auth(Config.REDIS_PWD);
            jedis.select(Config.REDIS_DB_CLEAN);
            super.open(parameters);
        }

        @Override
        public void close() throws Exception {
            super.close();
        }

        @Override
        public void invoke(JSONObject value, Context context) throws Exception {
            String key = value.getString("key");
            String val =   value.getString("val");
            jedis.hdel(key,val);
        }
    }


}
