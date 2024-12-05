package net.maku.subcontrol.rule;


import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.FollowRemainderEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowSysmbolSpecificationService;
import net.maku.followcom.service.FollowVarietyService;
import net.maku.followcom.util.SpringContextUtils;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.subcontrol.pojo.EaSymbolInfo;
import net.maku.subcontrol.trader.AbstractApiTrader;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author X.T. LI
 */
@Slf4j
public abstract class AbstractFollowRule {
    protected FixedLot fixedLot = new FixedLot();
    protected FixedRatio fixedRatio = new FixedRatio();
    protected FixedEuqit fixedEuqit = new FixedEuqit();

    /**
     * 链中下一元素
     */
    protected AbstractFollowRule nextRule = null;
    protected LeaderApiTradersAdmin leaderApiTradersAdmin = SpringContextUtils.getBean(LeaderApiTradersAdmin.class);
    protected  RedisCache redisCache= SpringContextUtils.getBean(RedisCache.class);
    protected  FollowSysmbolSpecificationService followSysmbolSpecificationService= SpringContextUtils.getBean(FollowSysmbolSpecificationService.class);
    protected FollowVarietyService followVarietyService= SpringContextUtils.getBean(FollowVarietyService.class);
    public void setNextRule(AbstractFollowRule nextRule) {
        this.nextRule = nextRule;
    }

    public PermitInfo rule(FollowTraderSubscribeEntity eaLeaderCopier, EaOrderInfo eaOrderInfo, AbstractApiTrader copierApiTrader) {
        PermitInfo permitInfo = permit(eaLeaderCopier, eaOrderInfo, copierApiTrader);
        if (permitInfo.getPermitted()) {
            //允许跟随
            if (nextRule != null) {
                //需要进一步判断
                return nextRule.permit(eaLeaderCopier, eaOrderInfo, copierApiTrader);
            } else {
                return permitInfo;
            }
        } else {
            return permitInfo;
        }
    }

    /**
     * 计算跟单者跟随的时候，根据当前订单跟随规则，如果是强制开仓，计算完成后不足最小手数，以最小手数开仓。
     *
     * @param masterSlave     订阅关系
     * @param eaOrderInfo     开仓订单信息
     * @param copierApiTrader 跟单者
     * @return double 跟单者开仓手数
     */
    public double lots(FollowTraderSubscribeEntity masterSlave, EaOrderInfo eaOrderInfo, AbstractApiTrader copierApiTrader) throws InvalidSymbolException, ConnectException, TimeoutException {
        double lots = 0;
        if (ObjectUtils.isEmpty(masterSlave)) {
            return 0.0;
        }
        //喊单者、跟单者的合约大小
        EaSymbolInfo copierSymbolInfo = copierApiTrader.symbolInfo(eaOrderInfo.getSymbol(), true);
        EaSymbolInfo leaderSymbolInfo = null;
        //MT4平台
        LeaderApiTrader LeaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(eaOrderInfo.getMasterId().toString());
        leaderSymbolInfo = LeaderApiTrader.symbolInfo(eaOrderInfo.getOriSymbol(), true);


        log.debug("leaderSymbolInfo {}", leaderSymbolInfo);
        log.debug("copierSymbolInfo {}", copierSymbolInfo);
        double pr = getPr(copierApiTrader, copierApiTrader.getTrader().getId(), eaOrderInfo.getOriSymbol());
        switch (masterSlave.getFollowMode()) {
            case 0:
                //按固定手数
                lots = fixedLot.lots(masterSlave, eaOrderInfo, 0.0, null);
                break;
            case 1:
                //按比例跟单
                lots = fixedRatio.lots(masterSlave, eaOrderInfo, 0.0, null,pr);
                break;
            case 2:
                log.info("下单参数 {}-{}-{}-{}-{}", masterSlave.getFollowParam(),eaOrderInfo.getLots(),LeaderApiTrader.quoteClient.Equity, copierApiTrader.quoteClient.Equity,pr);
              //  0.30-0.5-499297.68-499921.94999999995-3.0

                //按净值比例
                lots = fixedEuqit.lots(eaOrderInfo, LeaderApiTrader.quoteClient.Equity, copierApiTrader.quoteClient.Equity,masterSlave,pr);

                break;
            case 3:
                //按资金比例(余额
                lots = fixedEuqit.lots(eaOrderInfo, LeaderApiTrader.quoteClient.Balance, copierApiTrader.quoteClient.Balance);
            default:
                lots = 0.0;
                break;
        }
        BigDecimal lots2digits;
        if (masterSlave.getRemainder().equals(FollowRemainderEnum.ROUND_IT_UP.getValue())){
            lots2digits = BigDecimal.valueOf(lots).setScale(2, RoundingMode.HALF_UP);
        } else {
            lots2digits = BigDecimal.valueOf(lots).setScale(2, RoundingMode.DOWN);
        }
        return lots2digits.doubleValue();
    }

    /***
     * 获取合约比例
     * */
    public double getPr(AbstractApiTrader copierApiTrader,long traderId,String symbol){
        double pr = 1;
        // 查看品种匹配 模板
        List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(copierApiTrader.getTrader().getTemplateId());
        Integer contract = followVarietyEntityList.stream().filter(o -> ObjectUtil.isNotEmpty(o.getStdSymbol()) && o.getStdSymbol().equals(symbol)).findFirst()
                .map(FollowVarietyEntity::getStdContract)
                .orElse(0);
        log.info("跟单账号标准合约大小{}", contract);
        if (contract != 0) {
            //查询合约手数比例
            Map<String, FollowSysmbolSpecificationEntity> symbolSpecification = getSymbolSpecification(traderId);
            FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = symbolSpecification.get(symbol);
            if (ObjectUtil.isNotEmpty(followSysmbolSpecificationEntity)) {
                log.info("对应合约值{}", followSysmbolSpecificationEntity.getContractSize());
                pr = (double) contract / followSysmbolSpecificationEntity.getContractSize();
            }
        }
        return pr;
    }

    private Map<String, FollowSysmbolSpecificationEntity> getSymbolSpecification(long traderId) {
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))) {
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>) redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        } else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION + traderId, followSysmbolSpecificationEntityList);
        }
        return followSysmbolSpecificationEntityList.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i));
    }


    /**
     * 判断
     *
     * @param leaderCopier    跟随关系
     * @param orderInfo       交易信号
     * @param copierApiTrader 跟单者
     * @return PermitInfo
     */
    protected abstract PermitInfo permit(FollowTraderSubscribeEntity leaderCopier, EaOrderInfo orderInfo, AbstractApiTrader copierApiTrader);


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PermitInfo {
        private Boolean permitted;
        private int permit;
        private String extra;
        private double lots;
    }
}

