package net.maku.mascontrol.binlog;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import jakarta.annotation.PostConstruct;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.util.FollowConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BinlogListener {

    @Value("${spring.datasource.dynamic.datasource.master.username}")
    private String username;

    @Value("${spring.datasource.dynamic.datasource.master.password}")
    private String password;

    private BinaryLogClient binaryLogClient;
    private final Map<Long, TableInfo> tableIdToInfoMap = new ConcurrentHashMap<>();
    @Autowired
    private CacheManager cacheManager;

    @PostConstruct
    public void start() throws Exception {
        // 解析 JDBC URL 获取 host 和 port（略）

        binaryLogClient = new BinaryLogClient(FollowConstant.LOCAL_HOST, 3306, username, password);
        binaryLogClient.setServerId(123456);

        // 监听 TableMapEvent 建立 tableId 映射
        binaryLogClient.registerEventListener(event -> {
            EventData data = event.getData();
            if (data instanceof TableMapEventData) {
                TableMapEventData tableMapEvent = (TableMapEventData) data;
                tableIdToInfoMap.put(
                        tableMapEvent.getTableId(),
                        new TableInfo(
                                tableMapEvent.getDatabase(),
                                tableMapEvent.getTable()
                        )
                );
            }
        });

        // 监听 Update 事件
        binaryLogClient.registerEventListener(event -> {
            EventData data = event.getData();
            if (data instanceof UpdateRowsEventData) {
                handleUpdateEvent((UpdateRowsEventData) data);
            }
        });

        // 监听 Insert 事件
        binaryLogClient.registerEventListener(event -> {
            EventData data = event.getData();
            if (data instanceof WriteRowsEventData) {
                handleInsertEvent((WriteRowsEventData) data);
            }
        });

        // 监听 Delete 事件
        binaryLogClient.registerEventListener(event -> {
            EventData data = event.getData();
            if (data instanceof DeleteRowsEventData) {
                handleDeleteEvent((DeleteRowsEventData) data);
            }
        });

        new Thread(() -> {
            try {
                binaryLogClient.connect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }

    // 处理 Update 事件
    private void handleUpdateEvent(UpdateRowsEventData eventData) {
        long tableId = eventData.getTableId();
        TableInfo tableInfo = tableIdToInfoMap.get(tableId);

        if (tableInfo == null) {
            return; // 未找到表信息
        }

        String database = tableInfo.getDatabase();
        String table = tableInfo.getTable();

        if (!"follow-order-cp".equals(database) || (!"follow_trader_subscribe".equals(table)&& !"follow_trader".equals(table))) {
            return;
        }

        // 遍历所有行变更
        for (Map.Entry<Serializable[], Serializable[]> row : eventData.getRows()) {
            Serializable[] after = row.getValue(); // 更新后的数据
            String id = String.valueOf(after[0]); // 假设主键是第一个字段
            if (after.length!=27){
                //账户表
                Cache cache = cacheManager.getCache("followFollowCache");
                if (cache != null) {
                    cache.evict(id); // 移除指定缓存条目
                }
            }else {
                //订阅关系表
                String slaveId = String.valueOf(after[2]);
                String masterId = String.valueOf(after[1]);
                //删除订阅缓存
                Cache cache = cacheManager.getCache("followSubscriptionCache");
                if (cache != null) {
                    cache.evict(generateCacheKey(slaveId,masterId)); // 移除指定缓存条目
                }
                //移除喊单的跟单缓存
                Cache cache1= cacheManager.getCache("followSubOrderCache");
                if (cache1 != null) {
                    cache1.evict(masterId); // 移除指定缓存条目
                }
                Cache cache3= cacheManager.getCache("followSubTraderCache");
                if (cache3 != null) {
                    cache3.evict(slaveId); // 移除指定缓存条目
                }
            }
        }
    }


    // 处理 Insert 事件
    private void handleInsertEvent(WriteRowsEventData eventData) {
        long tableId = eventData.getTableId();
        TableInfo tableInfo = tableIdToInfoMap.get(tableId);

        if (tableInfo == null) {
            return; // 未找到表信息
        }

        String database = tableInfo.getDatabase();
        String table = tableInfo.getTable();

        if (!"follow-order-cp".equals(database) || (!"follow_trader_subscribe".equals(table)&& !"follow_trader".equals(table))) {
            return;
        }

        // 遍历所有插入的行
        for (Serializable[] row : eventData.getRows()) {
            Serializable[] after = row.clone(); // 更新后的数据
            if (after.length==27){
                //订阅关系表
                String masterId = String.valueOf(after[2]); // 假设主键是第一个字段
                //移除喊单的跟单缓存
                Cache cache1= cacheManager.getCache("followSubOrderCache");
                if (cache1 != null) {
                    cache1.evict(masterId); // 移除指定缓存条目
                }
            }
        }
    }

    // 处理 Delete 事件
    private void handleDeleteEvent(DeleteRowsEventData eventData) {
        long tableId = eventData.getTableId();
        TableInfo tableInfo = tableIdToInfoMap.get(tableId);

        if (tableInfo == null) {
            return; // 未找到表信息
        }

        String database = tableInfo.getDatabase();
        String table = tableInfo.getTable();

        if (!"follow-order-cp".equals(database) || (!"follow_trader_subscribe".equals(table)&& !"follow_trader".equals(table))) {
            return;
        }

        // 遍历所有删除的行
        for (Serializable[] row : eventData.getRows()) {
            Serializable[] after = row.clone(); // 更新后的数据
            if (after.length==27) {
                String slaveId = String.valueOf(after[2]);
                String masterId = String.valueOf(after[1]);
                //删除订阅缓存
                Cache cache = cacheManager.getCache("followSubscriptionCache");
                if (cache != null) {
                    cache.evict(generateCacheKey(slaveId,masterId)); // 移除指定缓存条目
                }
                //移除喊单的跟单缓存
                Cache cache1= cacheManager.getCache("followSubOrderCache");
                if (cache1 != null) {
                    cache1.evict(masterId); // 移除指定缓存条目
                }
                Cache cache3= cacheManager.getCache("followSubTraderCache");
                if (cache3 != null) {
                    cache3.evict(slaveId); // 移除指定缓存条目
                }
            }else {
                //删除账户缓存
                String id = String.valueOf(after[0]);
                Cache cache2 = cacheManager.getCache("followFollowCache");
                if (cache2 != null) {
                    cache2.evict(id); // 修改指定缓存条目
                }
            }
        }
    }


    // 表信息存储类
    private static class TableInfo {
        private final String database;
        private final String table;

        public TableInfo(String database, String table) {
            this.database = database;
            this.table = table;
        }

        public String getDatabase() { return database; }
        public String getTable() { return table; }
    }

    private String generateCacheKey(String slaveId, String masterId) {
        if (slaveId != null && masterId != null) {
            return slaveId + "_" + masterId;
        } else {
            return "defaultKey";
        }
    }
}