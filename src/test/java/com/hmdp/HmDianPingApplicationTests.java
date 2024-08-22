package com.hmdp;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Test
    void testSaveShop() {
        shopService.saveShop2Redis(1L, 10L);
    }

    @Test
    void testTime() {
        LocalDateTime localDateTime = LocalDateTimeUtil.of(1724320324411L);
        System.out.println(localDateTime.isAfter(LocalDateTime.now()));
    }
}
