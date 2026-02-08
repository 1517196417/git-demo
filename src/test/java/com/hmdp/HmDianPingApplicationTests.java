package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
        CountDownLatch countDownLatch = new CountDownLatch(300);



        long start = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            long id = redisIdWorker.nextId("order");
            System.out.println("id = " + id);
            countDownLatch.countDown();
        }
        long end = System.currentTimeMillis();
        System.out.println("总耗时： " + (end - start));
    }

    @Test
    void test1() {
        stringRedisTemplate.opsForValue().set("order:1", "1");
        stringRedisTemplate.opsForValue().set("order:2", "2");
        stringRedisTemplate.opsForValue().set("order:3", "3");
        stringRedisTemplate.opsForValue().set("order:4", "4");
        String s = stringRedisTemplate.opsForValue().get("order:1");
        System.out.println(s);
    }
}
