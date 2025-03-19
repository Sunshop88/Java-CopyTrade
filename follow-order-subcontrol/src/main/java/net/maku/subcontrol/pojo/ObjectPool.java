package net.maku.subcontrol.pojo;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectPool<T> {
    private final ConcurrentLinkedQueue<T> pool;
    private final ObjectFactory<T> factory;

    public ObjectPool(ObjectFactory<T> factory) {
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
    }

    public T borrowObject() {
        T obj = pool.poll();
        return (obj == null) ? factory.create() : obj;
    }

    public void returnObject(T obj) {
        pool.offer(obj);
    }

    public interface ObjectFactory<T> {
        T create();
    }
}