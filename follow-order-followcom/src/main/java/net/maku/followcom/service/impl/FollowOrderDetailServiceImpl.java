package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.log.Log;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowOrderDetailConvert;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.query.FollowOrderDetailQuery;
import net.maku.followcom.vo.FollowOrderDetailVO;
import net.maku.followcom.dao.FollowOrderDetailDao;
import net.maku.followcom.service.FollowOrderDetailService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowOrderDetailExcelVO;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.Order;
import online.mtapi.mt4.QuoteClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderDetailServiceImpl extends BaseServiceImpl<FollowOrderDetailDao, FollowOrderDetailEntity> implements FollowOrderDetailService {
    private final TransService transService;
    private final FollowOrderDetailDao followOrderDetailDao;
    @Override
    public PageResult<FollowOrderDetailVO> page(FollowOrderDetailQuery query) {
        IPage<FollowOrderDetailEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderDetailConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderDetailEntity> getWrapper(FollowOrderDetailQuery query){
        LambdaQueryWrapper<FollowOrderDetailEntity> wrapper = Wrappers.lambdaQuery();
        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            wrapper.eq(FollowOrderDetailEntity::getTraderId, query.getTraderId());
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


        wrapper.gt(ObjectUtil.isNotEmpty(query.getStartTime()), FollowOrderDetailEntity::getCloseTime, startTime);
        wrapper.lt(ObjectUtil.isNotEmpty(query.getEndTime()), FollowOrderDetailEntity::getCloseTime, endTime);
        wrapper.eq(ObjectUtil.isNotEmpty(query.getType()), FollowOrderDetailEntity::getType, query.getType());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getAccount()), FollowOrderDetailEntity::getAccount, query.getAccount());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getPlatform()), FollowOrderDetailEntity::getPlatform, query.getPlatform());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getSendNo()), FollowOrderDetailEntity::getSendNo, query.getSendNo());
        if(query.getIsHistory()){
            wrapper.isNotNull(FollowOrderDetailEntity::getCloseTime);
        }

        return wrapper;
    }


    @Override
    public FollowOrderDetailVO get(Long id) {
        FollowOrderDetailEntity entity = baseMapper.selectById(id);
        FollowOrderDetailVO vo = FollowOrderDetailConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderDetailVO vo) {
        FollowOrderDetailEntity entity = FollowOrderDetailConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderDetailVO vo) {
        FollowOrderDetailEntity entity = FollowOrderDetailConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export(List<FollowOrderDetailVO> followOrderDetailVOList) {
        List<FollowOrderDetailExcelVO> excelList = FollowOrderDetailConvert.INSTANCE.convertExcelList3(followOrderDetailVOList);
        excelList.parallelStream().forEach(o->{
            //设置类型
            if (o.getType()==0){
                o.setTypeName("BUY");
            }else{
                o.setTypeName("SELL");
            }
        });
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderDetailExcelVO.class, "订单列表", null, excelList);
    }

    @Override
    public PageResult<FollowOrderSlipPointVO> listFollowOrderSlipPoint(FollowOrderSpliListQuery query) {
        Page<?> pageRequest = new Page<>(query.getPage(), query.getLimit());
        query.setServer(FollowConstant.LOCAL_HOST);
        Page<FollowOrderSlipPointVO> page = followOrderDetailDao.getFollowOrderDetailStats(pageRequest,query);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }


    @Override
    public void saveOrderHistory(QuoteClient quoteClient, FollowTraderEntity u, LocalDateTime startTime) {
        try {
            Order[] orders = quoteClient.DownloadOrderHistory(startTime, LocalDateTime.now());
            //保存历史订单
            List<FollowOrderDetailEntity> list=new ArrayList<>();
            Arrays.stream(orders).filter(order -> order.Type.equals(Op.Buy) || order.Type.equals(Op.Sell) || order.Type.equals(Op.Balance) || order.Type.equals(Op.Credit)).forEach(order -> {
                FollowOrderDetailEntity entity = getFollowOrderDetailEntity(quoteClient, u, order);
                list.add(entity);
            });

            if(ObjectUtil.isNotEmpty(list)) {
                customBatchSaveOrUpdate(list);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static FollowOrderDetailEntity getFollowOrderDetailEntity(QuoteClient quoteClient, FollowTraderEntity u, Order order) {
        FollowOrderDetailEntity entity =new FollowOrderDetailEntity();
        entity.setSymbol(order.Symbol);
        entity.setType(order.Type.getValue());
        entity.setOrderNo(order.Ticket);
        entity.setTraderId(u.getId());
        entity.setAccount(u.getAccount());
        //  entity.setRequestOpenTime(order.OpenTime);
        // entity.setRequestCloseTime(order.CloseTime);
        entity.setOpenTime(order.OpenTime);
        if(order.Type.equals(Op.Balance) || order.Type.equals(Op.Credit)){
            entity.setOpenPrice(null);
            entity.setSize(null);

        }else{
            entity.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
            entity.setSize(BigDecimal.valueOf(order.Lots));
        }
        entity.setClosePrice(BigDecimal.valueOf(order.ClosePrice));
        if(order.CloseTime!=null){
            String time = DateUtil.format(order.CloseTime, "yyyy-MM-dd");
            if(!"1970-01-01".equals(time)){
                entity.setCloseTime(order.CloseTime);
                entity.setCloseServerHost(quoteClient.Host+":"+ quoteClient.Port);
                entity.setCloseStatus(CloseOrOpenEnum.OPEN.getValue());
                entity.setRemark(null);
            }

        }
        entity.setSl(BigDecimal.valueOf(order.StopLoss));
        entity.setSwap(BigDecimal.valueOf(order.Swap));
        entity.setTp(BigDecimal.valueOf(order.TakeProfit));
        entity.setCommission(BigDecimal.valueOf(order.Commission));
        entity.setProfit(BigDecimal.valueOf(order.Profit));
        // entity.setPlacedType(order);
        //   entity.setBrokeName();
        entity.setPlatform(u.getPlatform());
        entity.setIpAddr(u.getIpAddr());
        entity.setServerName(u.getServerName());
        entity.setRateMargin(order.RateMargin);
       // entity.setMagical(order.Ticket);
        entity.setComment(order.Comment);
        entity.setIsExternal(1);
     //   entity.setPlacedType(order.p);
        // entity.setBrokeName(u.get);
         //  entity.setSourceUser(u.getAccount());
        entity.setServerHost(quoteClient.Host+":"+ quoteClient.Port);

        return entity;
    }


    @Override
    public void saveOrderActive(QuoteClient quoteClient, FollowTraderEntity u) {
        Order[] orders = quoteClient.GetOpenedOrders();
        List<FollowOrderDetailEntity> list=new ArrayList<>();
        Arrays.stream(orders).filter(order -> order.Type.equals(Op.Buy) || order.Type.equals(Op.Sell)).forEach(order -> {
            FollowOrderDetailEntity entity = getFollowOrderDetailEntity(quoteClient, u, order);
            entity.setIsExternal(1);
            list.add(entity);
        });
        if(ObjectUtil.isNotEmpty(list)) {
            customBatchSaveOrUpdate(list);
        }
    }

    @Override
    public List<FollowOrderDetailEntity> getInstruct(String orderNo) {
        return baseMapper.getInstruct(orderNo);
    }

    /**
     * 自定义保存或者更新，根据唯一约束处理
     */
    public void customBatchSaveOrUpdate(List<FollowOrderDetailEntity> list) {
        baseMapper.customBatchSaveOrUpdate(list);
    }
}