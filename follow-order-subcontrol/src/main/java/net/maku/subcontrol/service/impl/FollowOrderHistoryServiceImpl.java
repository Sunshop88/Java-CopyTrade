package net.maku.subcontrol.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.framework.common.utils.DateUtils;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.subcontrol.convert.FollowOrderHistoryConvert;
import net.maku.subcontrol.dao.FollowOrderHistoryDao;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.query.FollowOrderHistoryQuery;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.vo.FollowOrderHistoryExcelVO;
import net.maku.subcontrol.vo.FollowOrderHistoryVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderHistoryServiceImpl extends BaseServiceImpl<FollowOrderHistoryDao, FollowOrderHistoryEntity> implements FollowOrderHistoryService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderHistoryVO> page(FollowOrderHistoryQuery query) {
        IPage<FollowOrderHistoryEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderHistoryConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderHistoryEntity> getWrapper(FollowOrderHistoryQuery query) {
        LambdaQueryWrapper<FollowOrderHistoryEntity> wrapper = Wrappers.lambdaQuery();
        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            wrapper.eq(FollowOrderHistoryEntity::getTraderId, query.getTraderId());
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 解析字符串为 LocalDateTime
        LocalDateTime startTime =null;
        if (ObjectUtil.isNotEmpty(query.getStartTime())) {
            startTime = LocalDateTime.parse(query.getStartTime(), formatter);
        }
        LocalDateTime endTime =null;
        if (ObjectUtil.isNotEmpty(query.getEndTime())) {
            endTime =  LocalDateTime.parse(query.getEndTime(), formatter);
        }


        wrapper.gt(ObjectUtil.isNotEmpty(query.getStartTime()), FollowOrderHistoryEntity::getCloseTime, startTime);
        wrapper.lt(ObjectUtil.isNotEmpty(query.getEndTime()), FollowOrderHistoryEntity::getCloseTime, endTime);
        wrapper.eq(ObjectUtil.isNotEmpty(query.getType()), FollowOrderHistoryEntity::getType, query.getType());
        return wrapper;
    }


    @Override
    public FollowOrderHistoryVO get(Long id) {
        FollowOrderHistoryEntity entity = baseMapper.selectById(id);
        FollowOrderHistoryVO vo = FollowOrderHistoryConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderHistoryVO vo) {
        FollowOrderHistoryEntity entity = FollowOrderHistoryConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderHistoryVO vo) {
        FollowOrderHistoryEntity entity = FollowOrderHistoryConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
        List<FollowOrderHistoryExcelVO> excelList = FollowOrderHistoryConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderHistoryExcelVO.class, "所有MT4账号的历史订单", null, excelList);
    }

    /**
     * 自定义保存或者更新，根据唯一约束处理
     */
    @Override
    public void customBatchSaveOrUpdate(List<FollowOrderHistoryEntity> list) {
        baseMapper.customBatchSaveOrUpdate(list);
    }

    @Override
    public void saveOrderHistory(QuoteClient quoteClient, FollowTraderEntity leader) {
        try {
            Calendar cal = Calendar.getInstance();
            //获取数据库最后一次历史订单时间，追溯5天
            cal.add(Calendar.DATE, -5);
            //日历往前追溯3个月
            //    cal.add(Calendar.MONTH,-3);
            //获取mt4历史订单
            Order[] orders = quoteClient.DownloadOrderHistory(DateUtil.toLocalDateTime(DateUtil.offsetDay(DateUtil.date(),-5)), LocalDateTime.now());
            //保存历史订单
            List<FollowOrderHistoryEntity> list=new ArrayList<>();
            Arrays.stream(orders).filter(order -> order.Type.equals(Op.Buy) || order.Type.equals(Op.Sell) || order.Type.equals(Op.Balance)).forEach(order -> {
                FollowOrderHistoryEntity historyEntity = new FollowOrderHistoryEntity();
                historyEntity.setTraderId(leader.getId());
                historyEntity.setAccount(leader.getAccount());
                historyEntity.setOrderNo(order.Ticket);
                historyEntity.setType(order.Type.getValue());
                historyEntity.setOpenTime(order.OpenTime);
                historyEntity.setCloseTime(order.CloseTime);
                historyEntity.setSize(BigDecimal.valueOf(order.Lots));
                historyEntity.setSymbol(order.Symbol);
                historyEntity.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
                historyEntity.setClosePrice(BigDecimal.valueOf(order.ClosePrice));
                //止损
                BigDecimal copierProfit = new BigDecimal(order.Swap + order.Commission + order.Profit).setScale(2, RoundingMode.HALF_UP);
                historyEntity.setProfit(copierProfit);
                historyEntity.setComment(order.Comment);
                historyEntity.setSwap(BigDecimal.valueOf(order.Swap));
                historyEntity.setMagic(order.MagicNumber);
                historyEntity.setTp(BigDecimal.valueOf(order.TakeProfit));
                historyEntity.setSymbol(order.Symbol);
                historyEntity.setSl(BigDecimal.valueOf(order.StopLoss));
                historyEntity.setCreateTime(LocalDateTime.now());
                historyEntity.setVersion(0);
                historyEntity.setCommission(BigDecimal.valueOf(order.Commission));
                list.add(historyEntity);
            });
            if(ObjectUtil.isNotEmpty(list)) {
                customBatchSaveOrUpdate(list);
            }

        } catch (Exception e) {
            log.error("保存历史数据失败"+e);
        }
    }



}