package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StopWatch;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Qianlk
 */
@SpringBootTest
class RedisIdWorkerTest {

    @Autowired
    RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        // 每个任务生成100个id
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id " + id);
            }
            countDownLatch.countDown();
        };

        StopWatch sw = new StopWatch();
        sw.start();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        countDownLatch.await();
        sw.stop();
        sw.getTotalTimeSeconds();
        sw.prettyPrint();
    }
}