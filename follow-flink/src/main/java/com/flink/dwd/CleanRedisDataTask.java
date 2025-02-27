package com.flink.dwd;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.flink.common.Config;
import com.flink.dwd.vo.FollowTraderAnalysis;
import com.flink.ods.vo.AccountData;

import online.mtapi.mt4.Order;
import online.mtapi.mt4.Op;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import redis.clients.jedis.Jedis;

import java.math.BigDecimal;
import java.util.*;

/**
 * Author:  zsd
 * Date:  2025/1/7/周二 14:21
 */
public class CleanRedisDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
      CustomRedisSource customRedisSource = new CustomRedisSource();
        env.addSource(customRedisSource).map(row->{
            AccountData accountData = JSONObject.parseObject(row.toString(), AccountData.class);
            //开始清洗
            Map<String, FollowTraderAnalysis> map=new HashMap<>();
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
                        num=analysis.getNum();
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
                    analysis.setFreeMargin(accountData.getFreeMargin());
                    analysis.setSymbol(order.Symbol);
                    analysis.setLots(lots + order.Lots);
                    analysis.setNum(num + 1);
                    analysis.setProfit(profit + order.Profit);
                    BigDecimal bigDecimal = new BigDecimal(accountData.getEquity()).setScale(5, BigDecimal.ROUND_HALF_UP);
                    analysis.setEquity(bigDecimal.doubleValue());
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
            }else{
                FollowTraderAnalysis analysis =new FollowTraderAnalysis();
                analysis.setAccount(accountData.getUser());
                analysis.setPlatform(accountData.getPlatform());
                analysis.setPlatformId(accountData.getPlatformId());
                analysis.setType(accountData.getType());
                analysis.setVpsId(accountData.getVpsId());
                analysis.setVpsName(accountData.getVpsName());
                analysis.setSymbol(null);
                map.put(accountData.getUser().toString(), analysis);
            }
            String json = JSON.toJSONString(map.values());
            return json;
        }).addSink(new  RichSinkFunction<String>(){
            private Jedis jedis;

            @Override
            public void open(OpenContext openContext) throws Exception {
                this.jedis = new Jedis(Config.REDIS_HOSTNAME, 6379);
                jedis.auth(Config.REDIS_PWD);
                jedis.select(Config.REDIS_DB_CLEAN);

                super.open(openContext);
            }


            @Override
            public void invoke(String value, SinkFunction.Context context) throws Exception {

                List<FollowTraderAnalysis> list = JSON.parseArray(value, FollowTraderAnalysis.class);
                if(list!=null&&list.size()>0) {
                    List<String> symbolKey=new ArrayList<>();
                    String accountKey = list.get(0).getVpsId() + ":" + list.get(0).getAccount() + ":" + list.get(0).getPlatformId();
                    list.forEach(analysis -> {
                        if(analysis.getSymbol()!=null) {
                            analysis.setPosition(analysis.getPosition() == null ? 0.00 : analysis.getPosition());
                            analysis.setLots(analysis.getLots() == null ? 0.00 : analysis.getLots());
                            analysis.setNum(analysis.getNum() == null ? 0 : analysis.getNum());
                            analysis.setProfit(analysis.getProfit() == null ? 0.00 : analysis.getProfit());
                            analysis.setBuyNum(analysis.getBuyNum() == null ? 0 : analysis.getBuyNum());
                            analysis.setBuyLots((analysis.getBuyLots() == null ? 0.00 : analysis.getBuyLots()));
                            analysis.setBuyProfit(analysis.getBuyProfit() == null ? 0.00 : analysis.getBuyProfit());
                            analysis.setSellNum(analysis.getSellNum() == null ? 0 : analysis.getSellNum());
                            analysis.setSellLots(analysis.getSellLots() == null ? 0.00 : analysis.getSellLots());
                            analysis.setSellProfit(analysis.getSellProfit() == null ? 0.00 : analysis.getSellProfit());
                            String key = analysis.getSymbol() + ":" + analysis.getVpsId() + ":" + analysis.getAccount() + ":" + analysis.getPlatformId();
                            jedis.hset(Config.REDIS_CLEAN_KEY, key, JSON.toJSONString(analysis));
                            symbolKey.add(key);
                        }
                    });
                    jedis.hset(Config.REDIS_CLEAN_ACCOUNT_KEY, accountKey, JSON.toJSONString(symbolKey));
                }
            }

            @Override
            public void close() throws Exception {
                super.close();
                // 关闭 PreparedStatement 和 Connection
                if (jedis != null) {
                    jedis.close();
                }

            }
        });
        env.execute("CleanRedisDataTask"+Config.PROFILES);

    }

    public static class CustomRedisSource extends RichParallelSourceFunction<String> {
        private boolean flag = true;
        private Jedis jedis;

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
