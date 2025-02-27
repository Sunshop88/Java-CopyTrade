package com.flink.ods;

import com.alibaba.fastjson.JSON;
import com.flink.common.Config;
import com.flink.ods.mapper.MyRedisMapper;
import com.flink.ods.vo.AccountData;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import online.mtapi.mt4.QuoteEventHandler;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction;
import org.apache.flink.streaming.connectors.redis.RedisSink;
import org.apache.flink.streaming.connectors.redis.common.config.FlinkJedisPoolConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

/**
 * Author:  zsd
 * Date:  2025/1/6/周一 10:46
 */
public class GatherListenerDataTask {
    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        MyListenerSource myListenerSource = new MyListenerSource();
        FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new MyRedisMapper());
        env.addSource(myListenerSource).addSink(redisSink);
        env.execute("GatherListenerDataTask"+Config.PROFILES);
    }
    public static class  MyListenerSource extends RichParallelSourceFunction<String> {
      /* LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();*/
       List<QuoteClient> clients = new ArrayList<>();
        HashMap<String, List<String>> map = new HashMap<>();
        HashMap<Integer, List<AccountData>> traderMap = new HashMap<>();
        private boolean flag = true;

        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
                System.out.println("执行监控任务:");
            //   String take = queue.take();
                clients.forEach(client->{
                    List<AccountData> accountData = traderMap.get(client.User);
                    accountData.forEach(data->{
                        String platform = data.getPlatform();
                        Integer type = data.getType();
                        Integer platformId = data.getPlatformId();
                        Integer vpsId = data.getVpsId();
                        String vpsName = data.getVpsName();
                        AccountData    da= AccountData.builder().user(client.User).password(client.Password).credit(client.Credit).freeMargin(client.FreeMargin)
                                .equity(client.Equity).host(client.Host).profit(client.Profit).platform(platform).type(type).platformId(platformId)
                                .vpsId(vpsId).vpsName(vpsName).build();
                        List<Order> orders = Arrays.stream(client.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
                        if(orders!=null){
                            da.setNum(orders.size());
                            da.setOrders(orders);
                        }
                        String json = JSON.toJSONString(da);
                        sourceContext.collect(json);
                    });
                });
             //  Thread.sleep(1000);

            }
        }

        @Override
        public void cancel() {
            flag = false;
        }

        @Override
        public void open(OpenContext openContext) throws Exception {

            //1.加载数据库厂商提供的驱动
            Class.forName("com.mysql.cj.jdbc.Driver");//指定路径

            //2.获取数据库的连接                 固定写法        IP+端口号       数据库               字符集编码
            Connection connection = DriverManager.getConnection("jdbc:mysql://"+ Config.MYSQL_HOSTNAME+":3306/"+Config.MYSQL_DB+"?charterEncoding=utf-8&useSSL=false",
                    Config.MYSQL_USERNAME, Config.MYSQL_PWD);//通过实现类来以获取数据库连接Connnection是Java中的类
            //3.创建Statement对象
            Statement statement = connection.createStatement();

            //4.定义SQL语句
            String sql="select account,password,platform,type,platform_id,server_id,server_name from follow_trader";
            String platformSql= "SELECT server_node,`server` FROM follow_platform";

            ResultSet resultSet = statement.executeQuery(platformSql);
            while (resultSet.next()) {
                String serverNode = resultSet.getString("server_node");
                String platform = resultSet.getString("server");
                List<String> vals = map.get(platform);
                if(vals==null){
                    vals=new ArrayList<>();
                }
                vals.add(serverNode);
                map.put(platform,vals);
            }
            //5.执行SQL语句
            ResultSet resultSetTwo= statement.executeQuery(sql);
            while (resultSetTwo.next()) {
                int account = resultSetTwo.getInt("account");
                List<AccountData> accountDatas = traderMap.get(account);
                if(accountDatas==null){
                    accountDatas=new ArrayList<>();
                }
                String password = resultSetTwo.getString("password");
                String platform = resultSetTwo.getString("platform");

                Integer type = resultSetTwo.getInt("type");
                Integer platformId = resultSetTwo.getInt("platform_id");
                Integer vpsId = resultSetTwo.getInt("server_id");
                String vpsName = resultSetTwo.getString("server_name");
                AccountData info = new AccountData();
                info.setType(type);
                info.setPlatform(platform);
                info.setPlatformId(platformId);
                info.setVpsId(vpsId);
                info.setVpsName(vpsName);
                info.setUser(account);
                accountDatas.add(info);
                List<String> nodes = map.get(platform);
                traderMap.put(account,accountDatas);
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        String serverNode = nodes.get(i);
                        String[] split = serverNode.split(":");
                        try {
                            QuoteClient   client = new QuoteClient(account, password, split[0], Integer.parseInt(split[1]));
                            client.Connect();
                            clients.add(client);
                            System.out.println("登录成功:"+account);
                     /*       client.OnQuote.addListener(new QuoteEventHandler() {
                                @Override
                                public void invoke(Object sender, QuoteEventArgs quoteEventArgs) {
                                    QuoteClient qc = (QuoteClient) sender;
                                    AccountData    da= AccountData.builder().user(client.User).password(client.Password).credit(client.Credit).freeMargin(client.FreeMargin)
                                            .equity(client.Equity).host(client.Host).profit(client.Profit).platform(platform).type(type).platformId(platformId)
                                            .vpsId(vpsId).vpsName(vpsName).build();
                                    List<Order> orders = Arrays.stream(qc.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
                                    if(orders!=null){
                                        da.setNum(orders.size());
                                        da.setOrders(orders);
                                    }
                                    String json = JSON.toJSONString(da);
                                    try {
                                        queue.put(json);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            });*/

                        } catch (Exception e) {
                            System.out.println("登录失败:"+account);
                           e.printStackTrace();
                        }
                    }
                }
            }

            super.open(openContext);
        }
    }
}
