package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
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
    private ResourceLoader resourceLoader;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private IVoucherOrderService voucherOrderService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result secKillOrder(Long voucherId) {
        //1：查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2：判断秒杀是否开始， 未开始返回报错
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀活动尚未开始!");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动已结束!");
        }
        //3：判断优惠券库存是否充足， 不充足返回报错
            if(voucher.getStock() < 1) {
                return Result.fail("优惠券数量不足!");
            }

        Long userId = UserHolder.getUser().getId();
        //获取锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean success = lock.tryLock(1200L, stringRedisTemplate);
        if(!success) {
            return Result.fail("获取分布式锁失败, 不允许重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //4：扣减库存
        boolean orderSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if(!orderSuccess) {
            return Result.fail("删减库存失败");
        }
        //5：创建订单,存入用户userId，优惠券voucherId，订单orderId

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        if(UserHolder.getUser().getId() == null) {
            voucherOrder.setUserId(985L);
            save(voucherOrder);
            return Result.fail("获取不到用户信息");
        } else {
            voucherOrder.setUserId(UserHolder.getUser().getId());
        }
        save(voucherOrder);
        //返回订单id
        return  Result.ok(orderId);
    }
}
