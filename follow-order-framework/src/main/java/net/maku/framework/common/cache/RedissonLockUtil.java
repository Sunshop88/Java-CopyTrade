package net.maku.framework.common.cache;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLockUtil {

    private final RedissonClient redissonClient;

    @Autowired
    public RedissonLockUtil(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取分布式锁
     *
     * @param lockKey 锁的key
     * @return 锁对象
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 加锁，默认超时自动解锁
     *
     * @param lockKey 锁的key
     * @param leaseTime 锁持有时间，超时后自动释放锁
     * @param unit 时间单位
     * @return 是否成功获取锁
     */
    public boolean lock(String lockKey, long leaseTime, TimeUnit unit) {
        RLock lock = getLock(lockKey);
        try {
            return lock.tryLock(0, leaseTime, unit);  // 立即获取锁，如果失败则返回false
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 加锁，带等待时间和超时自动解锁
     *
     * @param lockKey 锁的key
     * @param waitTime 等待时间，超时则获取锁失败
     * @param leaseTime 锁持有时间，超时后自动释放锁
     * @param unit 时间单位
     * @return 是否成功获取锁
     */
    public boolean lock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 解锁
     *
     * @param lockKey 锁的key
     */
    public void unlock(String lockKey) {
        RLock lock = getLock(lockKey);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 判断锁是否被当前线程持有
     *
     * @param lockKey 锁的key
     * @return 当前线程是否持有锁
     */
    public boolean isLockedByCurrentThread(String lockKey) {
        RLock lock = getLock(lockKey);
        return lock.isHeldByCurrentThread();
    }

    public boolean tryLockForShortTime(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = getLock(lockKey);
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
