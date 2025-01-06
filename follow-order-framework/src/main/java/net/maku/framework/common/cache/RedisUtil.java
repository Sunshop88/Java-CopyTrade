package net.maku.framework.common.cache;

import net.maku.framework.common.exception.ServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * redis 工具类
 *
 * @Author Scott
 */
@Component
public class RedisUtil {

    @Autowired
    @Qualifier("redisTemplate1")
    private RedisTemplate<String, Object> redisTemplate;

//    @Autowired
//    @Qualifier("redisTemplate2")
//    private RedisTemplate<String, Object> redisTemplate2;

    /**
     * 指定缓存失效时间
     *
     * @param key  键
     * @param time 时间(秒)
     * @return
     */
    public boolean expire(String key, long time) {
        try {
            if (time > 0) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据key 获取过期时间
     *
     * @param key 键 不能为null
     * @return 时间(秒) 返回0代表为永久有效
     */
    public long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断key是否存在
     *
     * @param key 键
     * @return true 存在 false不存在
     */
    public boolean hasKey(String key) {
        try {
            Boolean hasKey = redisTemplate.hasKey(key);
            if (hasKey == null) {
                hasKey = Boolean.FALSE;
            }
            return hasKey;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除缓存
     *
     * @param key 可以传一个值 或多个
     */
    @SuppressWarnings("unchecked")
    public void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete((Collection<String>) CollectionUtils.arrayToList(key));
            }
        }
    }

    // ============================String=============================

    /**
     * 普通缓存获取
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取指定 key 的值的子字符串,普通缓存获取
     * Redis命令
     * GETRANGE key start end
     *
     * @param key 键
     * @return 值
     */
    public Object getRange(String key, long start, long end) {
        return key == null ? null : redisTemplate.opsForValue().get(key, start, end);
    }

    /**
     * 将给定 key 的值设为 value ，并返回 key 的旧值(old value)。
     * Redis命令
     * GETSET key value
     *
     * @param key   键
     * @param value 新值
     * @return 旧值
     */
    public Object getAndSet(String key, Object value) {
        return key == null ? null : redisTemplate.opsForValue().getAndSet(key, value);
    }

    /**
     * 普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    /**
     * 普通缓存放入并设置时间
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒) time要大于0 如果time小于等于0 将设置无限期
     * @return true成功 false 失败
     */
    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 設置nx值
     **/
    public boolean setnx(String key, Object value, long time) {
        try {
            if (time > 0) {
                return redisTemplate.opsForValue().setIfAbsent(key, value, time, TimeUnit.SECONDS);
            } else {
                return redisTemplate.opsForValue().setIfAbsent(key, value);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 递增
     *
     * @param key   键
     * @param delta 要增加几(大于0)
     * @return 递增后的值
     */
    public long incr(String key, long delta) throws Exception {
        if (delta < 0) {
            throw new ServerException("递增因子必须大于0");
        }
        Long increment;
        try {
            increment = redisTemplate.opsForValue().increment(key, delta);
            if (!ObjectUtils.isEmpty(increment)) {
                return increment;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception("key 对应的值不是数字");
        }

    }

    /**
     * 递减
     *
     * @param key   键
     * @param delta 要减少几(小于0)
     * @return 递减后的值
     */
    public long decr(String key, long delta) throws Exception {
        if (delta < 0) {
            throw new ServerException("递减因子必须大于0");
        }
        Long increment;
        try {
            increment = redisTemplate.opsForValue().increment(key, -delta);
            if (!ObjectUtils.isEmpty(increment)) {
                return increment;
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new Exception("key 对应的值不是数字");
        }
    }

    // ================================Map=================================

    /**
     * HashGet
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return 值
     */
    public Object hGet(String key, String item) {
        return redisTemplate.opsForHash().get(key, item);
    }

    /**
     * HashGet 获获取所有给定字段的值
     * HMGET key field1 [field2]
     *
     * @param key   键 不能为null
     * @param items 项 不能为null
     * @return 值
     */
    public Object hmGet(String key, Collection<Object> items) {
        return redisTemplate.opsForHash().get(key, items);
    }

    /**
     * 获取hashKey对应的所有键值对Map
     * HGETALL key
     *
     * @param key 键
     * @return 对应的多个键值
     */
    public Map<Object, Object> hGetAll(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * HINCRBY key
     * field increment
     */
    public long hIncrBy(String key, String field, Long delta) throws Exception {
        Long increment = redisTemplate.opsForHash().increment(key, field, delta);
        if (ObjectUtils.isEmpty(increment)) {
            return increment;
        } else {
            throw new Exception("");
        }
    }

    /**
     * HINCRBYFLOAT key field increment
     */
    public double hIncrByFloat(String key, String field, Double delta) throws Exception {
        double increment = redisTemplate.opsForHash().increment(key, field, delta);
        if (ObjectUtils.isEmpty(increment)) {
            return increment;
        } else {
            throw new Exception("");
        }
    }

    /**
     * 获取所有哈希表中的字段
     * HKEYS key
     */
    public Set<Object> hKeys(String key) {
        return redisTemplate.opsForHash().keys(key);
    }

    /**
     * 获取哈希表中字段的数量
     * HLEN key
     */
    public long hLen(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    /**
     * HashSet
     *
     * @param key 键
     * @param map 对应多个键值
     * @return true 成功 false 失败
     */
    public boolean hmSet(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HashSet 并设置时间
     *
     * @param key  键
     * @param map  对应多个键值
     * @param time 时间(秒)
     * @return true成功 false失败
     */
    public boolean hmset(String key, Map<String, Object> map, long time) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @return true 成功 false失败
     */
    public boolean hSet(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 向一张hash表中放入数据,如果不存在将创建
     *
     * @param key   键
     * @param item  项
     * @param value 值
     * @param time  时间(秒) 注意:如果已存在的hash表有时间,这里将会替换原有的时间
     * @return true 成功 false失败
     */
    public boolean hset(String key, String item, Object value, long time) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 只有在字段 field 不存在时，设置哈希表字段的值。
     * HSETNX key field value
     *
     * @param key   键
     * @param field 项
     * @param value 值
     * @return 设置结果
     */
    public Boolean hSetNx(String key, String field, Object value) {
        return redisTemplate.opsForHash().putIfAbsent(key, field, value);
    }

    /**
     * 获取哈希表中所有值
     * HVALS key
     *
     * @param key 键
     * @return List<Object>
     */
    public List<Object> hVals(String key) {
        return redisTemplate.opsForHash().values(key);
    }

    /**
     * 删除一个或多个哈希表字段
     * HDEL key field1 [field2]
     *
     * @param key  键 不能为null
     * @param item 项 可以使用多个 不能为null
     * @return Long 删除的个数
     */
    public Long hDel(String key, Object... item) {
        return redisTemplate.opsForHash().delete(key, item);
    }

    /**
     * 判断hash表中是否有该项的值
     *
     * @param key  键 不能为null
     * @param item 项 不能为null
     * @return true 存在 false不存在
     */
    public boolean hHasKey(String key, String item) {
        return redisTemplate.opsForHash().hasKey(key, item);
    }

    /**
     * hash递增 如果不存在,就会创建一个 并把新增后的值返回
     *
     * @param key  键
     * @param item 项
     * @param by   要增加几(大于0)
     * @return
     */
    public double hincr(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, by);
    }

    /**
     * hash递减
     *
     * @param key  键
     * @param item 项
     * @param by   要减少记(小于0)
     * @return
     */
    public double hdecr(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, -by);
    }

    // ============================set=============================

    /**
     * 根据key获取Set中的所有值
     *
     * @param key 键
     * @return
     */
    public Set<Object> sGet(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 根据value从一个set中查询,是否存在
     *
     * @param key   键
     * @param value 值
     * @return true 存在 false不存在
     */
    public boolean sHasKey(String key, Object value) {
        try {
            Boolean member = redisTemplate.opsForSet().isMember(key, value);
            return member != null ? member : false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将数据放入set缓存
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSet(String key, Object... values) {
        try {
            Long add = redisTemplate.opsForSet().add(key, values);
            return add != null ? add : 0L;
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 将set数据放入缓存
     *
     * @param key    键
     * @param time   时间(秒)
     * @param values 值 可以是多个
     * @return 成功个数
     */
    public long sSetAndTime(String key, long time, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0) {
                expire(key, time);
            }
            return count != null ? count : 0L;
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    /**
     * 获取set缓存的长度
     *
     * @param key 键
     * @return
     */
    public long sGetSetSize(String key) {
        try {
            Long size = redisTemplate.opsForSet().size(key);
            return size != null ? size : 0L;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 移除值为value的
     *
     * @param key    键
     * @param values 值 可以是多个
     * @return 移除的个数
     */
    public long setRemove(String key, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().remove(key, values);
            return count != null ? count : 0L;
        } catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }
    // ===============================list=================================

    /**
     * 获取list缓存的内容
     *
     * @param key   键
     * @param start 开始
     * @param end   结束 0 到 -1代表所有值
     * @return
     */
    public List<Object> lGet(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 获取list缓存的长度
     *
     * @param key 键
     * @return
     */
    public long lGetListSize(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 通过索引 获取list中的值
     *
     * @param key   键
     * @param index 索引 index>=0时， 0 表头，1 第二个元素，依次类推；index<0时，-1，表尾，-2倒数第二个元素，依次类推
     * @return
     */
    public Object lGetIndex(String key, long index) {
        try {
            return redisTemplate.opsForList().index(key, index);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public boolean lSet(String key, Object value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return
     */
    public boolean lSet(String key, Object value, long time) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @return
     */
    public boolean lSet(String key, List<Object> value) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 将list放入缓存
     *
     * @param key   键
     * @param value 值
     * @param time  时间(秒)
     * @return
     */
    public boolean lSet(String key, List<Object> value, long time) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            if (time > 0) {
                expire(key, time);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 根据索引修改list中的某条数据
     *
     * @param key   键
     * @param index 索引
     * @param value 值
     * @return
     */
    public boolean lUpdateIndex(String key, long index, Object value) {
        try {
            redisTemplate.opsForList().set(key, index, value);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 移除N个值为value
     *
     * @param key   键
     * @param count 移除多少个
     * @param value 值
     * @return 移除的个数
     */
    public long lRemove(String key, long count, Object value) {
        try {
            Long remove = redisTemplate.opsForList().remove(key, count, value);
            return remove;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 获取指定前缀的一系列key
     * 使用scan命令代替keys, Redis是单线程处理，keys命令在KEY数量较多时，
     * 操作效率极低【时间复杂度为O(N)】，该命令一旦执行会严重阻塞线上其它命令的正常请求
     *
     * @param keyPrefix
     * @return
     */
    private Set<String> keys(String keyPrefix) {
        String realKey = keyPrefix + "*";
        Set<String> binaryKeys = new HashSet<>();

        try {
            // 设置一个合理的 count 值，如 1000，避免每次扫描太多数据
            ScanOptions options = ScanOptions.scanOptions().match(realKey).count(1000).build();

            return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                Cursor<byte[]> cursor = connection.scan(options);
                while (cursor.hasNext()) {
                    // 使用新建的 String 来避免字符编码问题
                    binaryKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
                return binaryKeys;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        return binaryKeys;
    }

    /**
     * 删除指定前缀的一系列key
     *
     * @param keyPrefix
     */
    public void removeAll(String keyPrefix) {
        try {
            Set<String> keys = keys(keyPrefix);
            redisTemplate.delete(keys);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    // ============================sorted set=============================
    /**
     * Redis 有序集合和集合一样也是string类型元素的集合,且不允许重复的成员。
     *
     * 不同的是每个元素都会关联一个double类型的分数。redis正是通过分数来为集合中的成员进行从小到大的排序。
     *
     * 有序集合的成员是唯一的,但分数(score)却可以重复。
     *
     * 集合是通过哈希表实现的，所以添加，删除，查找的复杂度都是O(1)。 集合中最大的成员数为 232 - 1 (4294967295, 每个集合可存储40多亿个成员)。
     */

    /**
     * 向有序集合添加一个或多个成员，或者更新已存在成员的分数
     *
     * @param key
     * @param val
     * @param score
     */
    public void zadd(String key, Object val, double score) {
        this.redisTemplate.opsForZSet().add(key, val, score);
    }

    /**
     * 获取有序集合的成员数
     *
     * @param key redis的主键
     * @return
     */
    private Long zCard(String key) {
        return this.redisTemplate.opsForZSet().zCard(key);
    }

    /**
     * 计算在有序集合中指定区间分数的成员数
     * <p>
     * todo min max 区间的开闭情况？
     *
     * @param key 键
     * @param min 最小值
     * @param max 最大值
     * @return 指定区间的成员数量
     */
    private Long zCount(String key, double min, double max) {
        return this.redisTemplate.opsForZSet().count(key, min, max);
    }

    /**
     * 根据匹配的 key 集合批量获取对应的值
     *
     * @param pattern 模糊匹配的模式
     * @return 匹配的 key-value 映射
     */
    public Map<String, Object> getValuesByPattern(String pattern) {
        Set<String> keys = getKeysByPattern(pattern);
        Map<String, Object> resultMap = new HashMap<>();

        if (!keys.isEmpty()) {
            for (String key : keys) {
                resultMap.put(key, get(key)); // 调用已有的 get 方法
            }
        }

        return resultMap;
    }

    /**
     * 使用 RedisConnection 执行 SCAN 命令实现模糊查询
     *
     * @param pattern 模糊匹配的模式 (如 "prefix*")
     * @return 匹配的 key 集合
     */
    public Set<String> getKeysByPattern(String pattern) {
        try {
            // 获取 Redis 连接
            RedisConnection connection = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();

            Set<String> keys = new HashSet<>();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }

            return keys;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    /**
     * 根据三个条件模糊匹配 Redis 的键并获取其值
     *
     * @param pattern  主模糊匹配模式 (如 "follow:repair:*")
     * @param ip       第一个条件，对应键中 IP 部分 (如 "39.98.109.212")
     * @param part1    第二个条件，对应键中第二个 "#" 后的内容
     * @param part3    第三个条件，对应键中第四个 "#" 后的内容
     * @return Map<String, Map<Object, Object>> 返回符合条件的键和值 (hash 类型)
     */
    public Map<String, Map<Object, Object>> getKeysByThreeConditions(String pattern, String ip, String part1, String part3) {
        try {
            // 获取符合主模式的所有键
            Set<String> keys = getKeysByPattern(pattern);
            Map<String, Map<Object, Object>> resultMap = new HashMap<>();

            for (String key : keys) {
                // 拆分键，检查是否符合三个条件
                String[] parts = key.split("#");
                if (parts.length >= 4) { // 确保键格式正确
                    // 提取主键部分的 IP 部分
                    String[] prefixParts = parts[0].split(":");
                    String extractedIp = prefixParts[prefixParts.length - 1]; // 获取最后一个部分作为 IP

                    // 校验三个条件是否匹配
                    if (extractedIp.equals(ip) && parts[2].equals(part1) && parts[4].equals(part3)) {
                        // 获取键的值，假定为 hash 类型
                        Map<Object, Object> hashValues = hGetAll(key);
                        resultMap.put(key, hashValues);
                    }
                }
            }

            return resultMap;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    /**
     * redis2普通缓存放入
     *
     * @param key   键
     * @param value 值
     * @return true成功 false失败
     */
//    public boolean setSlaveRedis(String key, Object value) {
//        try {
//            redisTemplate2.setValueSerializer(new StringRedisSerializer(StandardCharsets.UTF_8));
//            redisTemplate2.afterPropertiesSet();
//            redisTemplate2.opsForValue().set(key, value);
//            return true;
//        } catch (Exception e) {
//            e.printStackTrace();
//            return false;
//        }
//
//    }

//4	ZINCRBY key increment member
//    有序集合中对指定成员的分数加上增量 increment
//5	ZINTERSTORE destination numkeys key [key ...]
//    计算给定的一个或多个有序集的交集并将结果集存储在新的有序集合 key 中
//6	ZLEXCOUNT key min max
//    在有序集合中计算指定字典区间内成员数量
//7	ZRANGE key start stop [WITHSCORES]
//    通过索引区间返回有序集合指定区间内的成员
//8	ZRANGEBYLEX key min max [LIMIT offset count]
//    通过字典区间返回有序集合的成员
//9	ZRANGEBYSCORE key min max [WITHSCORES] [LIMIT]
//    通过分数返回有序集合指定区间内的成员
//10	ZRANK key member
//            返回有序集合中指定成员的索引
//11	ZREM key member [member ...]
//    移除有序集合中的一个或多个成员
//12	ZREMRANGEBYLEX key min max
//    移除有序集合中给定的字典区间的所有成员
//13	ZREMRANGEBYRANK key start stop
//    移除有序集合中给定的排名区间的所有成员
//14	ZREMRANGEBYSCORE key min max
//    移除有序集合中给定的分数区间的所有成员
//15	ZREVRANGE key start stop [WITHSCORES]
//    返回有序集中指定区间内的成员，通过索引，分数从高到低
//16	ZREVRANGEBYSCORE key max min [WITHSCORES]
//    返回有序集中指定分数区间内的成员，分数从高到低排序
//17	ZREVRANK key member
//    返回有序集合中指定成员的排名，有序集成员按分数值递减(从大到小)排序
//18	ZSCORE key member
//    返回有序集中，成员的分数值
//19	ZUNIONSTORE destination numkeys key [key ...]
//    计算给定的一个或多个有序集的并集，并存储在新的 key 中
//20	ZSCAN key cursor [MATCH pattern] [COUNT count]
//    迭代有序集合中的元素（包括元素成员和元素分值）
}
