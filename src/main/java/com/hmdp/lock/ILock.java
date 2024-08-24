package com.hmdp.lock;

/**
 * @author Qianlk
 */
public interface ILock {

    /**
     * 尝试获取锁
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
