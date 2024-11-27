package net.maku.subcontrol.trader;

import lombok.Data;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.impl.FollowPlatformServiceImpl;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.utils.ThreadPoolUtils;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.List;
import java.util.concurrent.*;

/**
 * mtapi MT4管理基础类
 */
@Data
public abstract class AbstractApiTradersAdmin {
    protected ConcurrentHashMap<String, LeaderApiTrader> leader4ApiTraderConcurrentHashMap;
    protected ConcurrentHashMap<String, CopierApiTrader> copier4ApiTraderConcurrentHashMap;

    protected FollowBrokeServerService followBrokeServerService;
    protected FollowTraderService followTraderService;
    protected FollowTraderSubscribeService followTraderSubscribeService;
    protected ThreadPoolExecutor scheduledExecutorService;
    protected FollowPlatformService followPlatformService;
    protected RedisUtil redisUtil;


    public AbstractApiTradersAdmin() {
        this.leader4ApiTraderConcurrentHashMap = new ConcurrentHashMap<>();
        scheduledExecutorService= ThreadPoolUtils.getScheduledExecute();
        this.copier4ApiTraderConcurrentHashMap = new ConcurrentHashMap<>();
        this.followPlatformService= SpringContextUtils.getBean(FollowPlatformServiceImpl.class);
    }

    protected int traderCount = 0;

    /**
     * 启动
     *
     * @throws Exception 异常
     */
    public abstract void startUp() throws Exception;

    /**
     * 启动部分账号
     *
     * @throws Exception 异常
     */
    public abstract void startUp(List<FollowTraderEntity> list) throws Exception;

    /**
     * 删除账号
     *
     * @param id mt跟单账户的id
     * @return 删除成功-true 失败-false
     */
    public abstract boolean removeTrader(String id);

    /**
     * 绑定账号
     *
     * @param trader        账号信息
     * @return ConCodeEnum 添加结果
     */
    public abstract ConCodeEnum addTrader(FollowTraderEntity trader);

}
