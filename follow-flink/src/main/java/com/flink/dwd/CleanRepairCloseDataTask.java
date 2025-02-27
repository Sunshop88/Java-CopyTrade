package com.flink.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.dwd.vo.EaOrderInfo;
import com.flink.dwd.vo.OrderRepairInfoVO;
import com.flink.enums.Op;
import com.flink.enums.TraderRepairOrderEnum;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import redis.clients.jedis.Jedis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Author:  zsd
 * Date:  2025/1/15/周三 19:11
 */
public class CleanRepairCloseDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        CustomRedisSource customRedisSource = new CustomRedisSource();
        env.setParallelism(1);
        env.addSource(customRedisSource).addSink(new MysqlSink());
        env.execute("CleanRepairCloseDataTask"+Config.PROFILES);


    }
    public static class MysqlSink  extends RichSinkFunction<String> {
        private Jedis jedis;
        private Statement statement;
        @Override
        public void open(Configuration parameters) throws Exception {
            this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
            jedis.auth(Config.REDIS_PWD);
            jedis.select(Config.REDIS_DB_CLEAN);
            //1.加载数据库厂商提供的驱动
            Class.forName("com.mysql.cj.jdbc.Driver");//指定路径

            //2.获取数据库的连接                 固定写法        IP+端口号       数据库               字符集编码
            Connection connection = DriverManager.getConnection("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false",
                    Config.MYSQL_USERNAME, Config.MYSQL_PWD);//通过实现类来以获取数据库连接Connnection是Java中的类
            //3.创建Statement对象
            statement = connection.createStatement();
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
                traderNewMaps.put(serverId+account,json);
            });
            EaOrderInfo eaOrderInfo = JSONObject.parseObject(value, EaOrderInfo.class);
            JSONObject jsonObject = traderNewMaps.get(eaOrderInfo.getServerId() + eaOrderInfo.getSlaveAccount());
       //     jedis.hset(Config.REPAIR_CLOSE_two + eaOrderInfo.getAccount(),eaOrderInfo.getSlaveAccount() , eaOrderInfo.getServer()+"---->"+jsonObject+"---->"+traderNewMaps);
            if(jsonObject!=null) {
                Long slaveId = jsonObject.getLong("id");
                jedis.select(Config.REDIS_DB_CLEAN);
                String s = jedis.get(Config.TRADER_ACTIVE + slaveId);
                if(s!=null && s.trim().length()>0) {
                String sql = "SELECT * FROM  follow_order_detail WHERE trader_id=" + slaveId + " and magical=" + eaOrderInfo.getTicket() +" and type!=6";
                ResultSet resultSet = statement.executeQuery(sql);
                boolean existsInActive = s.contains("\"magicNumber\":" + eaOrderInfo.getTicket());
                String repairStr = jedis.hget(Config.REPAIR_CLOSE + eaOrderInfo.getAccount()+":"+eaOrderInfo.getMasterId(), eaOrderInfo.getSlaveAccount());
                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap();
                if (repairStr != null && repairStr.trim().length() > 0) {
                    repairInfoVOS = JSONObject.parseObject(repairStr, Map.class);
                }
               while (resultSet.next()) {
                        if (existsInActive) {
                        OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                        orderRepairInfoVO.setMasterOpenTime(eaOrderInfo.getOpenTime());
                        orderRepairInfoVO.setMasterCloseTime(eaOrderInfo.getCloseTime());
                        orderRepairInfoVO.setMasterSymbol(eaOrderInfo.getSymbol());
                        orderRepairInfoVO.setMasterOpenPrice(eaOrderInfo.getOpenPrice());
                        orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.CLOSE.getType());
                        orderRepairInfoVO.setMasterLots(eaOrderInfo.getLots());
                        JSONArray objects = JSONArray.parseArray(eaOrderInfo.getProfit());
                        orderRepairInfoVO.setMasterProfit(objects.get(1).toString());
                        orderRepairInfoVO.setMasterOpenPrice(eaOrderInfo.getOpenPrice());
                        //orderRepairInfoVO.setMasterProfit(eaOrderInfo.getProfit());
                        orderRepairInfoVO.setMasterType(Op.forValue(eaOrderInfo.getType()).name());
                        orderRepairInfoVO.setMasterTicket(eaOrderInfo.getTicket());
                        orderRepairInfoVO.setSlaveLots(eaOrderInfo.getLots());
                        orderRepairInfoVO.setSlaveType(Op.forValue(eaOrderInfo.getType()).name());
                        orderRepairInfoVO.setSlaveOpenTime(resultSet.getString("open_time"));
                        orderRepairInfoVO.setSlaveOpenPrice(eaOrderInfo.getOpenPrice());
                        orderRepairInfoVO.setSlaveCloseTime(resultSet.getString("close_time"));
                        orderRepairInfoVO.setSlaveSymbol(resultSet.getString("symbol"));
                        orderRepairInfoVO.setSlaveAccount(resultSet.getString("account"));
                        orderRepairInfoVO.setSlavePlatform(resultSet.getString("server"));
                        orderRepairInfoVO.setSlaveTicket(resultSet.getInt("order_no"));
                        orderRepairInfoVO.setSlaverProfit(resultSet.getDouble("profit"));
                        orderRepairInfoVO.setMasterId(eaOrderInfo.getMasterId());
                        orderRepairInfoVO.setSlavePlatform(jsonObject.getString("platform"));
                        orderRepairInfoVO.setSlaveId(resultSet.getLong("trader_id"));
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

                        }
                        jedis.hset(Config.REPAIR_CLOSE + eaOrderInfo.getAccount()+":"+eaOrderInfo.getMasterId(), eaOrderInfo.getSlaveAccount(), JSON.toJSONString(repairInfoVOS));
                    } else {
                          repairInfoVOS.remove(eaOrderInfo.getTicket());
                        jedis.hset(Config.REPAIR_CLOSE + eaOrderInfo.getAccount()+":"+eaOrderInfo.getMasterId(), eaOrderInfo.getSlaveAccount(), JSON.toJSONString(repairInfoVOS));

                    }
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
                    Long id = json.getLong("id");
                    traderNewMaps.put(account,json);
                });
                jedis.select(Config.REDIS_DB_CLEAN);
                Set<String> keys = jedis.keys(Config.FOLLOW_REPAIR_CLOSE);

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
