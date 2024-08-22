package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 模拟线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        return queryByIdCachePassThrough(id);

        // 缓冲击穿
//        return queryByIdMutex(id);
        return queryByIdLogicalExpire(id);
    }

    /**
     * 缓存穿透代码抽取
     * - 缓存空对象
     */
    public Result queryByIdCachePassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            // 存在,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // #add 判断是否空值,(缓存穿透)
        if ("".equals(shopJson)) {
            return Result.fail("店铺信息不存在!");
        }
        // 不存在,查询数据库,并写入缓存
        Shop shop = getById(id);
        if (shop == null) {
            // #add 空值写入redis,(缓存穿透)
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在!");
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    /**
     * 缓存击穿代码抽取
     * - 互斥锁实现
     */
    public Result queryByIdMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            // 存在,直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // #add 判断是否空值,(缓存穿透)
        if ("".equals(shopJson)) {
            return Result.fail("店铺信息不存在!");
        }

        // 1.互斥实现缓存击穿
        // 1.1 获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 获取互斥锁失败,等待后重试
            if (!isLock) {
                Thread.sleep(50);
                return queryByIdMutex(id);
            }
            // 成功获取
            // fixme 模拟重建时间较长
            Thread.sleep(200);
            shop = getById(id);
            if (shop == null) {
                // 空值写入redis,防止缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("店铺不存在!");
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }
    }

    /**
     * 缓存击穿代码抽取
     * - 逻辑过期方案实现
     */
    public Result queryByIdLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 从redis查询商铺缓存
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(redisDataJson)) {
            return Result.fail("店铺信息不存在!");
        }

        // 1. 命中,解析出逻辑过期时间
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 1.1 未过期,直接返回数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return Result.ok(shop);
        }
        // 1.2 已过期,需要缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 获取锁后,再次检查是否过期
            redisDataJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(redisDataJson)) {
                return Result.fail("店铺信息不存在!");
            }
            // 逻辑过期时间
            redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                return Result.ok(shop);
            }
            // 如果Redis中缓存的店铺信息还是过期,开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 开启独立线程
                try {
                    // 查询数据库中的店铺信息并设置逻辑过期时间封装为RedisData对象存入Redis
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(LOCK_SHOP_KEY + id);
                }
            });
        }
        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);
        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    // 锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // unboxing会出现npe
        return BooleanUtil.isTrue(flag);
    }

    // 解锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 商铺数据缓存预热
    public void saveShop2Redis(Long id, Long expireSeconds) {
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 写入缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
