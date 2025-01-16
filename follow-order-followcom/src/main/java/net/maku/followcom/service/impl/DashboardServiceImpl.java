package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderAnalysisConvert;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.FollowTraderAnalysisQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.query.Query;
import net.maku.framework.common.utils.PageResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Author:  zsd
 * Date:  2025/1/2/周四 10:30
 * 仪表盘
 */
@Service
@AllArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final FollowTraderService followTraderService;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final RedisCache redisCache;
    private  final FollowTraderAnalysisService followTraderAnalysisService;
    private  final FollowPlatformService followPlatformService;
    /**
     * 仪表盘-账号数据
     * */
    @Override
    public List<DashboardAccountDataVO> getAccountDataPage(DashboardAccountQuery vo) {

        return  followTraderAnalysisService.getAccountDataPage(vo);

    }

    @Override
    public List<SymbolChartVO> getSymbolAnalysis() {

      //  return followTraderAnalysisService.getSymbolAnalysis(vo);
        return getSymbolChart();
    }

    @Override
    public List<FollowTraderAnalysisEntity> getSymbolAnalysisDetails(TraderAnalysisVO vo) {
       LambdaQueryWrapper<FollowTraderAnalysisEntity> wrapper = new LambdaQueryWrapper<>();
        if(ObjectUtil.isNotEmpty(vo.getSymbol())){
            List<String> symbols = JSONArray.parseArray(vo.getSymbol(), String.class);
            wrapper.in(ObjectUtil.isNotEmpty(symbols), FollowTraderAnalysisEntity::getSymbol, symbols);
        }
        if(ObjectUtil.isNotEmpty(vo.getVpsId())){
            List<Integer> vpsIds = JSONArray.parseArray(vo.getVpsId(), Integer.class);
            wrapper.in(ObjectUtil.isNotEmpty(vpsIds), FollowTraderAnalysisEntity::getVpsId, vpsIds);
        }
        if(ObjectUtil.isNotEmpty(vo.getSourcePlatform())){
            List<String> sourcePlatforms = JSONArray.parseArray(vo.getSourcePlatform(), String.class);
            wrapper.in(ObjectUtil.isNotEmpty(sourcePlatforms), FollowTraderAnalysisEntity::getSourcePlatform, sourcePlatforms);
        }
        if(ObjectUtil.isNotEmpty(vo.getPlatform())){
            List<String> platforms = JSONArray.parseArray(vo.getPlatform(), String.class);
            wrapper.in(ObjectUtil.isNotEmpty(platforms), FollowTraderAnalysisEntity::getPlatform, platforms);
        }
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getAccount()), FollowTraderAnalysisEntity::getAccount, vo.getAccount());

        wrapper.eq(ObjectUtil.isNotEmpty(vo.getSourceAccount()), FollowTraderAnalysisEntity::getSourceAccount, vo.getSourceAccount());

        wrapper.eq(ObjectUtil.isNotEmpty(vo.getType()), FollowTraderAnalysisEntity::getType, vo.getType());
        if(ObjectUtil.isNotEmpty(vo.getOrder())){
            if(vo.getOrder().equals("position")){
                wrapper.orderBy(true,vo.getAsc(),FollowTraderAnalysisEntity::getPosition);
            }
            if(vo.getOrder().equals("lots")){
                wrapper.orderBy(true,vo.getAsc(),FollowTraderAnalysisEntity::getLots);
            }
            if(vo.getOrder().equals("num")){
                wrapper.orderBy(true,vo.getAsc(),FollowTraderAnalysisEntity::getNum);
            }
            if(vo.getOrder().equals("profit")){
                wrapper.orderBy(true,vo.getAsc(),FollowTraderAnalysisEntity::getProfit);
            }

        }
        return    followTraderAnalysisService.list(wrapper);

    }


    @Override
    public Map<String,List<FollowTraderAnalysisEntity>> getSymbolAnalysisMapDetails() {
        Map<Object, Object> stringObjectMap = redisCache.hGetStrAll(Constant.STATISTICS_SYMBOL_INFO);
        Map<String, List<FollowTraderAnalysisEntity>> map = new HashMap<>();
        //订阅关系
        List<FollowTraderSubscribeEntity> subscribeList = followTraderSubscribeService.list();
        List<FollowTraderEntity> traders = followTraderService.list();
        Map<String, FollowTraderEntity> traderMap = traders.stream().collect(Collectors.toMap(o -> {
            return o.getServerId() + ":" + o.getAccount();
        }, Function.identity(),(k1,k2)->k2));
        if(stringObjectMap!=null) {
            //订阅关系map key：跟单者id val 喊单者id
            Map<Long,FollowTraderSubscribeEntity > subscribeMap=new HashMap<>();
            if(ObjectUtil.isNotEmpty(subscribeList)){
                subscribeMap = subscribeList.stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, Function.identity()));
            }
            Map<Long, FollowTraderSubscribeEntity> finalSubscribeMap = subscribeMap;
            stringObjectMap.forEach((k, v) -> {
                String[] split = k.toString().split(":");
                String key = split[0];
                List<FollowTraderAnalysisEntity> list = map.get(key);
                if(list==null){
                    list=new ArrayList<>();
                }
                FollowTraderAnalysisEntity followTraderAnalysisEntity = JSONObject.parseObject(v.toString(), FollowTraderAnalysisEntity.class);
                if(TraderTypeEnum.MASTER_REAL.getType().equals(followTraderAnalysisEntity.getType())){
                    followTraderAnalysisEntity.setSourceAccount(followTraderAnalysisEntity.getAccount());
                    followTraderAnalysisEntity.setSourcePlatform(followTraderAnalysisEntity.getPlatform());
                }else{
                    FollowTraderEntity followTraderEntity = traderMap.get(followTraderAnalysisEntity.getVpsId() + ":" + followTraderAnalysisEntity.getAccount());
                    if(followTraderEntity!=null){
                        FollowTraderSubscribeEntity subscribeEntity = finalSubscribeMap.get(followTraderEntity.getId());
                        String masterAccount = subscribeEntity.getMasterAccount();
                        followTraderAnalysisEntity.setSourceAccount(masterAccount);
                        FollowTraderEntity masterTrader = traderMap.get(followTraderAnalysisEntity.getVpsId() + ":" + masterAccount);
                        if(masterTrader!=null){
                            followTraderAnalysisEntity.setSourcePlatform(masterTrader.getPlatform());
                        }
                    }


                }
                list.add(followTraderAnalysisEntity);
                map.put(split[0],list);
            });
        }
        return map;
    }

    @Override
    public StatDataVO getStatData() {
        StatDataVO statData=followTraderAnalysisService.getStatData();
        //
        BigDecimal lots=BigDecimal.ZERO;
       BigDecimal num=BigDecimal.ZERO;
        BigDecimal profit=BigDecimal.ZERO;
        List<SymbolChartVO> list = getSymbolChart();
        for (int i = 0; i < list.size(); i++) {
            SymbolChartVO chartVO = list.get(i);
            lots= lots.add(chartVO.getLots());
            num= num.add(chartVO.getNum());
            profit= profit.add(chartVO.getProfit());
        }
        return statData;
    }

    @Override
    public List<RankVO> getRanking(Query query) {
 /*      boolean asc = query.isAsc();
        LambdaQueryWrapper<FollowTraderAnalysisEntity> wrapper = new LambdaQueryWrapper<>();
        if(ObjectUtil.isNotEmpty(query.getLimit())){
            wrapper.last("limit "+query.getLimit());
        }
        wrapper.orderBy(true,asc,FollowTraderAnalysisEntity::getProfit);
        List<FollowTraderAnalysisEntity> list = followTraderAnalysisService.list(wrapper);
        List<RankVO> ranks=  FollowTraderAnalysisConvert.INSTANCE.convertRank(list);
        return ranks;*/
        if(ObjectUtil.isEmpty(query.getOrder())){
            throw new ServerException("排序字段不能为空");
        }
        if(ObjectUtil.isEmpty(query.getLimit())){
            throw new ServerException("条数字段不能为空");
        }
        Map<Object, Object> stringObjectMap = redisCache.hGetStrAll(Constant.STATISTICS_SYMBOL_INFO);
        Map<String,RankVO> rankMap = new HashMap<>();
        Map<String, Integer> map =new HashMap<>();
           if(stringObjectMap!=null) {
               Collection<Object> values = stringObjectMap.values();
               values.forEach(o->{
                   FollowTraderAnalysisEntity analysis = JSONObject.parseObject(o.toString(), FollowTraderAnalysisEntity.class);
                   Integer i = map.get(analysis.getSymbol() + "_" + analysis.getAccount()+"_"+analysis.getPlatformId());
                   if(i==null){
                       RankVO rankVO = rankMap.get(analysis.getAccount());
                       if(rankVO==null){
                           rankVO=new RankVO();
                           rankVO.setAccount(analysis.getAccount());
                           rankVO.setPlatform(analysis.getPlatform());
                           rankVO.setLots(analysis.getLots());
                           rankVO.setNum(analysis.getNum());
                           rankVO.setProfit(analysis.getProfit());
                           rankVO.setFreeMargin(analysis.getFreeMargin());
                       }else{
                           rankVO.setAccount(analysis.getAccount());
                           rankVO.setPlatform(analysis.getPlatform());
                           BigDecimal lots = rankVO.getLots().add(analysis.getLots());
                           rankVO.setLots(lots);
                           BigDecimal num = rankVO.getNum().add(analysis.getNum());
                           rankVO.setNum(num);
                           BigDecimal profit = rankVO.getProfit().add(analysis.getProfit());
                           rankVO.setProfit(profit);
                           rankVO.setFreeMargin(analysis.getFreeMargin());
                       }
                       rankMap.put(analysis.getAccount(),rankVO);
                       map.put(analysis.getSymbol() + "_" + analysis.getAccount(),1);
                   }

               });
           }
        List<RankVO> list =new ArrayList<>();
        if (query.getOrder().equals("profit")) {
            list = rankMap.values().stream().sorted((o1, o2) -> {
                if(query.isAsc()){
                    return o1.getProfit().compareTo(o2.getProfit());
                }else{
                    return o2.getProfit().compareTo(o1.getProfit());
                }

            }).limit(query.getLimit()).toList();
        }
        if (query.getOrder().equals("lots")) {
           list = rankMap.values().stream().sorted((o1, o2) -> {
               if(query.isAsc()){
                   return o1.getLots().compareTo(o2.getLots());
               }else {
                   return o2.getLots().compareTo(o1.getLots());
               }

            }).limit(query.getLimit()).toList();
        }
        if (query.getOrder().equals("num")) {
            list = rankMap.values().stream().sorted((o1, o2) -> {
             if(query.isAsc()){
                 return o1.getNum().compareTo(o2.getNum());
             }else {
                 return o2.getNum().compareTo(o1.getNum());
             }
            }).limit(query.getLimit()).toList();
        }
        if (query.getOrder().equals("freeMargin")) {
            list = rankMap.values().stream().sorted((o1, o2) -> {
                if(query.isAsc()){
                    return o1.getFreeMargin().compareTo(o2.getFreeMargin());
                }else {
                    return o2.getFreeMargin().compareTo(o1.getFreeMargin());
                }
            }).limit(query.getLimit()).toList();
        }
       return list;
    }

    @Override
    public List<SymbolChartVO> getSymbolChart() {
     //   return followTraderAnalysisService.getSymbolChart();

        Map<Object, Object> stringObjectMap = redisCache.hGetStrAll(Constant.STATISTICS_SYMBOL_INFO);
        Map<String, Integer> map =new HashMap<>();
        Map<String, SymbolChartVO> symbolMap =new HashMap<>();
        if(stringObjectMap!=null) {
            stringObjectMap.forEach((k, v) -> {
                FollowTraderAnalysisEntity analysis = JSONObject.parseObject(v.toString(), FollowTraderAnalysisEntity.class);
                Integer i = map.get(analysis.getSymbol() + "_" + analysis.getAccount()+"_"+analysis.getPlatformId());
                if(i==null){
                    SymbolChartVO vo = symbolMap.get(analysis.getSymbol());
                    if(vo==null){
                        vo=new SymbolChartVO();
                        vo.setBuyLots(analysis.getBuyLots());
                        vo.setBuyNum(analysis.getBuyNum());
                        vo.setBuyProfit(analysis.getBuyProfit());
                        vo.setSellLots(analysis.getSellLots());
                        vo.setSellProfit(analysis.getSellProfit());
                        vo.setSellNum(analysis.getSellNum());
                        vo.setPosition(analysis.getPosition());
                        vo.setLots(analysis.getLots());
                        vo.setNum(analysis.getNum());
                        vo.setProfit(analysis.getProfit());
                    }else{
                        BigDecimal buylots = vo.getBuyLots().add(analysis.getBuyLots());
                        vo.setBuyLots(buylots);
                        BigDecimal buyNum = vo.getBuyNum().add(analysis.getBuyNum());
                        vo.setBuyNum(buyNum);
                        BigDecimal buyProfit = vo.getBuyProfit().add(analysis.getBuyProfit());
                        vo.setBuyProfit(buyProfit);
                        BigDecimal sellLots = vo.getSellLots().add(analysis.getSellLots());
                        vo.setSellLots(sellLots);
                        BigDecimal sellProfit = vo.getSellProfit().add(analysis.getSellProfit());
                        vo.setSellProfit(sellProfit);
                        BigDecimal sellNum = vo.getSellNum().add(analysis.getSellNum());
                        vo.setSellNum(sellNum);
                        BigDecimal position = vo.getPosition().add(analysis.getPosition());
                        vo.setPosition(position);
                        BigDecimal lots = vo.getLots().add(analysis.getLots());
                        vo.setLots(lots);
                        BigDecimal num = vo.getNum().add(analysis.getNum());
                        vo.setNum(num);
                        BigDecimal profit = vo.getProfit().add(analysis.getProfit());
                        vo.setProfit(profit);
                    }
                    vo.setSymbol(analysis.getSymbol());
                    map.put(analysis.getSymbol() + "_" + analysis.getAccount()+"_"+analysis.getPlatformId(),1);
                    symbolMap.put(analysis.getSymbol() ,vo);
                }

            });
        }
        Collection<SymbolChartVO> values = symbolMap.values();
        return new ArrayList<>(values);
    }

    @Override
    public List<FollowPlatformEntity> searchPlatform(String brokerName) {
        List<FollowPlatformEntity> list = followPlatformService.lambdaQuery().select(FollowPlatformEntity::getBrokerName,FollowPlatformEntity::getServer).like(ObjectUtil.isNotEmpty(brokerName), FollowPlatformEntity::getBrokerName, brokerName).groupBy(FollowPlatformEntity::getBrokerName).list();
        return list;
    }
}
