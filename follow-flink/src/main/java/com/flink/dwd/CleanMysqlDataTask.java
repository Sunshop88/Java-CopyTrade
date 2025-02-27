package com.flink.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.dwd.vo.SubscribeVo;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.tuple.Tuple19;
import org.apache.flink.api.java.tuple.Tuple8;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.datastream.api.ExecutionEnvironment;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import redis.clients.jedis.Jedis;
import com.flink.dwd.vo.FollowTraderAnalysis;

import java.math.BigDecimal;
import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.flink.table.api.Expressions.$;
import static org.apache.flink.table.api.Expressions.jsonObject;

/**
 * Author:  zsd
 * Date:  2025/1/13/周一 10:10
 */
public class CleanMysqlDataTask {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        CleanSource cleanSource = new CleanSource();
        env.addSource(cleanSource).addSink(new MysqlSink());
        env.execute("CleanMysqlDataTask"+Config.PROFILES);


    }
    public static class MysqlSink  extends RichSinkFunction<String> {
        private Statement statement;
        private Jedis jedis;
        @Override
        public void invoke(String data, Context context) throws Exception {

            List<FollowTraderAnalysis> list = JSONArray.parseArray(data, FollowTraderAnalysis.class);

            String sqlSubscribe="SELECT master_id,master_account,slave_id,slave_account,platform FROM follow_trader_subscribe s LEFT JOIN follow_trader t ON s.master_id=t.id";
            ResultSet resultSet = statement.executeQuery(sqlSubscribe);
            Map<Integer,SubscribeVo> map = new HashMap<>();
            while (resultSet.next()) {
                long masterId = resultSet.getLong("master_id");
                Integer masterAccount = resultSet.getInt("master_account");
                long slaveId = resultSet.getLong("slave_id");
                Integer slaveAccount = resultSet.getInt("slave_account");
                String masterPlatform = resultSet.getString("platform");
                SubscribeVo vo = SubscribeVo.builder().masterId(masterId).masterAccount(masterAccount).masterPlatform(masterPlatform).slaveAccount(slaveAccount).slaveId(slaveId).build();
                map.put(slaveAccount,vo);
            }
            String vpsSql="SELECT id,name FROM follow_vps";
            ResultSet resultMapSet = statement.executeQuery(vpsSql);
            Map<Integer,String> vpsMap = new HashMap<>();
            while (resultMapSet.next()) {
                Integer vpsId = resultMapSet.getInt("id");
                String vpsName = resultMapSet.getString("name");
                vpsMap.put(vpsId,vpsName);
            }
            //构建sql
            StringBuilder sql=new StringBuilder();
            sql.append("INSERT INTO follow_trader_analysis (" +
                    "account,vps_id,symbol,vps_name,platform,platform_id,source_account,source_platform,position," +
                    "lots,num,profit,buy_num,buy_lots,buy_profit,sell_num,sell_lots,sell_profit,type,free_margin,equity,proportion" +
                    ") VALUES ");
          /*  String delSql="DELETE FROM follow_trader_analysis";
            statement.execute(delSql);*/
            list.forEach(o->{
                FollowTraderAnalysis followTraderAnalysis = o;
                SubscribeVo subscribeVo = map.get(followTraderAnalysis.getAccount());
                if(subscribeVo!=null){
                    Integer masterAccount = subscribeVo.getMasterAccount();
                    String masterPlatform = subscribeVo.getMasterPlatform();
                    followTraderAnalysis.setSourceAccount(masterAccount.toString());
                    followTraderAnalysis.setSourcePlatform(masterPlatform);
                }
                if(followTraderAnalysis.getType()==0){
                    followTraderAnalysis.setSourceAccount(followTraderAnalysis.getAccount().toString());
                    followTraderAnalysis.setSourcePlatform(followTraderAnalysis.getPlatform());
                }
                sql.append("(");
                  sql.append(followTraderAnalysis.getAccount()+","+followTraderAnalysis.getVpsId()+",'"+followTraderAnalysis.getSymbol()+"','"+vpsMap.get(followTraderAnalysis.getVpsId())+"','");
                  sql.append(followTraderAnalysis.getPlatform()+"',"+followTraderAnalysis.getPlatformId()+","+followTraderAnalysis.getSourceAccount()+",'"+followTraderAnalysis.getSourcePlatform()+"',");
                  sql.append(followTraderAnalysis.getPosition()+","+followTraderAnalysis.getLots()+","+followTraderAnalysis.getNum()+","+followTraderAnalysis.getProfit()+",");
                  sql.append(followTraderAnalysis.getBuyNum()+","+followTraderAnalysis.getBuyLots()+","+followTraderAnalysis.getBuyProfit()+","+followTraderAnalysis.getSellNum()+",");
                  sql.append(followTraderAnalysis.getSellLots()+","+followTraderAnalysis.getSellProfit()+","+followTraderAnalysis.getType()+","+followTraderAnalysis.getFreeMargin());
                double v=0.00;
                if(followTraderAnalysis.getEquity()!=null && followTraderAnalysis.getEquity()!=0){
                  v = new BigDecimal(followTraderAnalysis.getEquity()).divide(new BigDecimal(followTraderAnalysis.getFreeMargin()),5, BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100)).doubleValue();
                }else{
                   followTraderAnalysis.setEquity(0.00);
                }
                double v1 = new BigDecimal(followTraderAnalysis.getEquity()).setScale(5, BigDecimal.ROUND_HALF_UP).doubleValue();
                double v2 = new BigDecimal(v).setScale(5, BigDecimal.ROUND_HALF_UP).doubleValue();
                sql.append(","+v1+","+v2);
                  sql.append("),");
            });
            int i = sql.toString().lastIndexOf(",");
            String substring = sql.substring(0, i);
            substring+="on duplicate key update  ";
            substring+="vps_name = values(vps_name),source_account = values(source_account),source_platform = values(source_platform), position = values(position), lots = values(lots), num = values(num), profit = values(profit), free_margin = values(free_margin), ";
            substring+=" buy_num = values(buy_num), buy_lots = values(buy_lots), buy_profit = values(buy_profit), sell_num = values(sell_num), sell_lots = values(sell_lots),sell_profit = values(sell_profit),equity = values(equity),proportion = values(proportion)";

            if(list!=null && list.size()>0){
                statement.execute(substring);
            }

            //增加删除
            Map<String, String> vals = jedis.hgetAll(Config.REDIS_CLEAN_ACCOUNT_KEY);
            vals.forEach((k,value)->{
                String[] split = k.split(":");
                Integer vpsId=Integer.valueOf(split[0]);
                Integer account=Integer.valueOf(split[1]);
                Integer platformId=Integer.valueOf(split[2]);
                StringBuilder symbol=new StringBuilder();
                if(value!=null){
                    List<String> ls = JSONArray.parseArray(value, String.class);
                    //增加删除
                    ls.forEach(x->{
                        String[] sp = x.split(":");
                        symbol.append("'");
                        symbol.append(sp[0]);
                        symbol.append("'");
                        symbol.append(",");
                    });


                }

                String sqlRmove=null;
                if(symbol.length()>3){
                    int i1 = symbol.toString().lastIndexOf(",");
                    String sq = symbol.toString().substring(0, i1);
                    sqlRmove="DELETE FROM follow_trader_analysis WHERE symbol not in ("+sq+")  AND account="+account+" AND platform_id="+platformId+" AND vps_id="+vpsId;
                    jedis.hset(Config.REDIS_CLEAN_ACCOUNT_KEY_DEL_SQL,account.toString(),sqlRmove);
                }
                else{
                    sqlRmove="DELETE FROM follow_trader_analysis WHERE  account="+account+" AND platform_id="+platformId+" AND vps_id="+vpsId;
                }
                try {
                    statement.execute(sqlRmove);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                Map<String, String> delMap = jedis.hgetAll(Config.REDIS_CLEAN_ACCOUNT_KEY_DEL);
                System.out.println("开始1");
                delMap.forEach((k1,v1)->{
                    try {
                        JSONObject before = JSON.parseObject(v1);
                        Integer v = before.getInteger("server_id");
                        Integer a = before.getInteger("account");
                        Integer p = before.getInteger("platform_id");
                        Integer num = before.getInteger("num");
                        String   delSql="DELETE FROM follow_trader_analysis WHERE  account="+a+" AND platform_id="+p+" AND vps_id="+v;
                        statement.execute(delSql);
                        String accountKey = v + ":" + a + ":" + p;
                        String val = jedis.hget(Config.REDIS_CLEAN_ACCOUNT_KEY, accountKey);
                        if(val!=null){
                            JSONArray arr = JSONArray.parseArray(val);
                            for (Object o : arr) {
                                jedis.hdel(Config.REDIS_CLEAN_KEY,o.toString());
                            }
                        }
                        jedis.hdel(Config.REDIS_CLEAN_ACCOUNT_KEY,accountKey);
                      if(num!=null && num>100){
                            jedis.hdel(Config.REDIS_CLEAN_ACCOUNT_KEY_DEL,k1);
                        }else{
                            int n = num == null ? 1 : num + 1;
                            before.put("num",n);
                            jedis.hset(Config.REDIS_CLEAN_ACCOUNT_KEY_DEL,k1,before.toJSONString());
                        }
                        int n = num == null ? 1 : num + 1;
                        before.put("num",n);
                        jedis.hset(Config.REDIS_CLEAN_ACCOUNT_KEY_DEL,k1,before.toJSONString());
                        System.out.println("开始2");
                    } catch (SQLException e) {
                       e.printStackTrace();
                    }

                });
                System.out.println("开始3");

            });
          //  super.invoke(data, context);
        }

        @Override
        public void open(Configuration parameters) throws Exception {
            //1.加载数据库厂商提供的驱动
            Class.forName("com.mysql.cj.jdbc.Driver");//指定路径

            //2.获取数据库的连接                 固定写法        IP+端口号       数据库               字符集编码
            Connection connection = DriverManager.getConnection("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false",
                    Config.MYSQL_USERNAME, Config.MYSQL_PWD);//通过实现类来以获取数据库连接Connnection是Java中的类
            //3.创建Statement对象
            statement = connection.createStatement();
            this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
            jedis.auth(Config.REDIS_PWD);
            jedis.select(Config.REDIS_DB_CLEAN);
            super.open(parameters);
        }

        @Override
        public void close() throws Exception {
            super.close();
        }
    }
    public static class  CleanSource extends RichParallelSourceFunction<String> {
        private Jedis jedis;
        private boolean flag = true;




        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                jedis.select(Config.REDIS_DB_CLEAN);
                Map<String, String> map = jedis.hgetAll(Config.REDIS_CLEAN_KEY);
                Collection<String> values = map.values();
             //   System.out.println("数据REDIS_CLEAN_KEY："+jedis.exists(Config.REDIS_CLEAN_KEY));
                sourceContext.collect(values.toString());
            }
        }

        @Override
        public void cancel() {
            flag = false;
        }

        @Override
        public void close() throws Exception {
            super.close();
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
