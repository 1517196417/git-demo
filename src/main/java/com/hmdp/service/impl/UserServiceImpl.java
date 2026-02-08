package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1:检验手机号是否正确：不正确则返回报错信息
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        //2:手机号格式正确就发送验证码：调用第三方工具类
        String code = RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码(log日志模拟发送)
        log.debug("发送短信验证码成功。 验证码: " + code);
        return Result.ok();
    }

    @Override
    public Result loginByForm(LoginFormDTO loginFormDTO, HttpSession session) {
        // 1：校验手机号格式
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // 2：校验验证码
        String code = loginFormDTO.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 3. 验证码校验通过，删除Redis中的验证码（防止重复使用）
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 4：查询/注册用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithLogin(phone);
        }

        // 5：生成token作为登录标识
        String token = UUID.randomUUID().toString();
        String tokenKey = LOGIN_USER_KEY + token;

        try {
            // 6：对象转换，安全转换为Map，修复空指针问题
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            UserHolder.saveUser(userDTO);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            // 字段值处理：空值设为空字符串，非空值转String
                            .setFieldValueEditor((fieldName, fieldValue) -> {
                                if (fieldValue == null) {
                                    return "";
                                }
                                return fieldValue.toString();
                            })
            );

            // 7：存储用户信息到Redis Hash结构
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            // 修复：使用正确的key设置过期时间
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

            // 8：返回token给前端
            return Result.ok(token);
        } catch (Exception e) {
            log.error("用户登录存储Redis异常", e);
            return Result.fail("登录失败，请稍后重试");
        }

    }

    private User createUserWithLogin(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);

        return user;
    }

}
