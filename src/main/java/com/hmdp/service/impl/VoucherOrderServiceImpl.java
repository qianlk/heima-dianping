package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.lock.SimpleRedisLock;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 判断秒杀是否开始或者是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 未开始
            return Result.fail("秒杀尚未开始!");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 已经结束
            return Result.fail("秒杀已经结束!");
        }
        // 判断秒杀库存
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }

        //获取锁后创建订单
        Long userId = UserHolder.getUser().getId();
        // 使用分布式锁
        // 1. 自定义分布式锁
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200);
        // 2. redisson
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            return Result.fail("不允许重复下单!");
        }

        try {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }


        /*synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*/
    }

    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        // 5 一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次!");
        }

        /*// 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("库存不足!");
        }*/
        // 扣减库存(乐观锁 cas)
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId).eq("stock", voucher.getStock()) // where id = ? and stock = ?
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            return Result.fail("库存不足!");
        }

        // 创建秒杀订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 创建单号
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
