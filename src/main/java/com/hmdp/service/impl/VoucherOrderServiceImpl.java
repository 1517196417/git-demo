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
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
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

    @Transactional
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
        if(UserHolder.getUser() == null) {
            return Result.fail("获取不到用户信息");
        }

        //4：扣减库存
        boolean orderSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
            if(!orderSuccess) {
                return Result.fail("删减库存失败");
            }
        //5：创建订单,存入用户userId，优惠券voucherId，订单orderId

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        //返回订单id
        return  Result.ok(orderId);
    }
}
