package com.jxph.cloud.service.auth.server.service.impl;

import com.jxph.cloud.common.exception.UserInvalidException;
import com.jxph.cloud.common.utils.JSONUtils;
import com.jxph.cloud.service.auth.api.pojo.SysUser;
import com.jxph.cloud.service.auth.api.pojo.SysUserExample;
import com.jxph.cloud.service.auth.server.common.constant.RedisConstant;
import com.jxph.cloud.service.auth.api.common.enums.SysUserStatusEnums;
import com.jxph.cloud.service.auth.server.common.utils.MD5Helper;
import com.jxph.cloud.service.auth.server.dao.SysUserMapper;
import com.jxph.cloud.service.auth.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author 谢秋豪
 * @date 2018/9/3 22:04
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Long addUser(SysUser sysUser) throws UserInvalidException {
        SysUserExample sysUserExample = new SysUserExample();
        SysUserExample.Criteria criteria = sysUserExample.createCriteria();
        criteria.andUsernameEqualTo(sysUser.getUsername());
        List<SysUser> sysUsers = sysUserMapper.selectByExample(sysUserExample);
        if (!sysUsers.isEmpty()) {
            throw new UserInvalidException("该用户名已存在");
        }
        String uuid = MD5Helper.getUUID();
        sysUser.setSalt(uuid);
        sysUser.setStatus(SysUserStatusEnums.NORMAL.getStatus());
        sysUser.setCreateTime(new Date());
        String password = MD5Helper.encryPassword(sysUser.getPassword(), uuid);
        sysUser.setPassword(password);
        sysUserMapper.insertSelective(sysUser);
        return sysUser.getUserId();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public SysUser getUserByUserId(Long userId) {
        Object value = redisTemplate.opsForValue().get(RedisConstant.REIDS_USER_PREFIX + userId.toString());
        if (value != null) {
            redisTemplate.expire(RedisConstant.REIDS_USER_PREFIX + userId.toString(), RedisConstant.REDIS_USER_EXPIRE, TimeUnit.SECONDS);
            return JSONUtils.parse(value.toString(), SysUser.class);
        }
        SysUser user = sysUserMapper.selectByPrimaryKey(userId);
        setRedisForObject(user);
        return user;
    }


    private void setRedisForObject(SysUser user){
        if (user == null) {
            redisTemplate.opsForValue().set(RedisConstant.REIDS_USER_PREFIX + user.getUserId(), null);
            redisTemplate.expire(RedisConstant.REIDS_USER_PREFIX + user.getUserId(), RedisConstant.REDIS_USER_NULL_EXPIRE, TimeUnit.SECONDS);
        } else {
            user.setPassword(null);
            redisTemplate.opsForValue().set(RedisConstant.REIDS_USER_PREFIX + user.getUserId(), JSONUtils.toJsonString(user));
            redisTemplate.expire(RedisConstant.REIDS_USER_PREFIX + user.getUserId(), RedisConstant.REDIS_USER_EXPIRE, TimeUnit.SECONDS);
        }
    }
}
