package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.sql.Time;
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

    private final StringRedisTemplate stringRedisTemplate;

    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Object queryById(Long id) {
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    private static final ExecutorService pool = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //1：从redis缓存取出商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2：redis缓存未命中，直接返回
        if(StrUtil.isBlank(shopJson)) {
            return null;
        }

        //3：redis缓存命中，判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //3.1：未过期直接返回商户信息
        if(expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }
        //3.2：信息过期，准备缓存重建
        String lockKey = LOCK_SHOP_KEY + id;
        if(tryLock(lockKey)) {
            //3.2.1：开启独立线程
            pool.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //3.2.2：没拿到锁，直接返回旧信息
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        try {
            Thread.sleep(200L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    public Shop queryWithMutex(Long id)  {
        String shopKey = CACHE_SHOP_KEY + id;
        //1：从redis缓存取出商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2：redis缓存命中，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //为解决缓存穿透，从redis中获取到”“就直接返回错误信息，防止访问数据库
        if(shopKey != null) {
            return null;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        //3:获取互斥锁 判断是否获取到锁，为获取就递归调用获取锁
        Shop shop = null;
        try {
            if(!tryLock(lockKey)){
                Thread.sleep(50);
                queryWithMutex(id);
            }
            //4：redis缓存未命中，从数据库里查询
            shop = getById(id);
            Thread.sleep(200);
            //5：数据库不存在，直接返回店铺不存在,并在redis缓存中设置空字符串，防止缓存穿透
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6：数据库存在，先加入redis，再返回店铺
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7:释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private Boolean unLock(String key) {
        return stringRedisTemplate.delete(key);
    }

    //封装好解决了缓存穿透的商户查询方法
    public Shop queryWithPassThrough(String id) {
        String shopKey = CACHE_SHOP_KEY + id;
        //1：从redis缓存取出商户信息
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2：redis缓存命中，直接返回
        if(StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //为解决缓存穿透，从redis中获取到”“就直接返回错误信息，防止访问数据库
        if(shopKey != null) {
            return null;
        }
        //3：redis缓存未命中，从数据库里查询
        Shop shop = getById(id);
        //4：数据库不存在，直接返回店铺不存在,并在redis缓存中设置空字符串，防止缓存穿透
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //5：数据库存在，先加入redis，再返回店铺
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        return shop;
    }

@Override
@Transactional
public Result update(Shop shop) {
    Long shopId = shop.getId();
    if (shopId == null) {
        return Result.fail("店铺id不能为空");
    }
    //1：先更新数据库
    updateById(shop);
    //2：再删除缓存
    stringRedisTemplate.delete(CACHE_SHOP_KEY + shopId);
    return Result.ok();
}



}
