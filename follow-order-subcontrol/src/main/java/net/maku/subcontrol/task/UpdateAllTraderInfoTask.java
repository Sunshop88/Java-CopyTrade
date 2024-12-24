package net.maku.subcontrol.task;


import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.impl.FollowBrokeServerServiceImpl;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.followcom.service.impl.FollowTraderServiceImpl;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;

import java.io.IOException;
import java.util.List;

/**
 * @author samson bruce
 */
@Slf4j
public class UpdateAllTraderInfoTask implements Runnable {
    private final FollowTraderService traderService;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final RedisCache redisCache;
    private final FollowPlatformService followPlatformService;
    private final FollowBrokeServerService followBrokeServerService;
    private final Integer flag;
    public UpdateAllTraderInfoTask(Integer flag) {
        this.traderService = SpringContextUtils.getBean(FollowTraderServiceImpl.class);
        this.leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
        this.copierApiTradersAdmin = SpringContextUtils.getBean(CopierApiTradersAdmin.class);
        this.redisCache = SpringContextUtils.getBean(RedisCache.class);
        this.followPlatformService = SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
        this.followBrokeServerService = SpringContextUtils.getBean(FollowBrokeServerServiceImpl.class);
        this.flag=flag;
    }

    @Override
    public void run() {
        try {
            log.info("开始更新交易者信息...{}",flag);
            List<FollowTraderEntity> followTraderEntityList = traderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST).eq(FollowTraderEntity::getType, flag));
            followTraderEntityList.forEach(trader -> ThreadPoolUtils.getExecutor().execute(() -> processTrader(trader,flag)));
        } catch (Exception e) {
            log.error("更新交易者信息时出现异常：", e);
        }
    }

    private void processTrader(FollowTraderEntity trader,Integer flag) {
        QuoteClient quoteClient = null;
        AbstractApiTrader abstractApiTrader=null;
        try {
            if (flag.equals(TraderTypeEnum.MASTER_REAL.getType())){
                abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(trader.getId().toString());
            }else {
                abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(trader.getId().toString());
            }
            if (ObjectUtil.isNotEmpty(abstractApiTrader)) {
                quoteClient = abstractApiTrader.quoteClient;
                if (!quoteClient.Connected()) {
                    log.info("交易者 {} 掉线，开始重连...", trader.getAccount());
                    reconnect(trader, quoteClient,abstractApiTrader);
                }
            } else {
                log.info("交易者 {} 不存在，开始连接...", trader.getAccount());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(trader);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    LeaderApiTrader newTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(trader.getId().toString());
                    quoteClient = newTrader.quoteClient;
                    newTrader.startTrade();
                }
            }
            freshTimeWhenConnected(quoteClient, trader.getId());
        } catch (Exception e) {
            handleReconnectException(trader, e,abstractApiTrader);
        }
    }

    private void handleReconnectException(FollowTraderEntity trader, Exception e,AbstractApiTrader abstractApiTrader) {
        log.error("[MT4{}:{}-{}-{}-{}-{}] 需要重连的异常：{}",
                trader.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) ? "喊单者" : "跟单者",
                trader.getId(), trader.getAccount(), trader.getServerName(), trader.getPlatform(), trader.getPassword(), e.getMessage());

        traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate()
                .set(FollowTraderEntity::getStatus, CloseOrOpenEnum.OPEN.getValue())
                .set(FollowTraderEntity::getStatusExtra, "账号掉线")
                .eq(FollowTraderEntity::getId, trader.getId()));

        String serverNode = getServerNode(trader);
        if (ObjectUtil.isNotEmpty(serverNode)) {
            reconnectWithNewNode(trader, serverNode,abstractApiTrader);
        } else {
            reconnectWithBackupNodes(trader,abstractApiTrader);
        }
    }

    private String getServerNode(FollowTraderEntity trader) {
        String serverNode = (String) redisCache.hGet(Constant.VPS_NODE_SPEED + trader.getServerId(), trader.getPlatform());
        if (ObjectUtil.isEmpty(serverNode)) {
            FollowPlatformEntity platformEntity = followPlatformService.getOne(
                    new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, trader.getPlatform()));
            if (platformEntity != null) {
                serverNode = platformEntity.getServerNode();
            }
        }
        return serverNode;
    }

    private void reconnectWithNewNode(FollowTraderEntity trader, String serverNode,AbstractApiTrader abstractApiTrader) {
        try {
            String[] split = serverNode.split(":");
            QuoteClient quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(), split[0], Integer.parseInt(split[1]));
            reconnect(trader, quoteClient,abstractApiTrader);
        } catch (Exception e) {
            if (e.getMessage().contains("Invalid account")) {
                log.info("账号或密码错误，无法重连：{}", trader.getId());
                throw new ServerException("账号或密码错误");
            } else {
                log.info("重连失败：{}", trader.getId());
            }
        }
    }

    private void reconnectWithBackupNodes(FollowTraderEntity trader,AbstractApiTrader abstractApiTrader) {
        List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(trader.getPlatform());
        if (ObjectUtil.isEmpty(serverEntityList)) {
            log.error("没有可用的服务器节点：{}", trader.getPlatform());
            return;
        }

        for (FollowBrokeServerEntity server : serverEntityList) {
            QuoteClient quoteClient = null;
            try {
                quoteClient = new QuoteClient(Integer.valueOf(trader.getAccount()), trader.getPassword(), server.getServerNode(), Integer.parseInt(server.getServerPort()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (reconnect(trader, quoteClient,abstractApiTrader)) {
                return; // 如果重连成功，直接返回
            }
        }
        log.error("所有备选节点重连失败：{}", trader.getId());
    }

    private void freshTimeWhenConnected(QuoteClient quoteClient, Long traderId) throws ConnectException, TimeoutException {
        LambdaUpdateWrapper<FollowTraderEntity> updateWrapper = Wrappers.<FollowTraderEntity>lambdaUpdate()
                .set(FollowTraderEntity::getLeverage, quoteClient.AccountLeverage())
                .set(FollowTraderEntity::getBalance, quoteClient.AccountBalance())
                .set(FollowTraderEntity::getIsDemo, quoteClient.IsDemoAccount())
                .set(FollowTraderEntity::getEuqit, quoteClient.AccountEquity())
                .set(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue())
                .set(FollowTraderEntity::getStatusExtra, "账号在线")
                .eq(FollowTraderEntity::getId, traderId);

        try {
            updateWrapper.set(FollowTraderEntity::getDiff, quoteClient.ServerTimeZone() / 60);
        } catch (Exception e) {
            log.error("读取服务器时区失败：", e);
        } finally {
            traderService.update(updateWrapper);
        }
    }

    /**
     * @return true - 重连成功，false - 重连失败
     */
    public boolean reconnect(FollowTraderEntity trader, QuoteClient quoteClient,AbstractApiTrader abstractApiTrader) {
        try {
            log.info("MT4账号{}：尝试重连...", trader.getId());
            traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate().set(FollowTraderEntity::getStatus, CloseOrOpenEnum.OPEN.getValue()).set(FollowTraderEntity::getStatusExtra, "账号掉线").eq(FollowTraderEntity::getId, trader.getId()));
            abstractApiTrader.connect2Broker();
            log.info("[MT4账号：{}] 重连成功", trader.getId());
            traderService.update(Wrappers.<FollowTraderEntity>lambdaUpdate()
                    .set(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue())
                    .set(FollowTraderEntity::getStatusExtra, "账号在线")
                    .eq(FollowTraderEntity::getId, trader.getId()));
            return true;
        } catch (Exception e) {
            log.warn("[MT4账号：{}] 重连失败：{}", trader.getId(), e.getMessage());
        }
        return false;
    }
}
