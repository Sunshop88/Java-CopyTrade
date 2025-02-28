package net.maku.subcontrol.trader;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.util.AesUtils;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.pojo.CachedCopierOrderInfo;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.task.UpdateTraderInfoTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Shaozz
 * @since 2021/7/6 10:35
 */
@Component
@Order(1)
@Slf4j
public class InitRunner implements ApplicationRunner {


    @Autowired
    private LeaderApiTradersAdmin leaderApiTradersAdmin;

    @Autowired
    private CopierApiTradersAdmin copierApiTradersAdmin;

    @Autowired
    private FollowTraderService aotfxTraderService;

    @Autowired
    FollowTraderSubscribeService masterSlaveService;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private FollowService followService;
    @Autowired
    private FollowTraderService followTraderService;
    @Autowired
    private FollowVarietyService followVarietyService;
    @Autowired
    private FollowPlatformService followPlatformService;
    @Autowired
    private FollowTraderSubscribeService followTraderSubscribeService;
    @Autowired
    private FollowSysmbolSpecificationService followSysmbolSpecificationService;
    @Autowired
    private FollowSubscribeOrderService followSubscribeOrderService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private RedisUtil redisUtil;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("=============启动时加载示例内容开始=============");
        log.info("全局加密=======开始");
        setPassword();
        log.info("全局加密=======结束");
        log.info("加载缓存=======开始");
        getCache();
        log.info("加载缓存=======结束");
        // 连接MT4交易账户
        mt4TraderStartup();
        log.info("=============启动时加载示例内容完毕=============");
    }

    private void setPassword() {
        List<FollowTraderEntity> mt4TraderList = aotfxTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery()
                .eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST)
                .eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue())
                .orderByAsc(FollowTraderEntity::getCreateTime));
        mt4TraderList.forEach(o->{
            if (o.getPassword().length()!=32){
                //加密处理
                o.setPassword(AesUtils.aesEncryptStr(o.getPassword()));
            }
            followTraderService.update(FollowTraderConvert.INSTANCE.convert(o));
        });
    }


    private void mt4TraderStartup() throws Exception {
        log.info("当前ip"+FollowConstant.LOCAL_HOST);
        //删除所有进行中的下单和平仓redis
        redisCache.deleteByPattern(Constant.TRADER_SEND);
        redisCache.deleteByPattern(Constant.TRADER_CLOSE);
        // 2.启动喊单者和跟单者
        // mt4账户 喊单
        List<FollowTraderEntity> mt4TraderList = aotfxTraderService.list(Wrappers.<FollowTraderEntity>lambdaQuery()
                .eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST)
                .eq(FollowTraderEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue())
                .orderByAsc(FollowTraderEntity::getCreateTime));
        if (ObjectUtil.isNotEmpty(mt4TraderList)){
            //默认异常
            followTraderService.update(new LambdaUpdateWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId,mt4TraderList.stream().map(FollowTraderEntity::getId).toList()).set(FollowTraderEntity::getStatus,CloseOrOpenEnum.OPEN.getValue()).set(FollowTraderEntity::getStatusExtra,"账号异常"));
        }
        // 分类MASTER和SLAVE
        long masters = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        log.info("===============喊单者{}", masters);
        leaderApiTradersAdmin.startUp();
        // mt4账户 跟单
        long slave = mt4TraderList.stream().filter(o->o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).count();
        log.info("===============跟单者{}", slave);
        copierApiTradersAdmin.startUp();

    }


    private void getCache() {
        //品种匹配缓存
        followVarietyService.getListByTemplate().forEach(o->{
            followVarietyService.getListByTemplated(o.getTemplateId());
        });

        List<FollowTraderEntity> list = followTraderService.list();
        list.forEach(o->{
            ThreadPoolUtils.getExecutor().execute(()->{
                //券商缓存
                followPlatformService.getPlatFormById(o.getPlatformId().toString());
                //账户信息缓存
                followTraderService.getFollowById(o.getId());
                //品种规格缓存
                followSysmbolSpecificationService.getByTraderId(o.getId());
            });
        });

        //订单关系缓存
        List<FollowTraderSubscribeEntity> followTraderSubscribeEntityList = followTraderSubscribeService.list();
        followTraderSubscribeEntityList.forEach(o->{
            followTraderSubscribeService.subscription(o.getSlaveId(),o.getMasterId());
            FollowTraderEntity slave = followTraderService.getFollowById(o.getSlaveId());
            FollowTraderEntity master = followTraderService.getFollowById(o.getMasterId());
            // 将跟单关系存储到Redis
            String key = Constant.FOLLOW_RELATION_KEY + o.getMasterAccount()+"#"+master.getPlatformId();
            String followerId =o.getSlaveAccount()+"#"+slave.getPlatformId();;
            redisUtil.sSet(key, followerId);
        });

        //喊单所有跟单缓存
        Set<Long> collect = followTraderSubscribeEntityList.stream()
                .map(FollowTraderSubscribeEntity::getMasterId) // 获取每个实体的 masterId
                .filter(Objects::nonNull)          // 过滤掉可能为 null 的值
                .collect(Collectors.toSet());
        collect.stream().toList().forEach(o->{
            ThreadPoolUtils.getExecutor().execute(()->{
                followTraderSubscribeService.getSubscribeOrder(o);
            });
        });

    }
}
