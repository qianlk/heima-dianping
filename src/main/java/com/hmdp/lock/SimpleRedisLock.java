package com.hmdp.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author Qianlk
 */
public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取锁, key为 KEY_PREFIX + name, value为 线程id
        long threadId = Thread.currentThread().getId();
        Boolean scs = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "",
                timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(scs);
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
