package com.flink.ods.mapper;

import com.alibaba.fastjson.JSONObject;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommand;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisCommandDescription;
import org.apache.flink.streaming.connectors.redis.common.mapper.RedisMapper;

/**
 * Author:  zsd
 * Date:  2025/1/6/周一 11:46
 */
public  class MyRedisMapper implements RedisMapper<String> {


    @Override
    public RedisCommandDescription getCommandDescription() {
        return new RedisCommandDescription(RedisCommand.LPUSH);
    }

    @Override
    public String getKeyFromData(String s) {
        JSONObject json = JSONObject.parseObject(s);
        String user = json.getString("user");
        String vpsId = json.getString("vpsId");
        String platformId = json.getString("platformId");
        return vpsId+":"+user+":"+platformId;
    }

    @Override
    public String getValueFromData(String s) {
        return s;
    }
}

