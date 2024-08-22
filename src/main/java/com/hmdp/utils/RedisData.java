package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 带过期时间的redis缓存数据封装
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
