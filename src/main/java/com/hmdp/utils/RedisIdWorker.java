package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author Qianlk
 */
@Component
public class RedisIdWorker {

    /**
     * 开始的时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1724371200L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        // 无时区偏移的时间戳(距离1970.1.1 0:0:0的描述)和初始时间戳的差值
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1 取当前时间的年月日作为redis存储key的部分
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 redis数值类型自增(increment方法,没有key会新增)
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3. 拼接返回
        // 右移32为再或运算
        return timestamp << COUNT_BITS | count;

    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2024, 8, 23, 0, 0, 0);
        long epochSecond = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("epochSecond = " + epochSecond);

    }
}
