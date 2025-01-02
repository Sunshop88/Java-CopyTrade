package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderAnalysisConvert;
import net.maku.followcom.entity.FollowTraderAnalysisEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.FollowTraderAnalysisQuery;
import net.maku.followcom.query.SymbolAnalysisQuery;
import net.maku.followcom.service.DashboardService;
import net.maku.followcom.service.FollowTraderAnalysisService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.query.Query;
import net.maku.framework.common.utils.PageResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    /**
     * 仪表盘-账号数据
     * */
    @Override
    public PageResult<DashboardAccountDataVO> getAccountDataPage(DashboardAccountQuery vo) {
        IPage<FollowTraderEntity> page=new Page<>(vo.getPage(), vo.getLimit());
        IPage<DashboardAccountDataVO> accountDataPage = followTraderService.getAccountDataPage(page, vo);
        List<DashboardAccountDataVO> records = accountDataPage.getRecords();
        List<FollowTraderEntity> masters = followTraderService.lambdaQuery().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).list();

        Map<Long, FollowTraderEntity> masterMap=new HashMap<>();
        if(ObjectUtil.isNotEmpty(masters)){
            masterMap = masters.stream().collect(Collectors.toMap(FollowTraderEntity::getId, Function.identity()));
        }
        //订阅关系
        List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list();
        //订阅关系map key：跟单者id val 喊单者id
        Map<Long, Long> subscribeMap=new HashMap<>();
        if(ObjectUtil.isNotEmpty(list)){
            subscribeMap = list.stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, FollowTraderSubscribeEntity::getMasterId));
        }
        if(ObjectUtil.isNotEmpty(records)){
            for (int i = 0; i < records.size(); i++) {
                DashboardAccountDataVO record = records.get(i);
                //设置信号源数据
                if(TraderTypeEnum.MASTER_REAL.getType().equals(record.getType())){
                    record.setSourceAccount(record.getAccount());
                    record.setSourceServer(record.getServer());
                }else{
                    Long masterId = subscribeMap.get(record.getTraderId());
                    FollowTraderEntity followTraderEntity = masterMap.get(masterId);
                    record.setSourceAccount(followTraderEntity.getAccount());
                    record.setSourceServer(followTraderEntity.getPlatform());
                }
                //处理redis的数据
                FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + record.getTraderId());
                if(ObjectUtil.isNotEmpty(followRedisTraderVO)){
                    record.setProfit(followRedisTraderVO.getProfit());
                    record.setMarginProportion(followRedisTraderVO.getMarginProportion());
                    double n = followRedisTraderVO.getBuyNum() + followRedisTraderVO.getSellNum();
                    record.setOrderNum(followRedisTraderVO.getTotal());
                    record.setLots(n);
                }
            }
         
        }

        return new PageResult<>(accountDataPage.getRecords(), page.getTotal());
    }

    @Override
    public List<SymbolAnalysisVO> getSymbolAnalysis(SymbolAnalysisQuery vo) {

        return followTraderAnalysisService.getSymbolAnalysis(vo);
    }

    @Override
    public List<FollowTraderAnalysisEntity> getSymbolAnalysisDetails(TraderAnalysisVO vo) {
        LambdaQueryWrapper<FollowTraderAnalysisEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getSymbol()), FollowTraderAnalysisEntity::getSymbol, vo.getSymbol());
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getAccount()), FollowTraderAnalysisEntity::getAccount, vo.getAccount());
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getVpsId()), FollowTraderAnalysisEntity::getVpsId, vo.getVpsId());
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getPlatform()), FollowTraderAnalysisEntity::getPlatform, vo.getPlatform());
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getSourceAccount()), FollowTraderAnalysisEntity::getSourceAccount, vo.getSourceAccount());
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getSourcePlatform()), FollowTraderAnalysisEntity::getSourcePlatform, vo.getSourcePlatform());
        wrapper.eq(ObjectUtil.isNotEmpty(vo.getType()), FollowTraderAnalysisEntity::getType, vo.getType());
        return    followTraderAnalysisService.list(wrapper);
    }

    @Override
    public StatDataVO getStatData() {
        StatDataVO statData=followTraderAnalysisService.getStatData();
        return statData;
    }

    @Override
    public List<RankVO> getRanking(Query query) {
        boolean asc = query.isAsc();
        LambdaQueryWrapper<FollowTraderAnalysisEntity> wrapper = new LambdaQueryWrapper<>();
        if(ObjectUtil.isNotEmpty(query.getLimit())){
            wrapper.last("limit "+query.getLimit());
        }
        wrapper.orderBy(true,asc,FollowTraderAnalysisEntity::getProfit);
        List<FollowTraderAnalysisEntity> list = followTraderAnalysisService.list(wrapper);
        List<RankVO> ranks=  FollowTraderAnalysisConvert.INSTANCE.convertRank(list);
        return ranks;
    }

    @Override
    public List<SymbolChartVO> getSymbolChart() {

        return followTraderAnalysisService.getSymbolChart();
    }
}
