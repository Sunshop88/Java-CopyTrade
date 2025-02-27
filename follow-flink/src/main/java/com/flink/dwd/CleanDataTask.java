package com.flink.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.dwd.vo.FollowTraderAnalysis;
import com.flink.ods.mapper.MyRedisMapper;
import com.flink.ods.vo.AccountData;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;

import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Author:  zsd
 * Date:  2025/1/6/周一 11:56
 * 清洗任务
 */
public class CleanDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        CustomRedisSource customRedisSource = new CustomRedisSource();
       /* env.addSource(customRedisSource).print();*/
        env.addSource(customRedisSource).map(row->{
            AccountData accountData = JSONObject.parseObject(row.toString(), AccountData.class);
            //开始清洗
            Map<String,FollowTraderAnalysis> map=new HashMap<>();
            List<Order> orders = accountData.getOrders();
            if(orders!=null&&orders.size()>0) {
                orders.forEach(order -> {
                    String key = accountData.getUser() + order.Symbol;
                    FollowTraderAnalysis analysis = map.get(key);
                    double lots = 0.00;
                    Integer num = 0;
                    double profit = 0.00;
                    Integer buyNum = 0;
                    double buyLots = 0.00;
                    double buyProfit = 0.00;

                    Integer sellNum = 0;
                    double sellLots = 0.00;
                    double sellProfit = 0.00;
                    if (analysis == null) {
                        analysis = new FollowTraderAnalysis();
                    } else {
                        lots = analysis.getLots();
                        profit = analysis.getProfit();
                        buyNum = analysis.getBuyNum() == null ? 0 : analysis.getBuyNum();
                        buyLots = analysis.getBuyLots() == null ? 0 : analysis.getBuyLots();
                        buyProfit = analysis.getBuyProfit() == null ? 0.00 : analysis.getBuyProfit();
                        sellNum = analysis.getSellNum() == null ? 0 : analysis.getSellNum();
                        sellLots = analysis.getSellLots() == null ? 0 : analysis.getSellLots();
                        sellProfit = analysis.getSellProfit() == null ? 0 : analysis.getSellProfit();

                    }
                    //公用数据
                    analysis.setAccount(accountData.getUser());
                    analysis.setPlatform(accountData.getPlatform());
                    analysis.setPlatformId(accountData.getPlatformId());
                    analysis.setType(accountData.getType());
                    analysis.setVpsId(accountData.getVpsId());
                    analysis.setVpsName(accountData.getVpsName());
                    analysis.setSymbol(order.Symbol);
                    analysis.setLots(lots + order.Lots);
                    analysis.setNum(num + 1);
                    analysis.setProfit(profit + order.Profit);
                    if (order.Type == Op.Buy) {
                        analysis.setBuyNum(buyNum + 1);
                        analysis.setBuyLots(buyLots + order.Lots);
                        analysis.setBuyProfit(buyProfit + order.Profit);
                    }
                    if (order.Type == Op.Sell) {
                        analysis.setSellNum(sellNum + 1);
                        analysis.setSellLots(sellLots + order.Lots);
                        analysis.setSellProfit(sellProfit + order.Profit);
                    }
                    double blots = analysis.getBuyLots() == null ? 0.00 : analysis.getBuyLots();
                    double slots = analysis.getSellLots() == null ? 0.00 : analysis.getSellLots();
                    double position = blots - slots;
                    analysis.setPosition(position);
                    map.put(key, analysis);
                });

            }
            String json = JSON.toJSONString(map.values());
            return json;
        }).addSink(new  RichSinkFunction<String>(){
            private   String sql = "INSERT INTO follow_trader_analysis (account, vps_id,symbol, vps_name,platform, platform_id,source_account,source_platform,position,lots, num,profit,buy_num, buy_lots, buy_profit,  sell_num, sell_lots,sell_profit, type ) VALUES( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )"+
                                   " ON DUPLICATE KEY UPDATE position=VALUES(position),lots=VALUES(lots), num=VALUES(num),profit=VALUES(profit),buy_num=VALUES(buy_num), buy_lots=VALUES(buy_lots), buy_profit=VALUES(buy_profit),  sell_num=VALUES(sell_num), sell_lots=VALUES(sell_lots),sell_profit=VALUES(sell_profit) ";
            private  Connection connection;
            private  PreparedStatement preparedStatement;

            @Override
            public void open(OpenContext openContext) throws Exception {
                //1.加载数据库厂商提供的驱动
                Class.forName("com.mysql.cj.jdbc.Driver");//指定路径

                //2.获取数据库的连接                 固定写法        IP+端口号       数据库               字符集编码
                connection = DriverManager.getConnection("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false",
                        Config.MYSQL_USERNAME, Config.MYSQL_PWD);//通过实现类来以获取数据库连接Connnection是Java中的类
                //3.创建preparedStatement 对象
                preparedStatement = connection.prepareStatement(sql);
                
                super.open(openContext);
            }


            @Override
            public void invoke(String value, SinkFunction.Context context) throws Exception {
                List<FollowTraderAnalysis> list = JSON.parseArray(value, FollowTraderAnalysis.class);
                if(list!=null&&list.size()>0) {
                    list.forEach(analysis -> {
                        try {
                            preparedStatement.setInt(1, analysis.getAccount());
                            preparedStatement.setInt(2, analysis.getVpsId());
                            preparedStatement.setString(3, analysis.getSymbol());
                            preparedStatement.setString(4, analysis.getVpsName());
                            preparedStatement.setString(5, analysis.getPlatform());
                            preparedStatement.setInt(6, analysis.getPlatformId());
                            preparedStatement.setString(7, null);
                            preparedStatement.setString(8, null);
                            preparedStatement.setBigDecimal(9, new BigDecimal(analysis.getPosition()==null?0.00:analysis.getPosition()));
                            preparedStatement.setBigDecimal(10, new BigDecimal(analysis.getLots()==null?0.00:analysis.getLots()));
                            preparedStatement.setInt(11, analysis.getNum()==null?0:analysis.getNum());
                            preparedStatement.setBigDecimal(12, new BigDecimal(analysis.getProfit()==null?0.00:analysis.getProfit()));
                            preparedStatement.setInt(13, analysis.getBuyNum()==null?0:analysis.getBuyNum());
                            preparedStatement.setBigDecimal(14, new BigDecimal(analysis.getBuyLots()==null?0.00:analysis.getBuyLots()));
                            preparedStatement.setBigDecimal(15, new BigDecimal(analysis.getBuyProfit()==null?0.00:analysis.getBuyProfit()));
                            preparedStatement.setInt(16, analysis.getSellNum()==null ? 0 : analysis.getSellNum());
                            preparedStatement.setBigDecimal(17, new BigDecimal(analysis.getSellLots()==null?0.00:analysis.getSellLots()));
                            preparedStatement.setBigDecimal(18, new BigDecimal(analysis.getSellProfit()==null?0.00:analysis.getSellProfit()));
                            preparedStatement.setInt(19, analysis.getType());
                            preparedStatement.executeUpdate();
                        } catch (SQLException e) {
                          e.printStackTrace();
                        }
                    });
                }
            }

            @Override
            public void close() throws Exception {
                super.close();
                // 关闭 PreparedStatement 和 Connection
                if (preparedStatement != null) {
                    preparedStatement.close();
                }
                if (connection != null) {
                    connection.close();
                }

            }
        });
        env.execute("CleanDataTask"+Config.PROFILES);

    }

    public static class CustomRedisSource extends RichParallelSourceFunction<String> {
        private boolean flag = true;
        private    Jedis jedis;

        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                Set<String> keys = jedis.keys("*");
                keys.forEach(key -> {
                    String data = jedis.lpop(key);
                    jedis.del(key);
                    System.out.println("清洗数据:"+data );
                    if (data!=null){
                        sourceContext.collect(data);
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
            jedis.select(Config.REDIS_DB);

            super.open(openContext);
        }
    }

}
