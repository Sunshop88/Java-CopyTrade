package com.flink.ods;

import com.alibaba.fastjson.JSON;
import com.flink.common.Config;
import com.flink.ods.mapper.MyRedisMapper;
import com.flink.ods.vo.AccountData;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.cdc.connectors.mysql.source.MySqlSource;
import org.apache.flink.cdc.debezium.JsonDebeziumDeserializationSchema;
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
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

/**
 * Author:  zsd
 * Date:  2025/1/9/周四 9:33
 */
public class GatherMysqlListenerDataTask {
    public static void main(String[] args) throws Exception {

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        MyListenerSource myListenerSource = new MyListenerSource();
        FlinkJedisPoolConfig redisConfig = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(Config.REDIS_DB).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink = new RedisSink<>(redisConfig,new MyRedisMapper());
        env.addSource(myListenerSource).addSink(redisSink);
        //构建监听mysql
        MySqlSource<String> mySqlSource = MySqlSource.<String>builder()
                .hostname(Config.MYSQL_HOSTNAME)
                .port(3306)
                .databaseList(Config.MYSQL_DB)
                .tableList(Config.MYSQL_DB+".follow_trader",Config.MYSQL_DB+".follow_platform")
                .username(Config.MYSQL_USERNAME)
                .password(Config.MYSQL_PWD)
                .deserializer(new JsonDebeziumDeserializationSchema())
                .build();
        FlinkJedisPoolConfig redisConfig2 = new FlinkJedisPoolConfig.Builder().setHost(Config.REDIS_HOSTNAME)
                .setPort(6379).setDatabase(8).setPassword(Config.REDIS_PWD).build();
        RedisSink<String> redisSink2 = new RedisSink<>(redisConfig2,new MyRedisMapper());
        env.fromSource(mySqlSource, WatermarkStrategy.noWatermarks(), "gatherMysql").addSink(redisSink2);
        env.execute();
        env.execute("GatherRedisDataTask"+Config.PROFILES);
    }

    public static class  MyListenerSource extends RichParallelSourceFunction<String> {
        List<QuoteClient> clients = new ArrayList<>();
        List<AccountData> errors = new ArrayList<>();
        HashMap<String, List<String>> map = new HashMap<>();
        HashMap<Integer, List<AccountData>> traderMap = new HashMap<>();
        private boolean flag = true;

        @Override
        public void run(SourceContext<String> sourceContext) throws Exception {
            while (flag) {
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
                //尝试重新登陆
                if (errors!=null && errors.size()>0) {
                    ArrayList<AccountData> success = new ArrayList<>();
                    errors.forEach(o->{
                        List<String> nodes = map.get(o.getPlatform());
                        if (nodes != null) {
                            for (int i = 0; i < nodes.size(); i++) {
                                String serverNode = nodes.get(i);
                                String[] split = serverNode.split(":");
                                try {
                                    QuoteClient   client = new QuoteClient(o.getUser(), o.getPassword(), split[0], Integer.parseInt(split[1]));
                                    client.Connect();
                                    clients.add(client);
                                    success.add(o);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                    if(success!=null && success.size()>0){
                        errors.removeAll(success);
                    }

                }
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
                            if(errors!=null && errors.size()>0){
                                AccountData accountData = errors.get(errors.size() - 1);
                                if(accountData.getUser().equals(account) && accountData.getVpsId().equals(vpsId)){
                                    errors.remove(accountData);
                                }
                            }
                        } catch (Exception e) {
                            errors.add(info);
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
