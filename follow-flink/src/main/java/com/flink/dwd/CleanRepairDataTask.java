package com.flink.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;

import com.flink.dwd.vo.OrderRepairInfoVO;
import com.flink.enums.Op;
import com.flink.enums.TraderRepairOrderEnum;
import com.flink.dwd.vo.EaOrderInfo;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import redis.clients.jedis.Jedis;

import java.util.*;

/**
 * Author:  zsd
 * Date:  2025/1/15/周三 13:32
 */
public class CleanRepairDataTask {
    public static void main(String[] args) throws Exception {
      StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        CustomRedisSource customRedisSource = new CustomRedisSource();
        env.addSource(customRedisSource).addSink(new MysqlSink());
        env.execute("CleanRepairDataTask"+Config.PROFILES);


    }
    public static class MysqlSink  extends RichSinkFunction<String> {
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
        public void invoke(String value, Context context) throws Exception {
            //判断是否已经存在持仓
            jedis.select(Config.REDIS_DB_SOURCE);
            Map<String, String> traderMaps = jedis.hgetAll(Config.REDIS_TRADER_KEY);
            Map<String, JSONObject> traderNewMaps =new HashMap<>();
            traderMaps.forEach((k,v)->{
                JSONObject json = JSONObject.parseObject(v);
                Integer serverId = json.getInteger("server_id");
                String account = json.getString("account");
                Long id = json.getLong("id");
                traderNewMaps.put(serverId+account,json);
            });
            EaOrderInfo eaOrderInfo = JSONObject.parseObject(value, EaOrderInfo.class);
            JSONObject jsonObject = traderNewMaps.get(eaOrderInfo.getServerId() + eaOrderInfo.getSlaveAccount());

            if(jsonObject!=null){
                Long slaveId =jsonObject.getLong("id");
                jedis.select(Config.REDIS_DB_CLEAN);
                String s = jedis.get(Config.TRADER_ACTIVE + slaveId);
                if(s!=null && s.trim().length()>0){
                    boolean existsInActive =  s.contains("\"magicNumber\":"+eaOrderInfo.getTicket());
                    String repairStr  = jedis.hget(Config.REPAIR_SEND+eaOrderInfo.getAccount()+":"+eaOrderInfo.getMasterId(), eaOrderInfo.getSlaveAccount());
                    Map<Integer,OrderRepairInfoVO> repairInfoVOS = new HashMap();
                    if (repairStr!=null && repairStr.trim().length()>0){
                        repairInfoVOS= JSONObject.parseObject(repairStr, Map.class);
                    }
                    if (!existsInActive) {
                        OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                        orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                        orderRepairInfoVO.setMasterLots(eaOrderInfo.getLots());
                        orderRepairInfoVO.setMasterOpenTime(eaOrderInfo.getOpenTime());
                        JSONArray objects = JSONArray.parseArray(eaOrderInfo.getProfit());
                        orderRepairInfoVO.setMasterProfit(objects.get(1).toString());
                        orderRepairInfoVO.setMasterSymbol(eaOrderInfo.getSymbol());
                        orderRepairInfoVO.setMasterTicket(eaOrderInfo.getTicket());
                        orderRepairInfoVO.setMasterOpenPrice(eaOrderInfo.getOpenPrice());
                        orderRepairInfoVO.setMasterType(Op.forValue(eaOrderInfo.getType()).name());
                        orderRepairInfoVO.setMasterId(eaOrderInfo.getMasterId());
                        orderRepairInfoVO.setSlaveAccount(eaOrderInfo.getSlaveAccount());
                        orderRepairInfoVO.setSlaveType(Op.forValue(eaOrderInfo.getType()).name());
                        orderRepairInfoVO.setSlavePlatform(jsonObject.getString("platform"));
                        orderRepairInfoVO.setSlaveId(slaveId);
                        repairInfoVOS.put(eaOrderInfo.getTicket(),orderRepairInfoVO);
                        Map<String, String> rkey = jedis.hgetAll(eaOrderInfo.getRedisKey());
                        List<String> del=new ArrayList<>();
                        if(rkey!=null) {
                            Set<String> strings = rkey.keySet();
                            if(repairInfoVOS!=null && repairInfoVOS.size()>0) {
                                repairInfoVOS.keySet().forEach(k->{
                                    boolean contains = strings.contains(k.toString());
                                    if(!contains){
                                        del.add(k.toString());
                                    }
                                });
                            }
                            for (String string : del) {
                                repairInfoVOS.remove(string);
                            }
                            jedis.hset(Config.REPAIR_SEND+eaOrderInfo.getAccount()+":"+eaOrderInfo.getMasterId(),eaOrderInfo.getSlaveAccount(),JSON.toJSONString(repairInfoVOS));
                        }


                    }else{
                        jedis.hset(Config.REPAIR_SEND+eaOrderInfo.getAccount()+":"+eaOrderInfo.getMasterId(),eaOrderInfo.getSlaveAccount(),   JSON.toJSONString(repairInfoVOS));
                    }

                }
            }


           // super.invoke(value, context);
        }
    }
    public static class CustomRedisSource extends RichParallelSourceFunction<String> {
        private boolean flag = true;
        private Jedis jedis;

        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                jedis.select(Config.REDIS_DB_SOURCE);
                Map<String, String> traderMaps = jedis.hgetAll(Config.REDIS_TRADER_KEY);
                Map<String, JSONObject> traderNewMaps =new HashMap<>();
                traderMaps.forEach((k,v)->{
                    JSONObject json = JSONObject.parseObject(v);
                    String account = json.getString("account");
                    traderNewMaps.put(account,json);
                });
                jedis.select(Config.REDIS_DB_CLEAN);
                Set<String> keys = jedis.keys(Config.FOLLOW_REPAIR_SEND);
                //删除不需keys要的数据
              /*  Set<String> keyAlls = jedis.keys(Config.REPAIR_SEND_ALL);
                keyAlls.forEach(key->{
                    Map<String, String> allMap = jedis.hgetAll(key);
                    allMap.forEach((k,v)->{
                        if(keys==null || !keys.contains(k)){
                           jedis.hdel(key,k);
                        }
                    });
                });*/
                keys.forEach(key -> {
                    Map<String, String> stringStringMap = jedis.hgetAll(key);
                    stringStringMap.values().forEach(v->{
                        JSONArray array = JSONArray.parseArray(v);
                        if (array.size() > 0) {
                            String[] accounts = key.split("#");
                            if(accounts.length>4){
                                String account = accounts[4];
                                JSONObject object = traderNewMaps.get(account);
                                if(object!=null) {
                                    JSONObject jsonObject = JSONObject.parseObject(array.get(1).toString());
                                    jsonObject.put("slaveAccount", accounts[3]);
                                    jsonObject.put("server_id", object.getInteger("server_id"));
                                    jsonObject.put("redisKey", key);
                                    sourceContext.collect(jsonObject.toJSONString());
                                }
                            }



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



}
