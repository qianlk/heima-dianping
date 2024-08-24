package com.hmdp.lock;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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

    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final String KEY_PREFIX = "lock:";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("lua/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取锁, key为 KEY_PREFIX + name, value为 线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean scs = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId,
                timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(scs);
    }

    @Override
    public void unlock() {
        // 调用lua脚本,原子性解锁
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }

    /*@Override
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        // 对比redis锁的标识,是否一致
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (threadId.equals(id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
