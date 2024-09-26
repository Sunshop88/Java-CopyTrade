package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowOrderDetailConvert;
import net.maku.followcom.convert.FollowSysmbolSpecificationConvert;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.dao.FollowOrderDetailDao;
import net.maku.followcom.dao.FollowTraderDao;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.RandomStringUtil;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import online.mtapi.mt4.*;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Exception.TradeException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
@Slf4j
public class FollowTraderServiceImpl extends BaseServiceImpl<FollowTraderDao, FollowTraderEntity> implements FollowTraderService {
    private final TransService transService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowVpsService followVpsService;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final FollowOrderSendService followOrderSendService;
    private final FollowOrderDetailService followOrderDetailService;
    private final RedisCache redisCache;
    @Override
    public PageResult<FollowTraderVO> page(FollowTraderQuery query) {
        IPage<FollowTraderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderEntity> getWrapper(FollowTraderQuery query){
        LambdaQueryWrapper<FollowTraderEntity> wrapper = Wrappers.lambdaQuery();
        //查询指定VPS下的账号
        wrapper.eq(FollowTraderEntity::getDeleted,query.getDeleted());
        return wrapper;
    }


    @Override
    public FollowTraderVO get(Long id) {
        FollowTraderEntity entity = baseMapper.selectById(id);
        FollowTraderVO vo = FollowTraderConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowTraderVO save(FollowTraderVO vo) {
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);
        FollowVpsEntity followVpsEntity = followVpsService.list().get(0);
        if (ObjectUtil.isEmpty(followVpsEntity)){
            throw new ServerException("请先添加VPS");
        }
        entity.setIpAddr(followVpsEntity.getIpAddress());
        entity.setServerId(followVpsEntity.getId());
        entity.setServerName(followVpsEntity.getName());
        entity.setCreator(SecurityUser.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        baseMapper.insert(entity);
        FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(entity);
        followTraderVO.setId(entity.getId());
        return followTraderVO;
    }

    private void addSysmbolSpecification(long traderId,QuoteClient quoteClient) {
        Long userId = SecurityUser.getUserId();
        LocalDateTime localDateTime = LocalDateTime.now();
        ThreadPoolUtils.execute(()->{
            try {
                String[] symbols = quoteClient.Symbols();
                Arrays.stream(symbols).forEach(o->{
                    try {
                        SymbolInfo symbolInfo = quoteClient.GetSymbolInfo(o);
                        ConGroupSec conGroupSec = quoteClient.GetSymbolGroupParams(o);
                        FollowSysmbolSpecificationVO followSysmbolSpecificationVO=new FollowSysmbolSpecificationVO();
                        followSysmbolSpecificationVO.setTraderId(traderId);
                        followSysmbolSpecificationVO.setDigits(symbolInfo.Digits);
                        followSysmbolSpecificationVO.setContractSize(symbolInfo.ContractSize);
                        followSysmbolSpecificationVO.setLotStep(Double.valueOf(conGroupSec.lot_step));
                        followSysmbolSpecificationVO.setMarginCurrency(symbolInfo.MarginCurrency);
                        followSysmbolSpecificationVO.setMaxLot(Double.valueOf(conGroupSec.lot_max));
                        followSysmbolSpecificationVO.setProfitMode(symbolInfo.ProfitMode.name());
                        followSysmbolSpecificationVO.setMinLot(Double.valueOf(conGroupSec.lot_min));
                        followSysmbolSpecificationVO.setSwapLong(symbolInfo.SwapLong);
                        followSysmbolSpecificationVO.setSwapShort(symbolInfo.SwapShort);
                        followSysmbolSpecificationVO.setSymbol(o);
                        followSysmbolSpecificationVO.setCreator(userId);
                        followSysmbolSpecificationVO.setCreateTime(localDateTime);
                        followSysmbolSpecificationVO.setMarginMode(symbolInfo.MarginMode.name());
                        followSysmbolSpecificationVO.setCurrency(symbolInfo.Currency);
                        followSysmbolSpecificationVO.setStopsLevel(symbolInfo.StopsLevel);
                        followSysmbolSpecificationVO.setFreezeLevel(symbolInfo.FreezeLevel);
                        followSysmbolSpecificationVO.setSpread(symbolInfo.Spread);
                        followSysmbolSpecificationVO.setMarginDivider(symbolInfo.MarginDivider);
                        followSysmbolSpecificationService.saveOrUpdate(FollowSysmbolSpecificationConvert.INSTANCE.convert(followSysmbolSpecificationVO));
                    } catch (InvalidSymbolException e) {
                        throw new RuntimeException(e);
                    } catch (ConnectException e) {
                        throw new RuntimeException(e);
                    }
                });
                //查询改账号的品种规格
                List<FollowSysmbolSpecificationEntity> list = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
                redisCache.set(Constant.SYMBOL_SPECIFICATION+traderId,list);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            } catch (ConnectException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderVO vo) {
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);
//        list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId, idList)).forEach(entity -> {
//            // 删除
//            entity.setDeleted(CloseOrOpenEnum.OPEN.getValue());
//            updateById(entity);
//        });

    }


    @Override
    public void export() {
    List<FollowTraderExcelVO> excelList = FollowTraderConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderExcelVO.class, "mt4账号", null, excelList);
    }

    @Override
    public boolean orderSend(FollowOrderSendVO vo,QuoteClient quoteClient,FollowTraderVO followTraderVO) {
        try {
            //买入、卖出价格
            quoteClient.Subscribe(vo.getSymbol());
            while (quoteClient.GetQuote(vo.getSymbol()) == null);
            //创建下单记录
            String orderNo = RandomStringUtil.generateNumeric(13);
            vo.setCreator(SecurityUser.getUserId());
            vo.setCreateTime(LocalDateTime.now());
            vo.setAccount(followTraderVO.getAccount());
            vo.setOrderNo(orderNo);
            //根据情况进行下单
            if (ObjectUtil.isNotEmpty(vo.getTotalSzie())&&ObjectUtil.isNotEmpty(vo.getTotalNum())&&ObjectUtil.isEmpty(vo.getStartSize())&&ObjectUtil.isEmpty(vo.getEndSize())){
                //情况一  总手数+订单数量情况 填写 范围未填写
                executeOrdersFixedQuantity(vo,followTraderVO.getId(),followTraderVO.getAccount(),vo.getType(),quoteClient,vo.getSymbol(),vo.getTotalSzie().doubleValue(), vo.getTotalNum(),vo.getIntervalTime(),orderNo);
            }else if (ObjectUtil.isNotEmpty(vo.getTotalSzie())&&ObjectUtil.isEmpty(vo.getTotalNum())&&ObjectUtil.isNotEmpty(vo.getStartSize())&&ObjectUtil.isNotEmpty(vo.getEndSize())){
                //情况二  区间随机下单，订单数量不固定，总手数不超过设定值
                executeOrdersRandomTotalLots(vo,followTraderVO.getId(),followTraderVO.getAccount(),vo.getType(),quoteClient,vo.getSymbol(),vo.getTotalSzie().doubleValue(), vo.getStartSize(),vo.getEndSize(),vo.getIntervalTime(),orderNo);
            }else if (ObjectUtil.isEmpty(vo.getTotalSzie())&&ObjectUtil.isNotEmpty(vo.getTotalNum())&&ObjectUtil.isNotEmpty(vo.getStartSize())&&ObjectUtil.isNotEmpty(vo.getEndSize())){
                //情况三  区间随机下单，订单数量固定，总手数不限制
                executeOrdersRandomFixedCount(vo,followTraderVO.getId(),followTraderVO.getAccount(),vo.getType(),quoteClient,vo.getSymbol(),vo.getTotalNum(), vo.getStartSize(),vo.getEndSize(),vo.getIntervalTime(),orderNo);
            }else if (ObjectUtil.isNotEmpty(vo.getTotalSzie())&&ObjectUtil.isNotEmpty(vo.getTotalNum())&&ObjectUtil.isNotEmpty(vo.getStartSize())&&ObjectUtil.isNotEmpty(vo.getEndSize())){
                //区间随机下单，总手数和订单数量都受限制
                executeOrdersRandomLimited(vo,followTraderVO.getId(),followTraderVO.getAccount(),vo.getType(),quoteClient,vo.getSymbol(),vo.getTotalNum(),vo.getTotalSzie().doubleValue(), vo.getStartSize(),vo.getEndSize(),vo.getIntervalTime(),orderNo);
            }
        } catch (InvalidSymbolException e) {
            throw new ServerException("此品种不支持下单");
        } catch (TimeoutException e) {
            throw new ServerException("下单超时，请稍后再试");
        } catch (ConnectException e) {
            throw new ServerException("账号连接异常,请稍后再试");
        }
        return true;
    }

    @Override
    public PageResult<FollowOrderSlipPointVO> pageSlipPoint(FollowOrderSpliListQuery query) {
        return followOrderDetailService.listFollowOrderSlipPoint(query);
    }

    @Override
    public PageResult<FollowOrderDetailVO> orderSlipDetail(FollowOrderSendQuery query) {
        LambdaQueryWrapper<FollowOrderDetailEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowOrderDetailEntity::getTraderId,query.getTraderId());
        //滑点分析筛选成功的数据
        if (query.getFlag().equals(CloseOrOpenEnum.OPEN.getValue())){
            wrapper.isNotNull(FollowOrderDetailEntity::getOpenTime);
        }
        if (ObjectUtil.isNotEmpty(query.getStartTime())&&ObjectUtil.isNotEmpty(query.getEndTime())){
            wrapper.ge(FollowOrderDetailEntity::getOpenTime, query.getStartTime());  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getOpenTime, query.getEndTime());    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getOrderNo())){
            wrapper.eq(FollowOrderDetailEntity::getSendNo,query.getOrderNo());
        }
        wrapper.orderByDesc(FollowOrderDetailEntity::getCreateTime);
        Page<FollowOrderDetailEntity> page = new Page<>(query.getPage(), query.getLimit());
        Page<FollowOrderDetailEntity> pageOrder = followOrderDetailService.page(page, wrapper);
        return  new PageResult<>(FollowOrderDetailConvert.INSTANCE.convertList(pageOrder.getRecords()), pageOrder.getTotal());
    }

    @Override
    public boolean orderClose(FollowOrderCloseVO vo,QuoteClient quoteClient) {
        //登录
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)){
            oc=quoteClient.OrderClient;
        }else {
            oc = new OrderClient(quoteClient);
        }
        Integer interval = vo.getIntervalTime();
        Integer orderCount;
        //查看目前未平仓订单
        List<FollowOrderDetailEntity> list ;
        if (vo.getFlag().equals(CloseOrOpenEnum.OPEN.getValue())){
           list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, vo.getTraderId()).isNotNull(FollowOrderDetailEntity::getOpenTime).isNull(FollowOrderDetailEntity::getCloseTime).orderByAsc(FollowOrderDetailEntity::getOpenTime));
            orderCount=list.size();
        }else {
            orderCount = vo.getNum();
            LambdaQueryWrapper<FollowOrderDetailEntity> followOrderDetailw = new LambdaQueryWrapper<>();
            followOrderDetailw.eq(FollowOrderDetailEntity::getTraderId, vo.getTraderId())
                    .eq(FollowOrderDetailEntity::getSymbol,vo.getSymbol()).isNotNull(FollowOrderDetailEntity::getOpenTime)
                    .isNull(FollowOrderDetailEntity::getCloseTime).orderByAsc(FollowOrderDetailEntity::getOpenTime);
            if (!vo.getType().equals(2)){
                followOrderDetailw.eq(FollowOrderDetailEntity::getType,vo.getType());
            }
            list = followOrderDetailService.list(followOrderDetailw);
        }
        if (ObjectUtil.isEmpty(orderCount)||orderCount==0){
            throw new ServerException("暂无可平仓订单");
        }
        // 无间隔时间下单时并发执行
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            ExecutorService executor = Executors.newFixedThreadPool(orderCount);
            CountDownLatch latch = new CountDownLatch(orderCount);  // 初始化一个计数器，数量为任务数

            for (int i = 0; i < orderCount; i++) {
                int finalI = i;
                executor.submit(() -> {
                    //平仓数据处理
                    updateCloseOrder(list.get(finalI),quoteClient,oc);
                    latch.countDown();  // 每当一个任务完成，计数器减1
                });
            }
            // 等待所有任务完成
            try {
                latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                log.info("所有平仓任务已完成");
                Thread.sleep(1000);
                ThreadPoolUtils.execute(()->{
                    //进行平仓滑点分析
                    updateCloseSlip(vo.getTraderId(),vo.getSymbol());
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 关闭线程池，等待所有任务完成
            executor.shutdown();
        } else {
            // 有间隔时间的下单，依次执行并等待
            ThreadPoolUtils.execute(()-> {
                for (int i = 0; i < orderCount; i++) {
                    try {
                        Thread.sleep(interval);
                        updateCloseOrder(list.get(i), quoteClient, oc);
                        //进行平仓滑点分析
                        updateCloseSlip(vo.getTraderId(), vo.getSymbol());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        return true;
    }

    @Override
    public FollowOrderSendEntity orderDoing(Long traderId) {
        List<FollowOrderSendEntity> list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId, traderId).eq(FollowOrderSendEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).orderByDesc(FollowOrderSendEntity::getCreateTime));
        if (ObjectUtil.isEmpty(list)){
            return null;
        }
        return list.get(0);
    }

    @Override
    public void saveQuo(QuoteClient quoteClient, FollowTraderEntity entity) {
        //结束循环并保存记录
        //策略信息入库
        try {
            entity.setBalance(BigDecimal.valueOf(quoteClient.AccountBalance()));
            entity.setEuqit(BigDecimal.valueOf(quoteClient.AccountEquity()));
            //todo 默认第一个VPS
            FollowVpsEntity followVpsEntity = followVpsService.list().get(0);
            if (ObjectUtil.isEmpty(followVpsEntity)){
                throw new ServerException("请先添加VPS");
            }
            entity.setIpAddr(followVpsEntity.getIpAddress());
            entity.setDiff(quoteClient.ServerTimeZone()/60);
            entity.setServerName(followVpsEntity.getName());
            entity.setServerId(followVpsEntity.getId());
            entity.setFreeMargin(BigDecimal.valueOf(quoteClient.AccountFreeMargin()));
            //预付款比例:账户的净值÷已用预付款
            entity.setMarginProportion(BigDecimal.valueOf(quoteClient.AccountMargin()).divide(BigDecimal.valueOf(quoteClient.AccountEquity()),2, RoundingMode.HALF_UP));
            entity.setLeverage(quoteClient.AccountLeverage());
            entity.setIsDemo(quoteClient._IsDemo);
            entity.setCreator(SecurityUser.getUserId());
            entity.setCreateTime(LocalDateTime.now());
            this.updateById(entity);
            //增加品种规格记录
            addSysmbolSpecification(entity.getId(),quoteClient);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    private void updateCloseSlip(long traderId,String symbol) {
        //查询需要滑点分析的数据 有平仓价格但是无平仓滑点
        List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, traderId)
                .eq(FollowOrderDetailEntity::getSymbol, symbol).isNotNull(FollowOrderDetailEntity::getClosePrice)
                .isNull(FollowOrderDetailEntity::getClosePriceSlip));
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))){
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>)redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        }else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION+traderId,followSysmbolSpecificationEntityList);
        }
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationEntityList.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i));
        FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(symbol);
        BigDecimal hd;
        if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")){
            //如果forex 并包含JPY 也是100
            if (symbol.contains("JPY")){
                hd=new BigDecimal("100");
            }else {
                hd=new BigDecimal("10000");
            }
        }else {
            //如果非forex 都是 100
            hd=new BigDecimal("100");
        }
        list.forEach(o->{
            long seconds = DateUtil.between(DateUtil.date(o.getResponseCloseTime()), DateUtil.date(o.getRequestCloseTime()), DateUnit.MS);
            o.setCloseTimeDifference((int) seconds);
            o.setClosePriceSlip(o.getClosePrice().subtract(o.getRequestClosePrice()).multiply(hd).abs());
            followOrderDetailService.updateById(o);
        });

    }

    private void updateCloseOrder(FollowOrderDetailEntity followOrderDetailEntity,QuoteClient quoteClient,OrderClient oc) {
        String symbol = followOrderDetailEntity.getSymbol();
        Integer orderNo = followOrderDetailEntity.getOrderNo();
        try {
            double  bid = quoteClient.GetQuote(symbol).Bid;
            double  ask = quoteClient.GetQuote(symbol).Ask;
            LocalDateTime nowdate = LocalDateTime.now();
            log.info("订单 " + orderNo + ": 平仓 ");
            Order orderResult;
            if (followOrderDetailEntity.getType()==Op.Buy.getValue()){
                orderResult = oc.OrderClose(symbol, orderNo, followOrderDetailEntity.getSize().doubleValue(), bid, 0);
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(bid));
            }else {
                orderResult = oc.OrderClose(symbol, orderNo,followOrderDetailEntity.getSize().doubleValue(), ask, 0);
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(ask));
            }
            //保存平仓信息
            followOrderDetailEntity.setResponseCloseTime(LocalDateTime.now());
            followOrderDetailEntity.setRequestCloseTime(nowdate);
            followOrderDetailEntity.setCloseTime(orderResult.CloseTime);
            followOrderDetailEntity.setClosePrice(BigDecimal.valueOf(orderResult.ClosePrice));
            followOrderDetailEntity.setSwap(BigDecimal.valueOf(orderResult.Swap));
            followOrderDetailEntity.setCommission(BigDecimal.valueOf(orderResult.Commission));
            followOrderDetailEntity.setProfit(BigDecimal.valueOf(orderResult.Profit));
            followOrderDetailService.updateById(followOrderDetailEntity);
        } catch (InvalidSymbolException | TimeoutException | ConnectException | TradeException e) {
            throw new RuntimeException(e);
        }
    }

    // 示例 1: 每笔订单的下单数量为 总手数/订单数量
    public void executeOrdersFixedQuantity(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,double totalLots, int orderCount,Integer interval,String orderNo) {
        double lotsPerOrder = roundToTwoDecimal(totalLots / orderCount);
        log.info("执行固定订单数量的下单操作，共" + orderCount + "笔订单，每笔手数: " + lotsPerOrder);
        followOrderSendService.save(vo);
        // 使用线程池进行并发下单
        exeutorSend(traderId,account,quoteClient,orderCount,interval,symbol,type,lotsPerOrder,orderNo);
    }

    private void exeutorSend(long traderId,String account, QuoteClient quoteClient,Integer orderCount,Integer interval,String symbol,Integer type,double lotsPerOrder,String orderNo) {
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)){
            oc=quoteClient.OrderClient;
        }else {
            oc = new OrderClient(quoteClient);
        }        // 无间隔时间下单时并发执行
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            ExecutorService executor = Executors.newFixedThreadPool(orderCount);
            CountDownLatch latch = new CountDownLatch(orderCount);  // 初始化一个计数器，数量为任务数
            for (int i = 0; i < orderCount; i++) {
                final int orderId = i + 1;
                executor.submit(() -> {
                    try {
                        double ask = quoteClient.GetQuote(symbol).Ask;
                        double bid = quoteClient.GetQuote(symbol).Bid;
                        LocalDateTime nowdate = LocalDateTime.now();
                    try {
                        log.info("订单 " + orderId + ": 并发下单手数为 " + lotsPerOrder);
                        ordersends(traderId, account, quoteClient, symbol, type, oc, lotsPerOrder, orderId, ask, bid, nowdate, orderNo);
                        //推送信息
                    } finally {
                        latch.countDown();  // 每当一个任务完成，计数器减1
                    }
                    } catch (InvalidSymbolException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            // 等待所有任务完成
            try {
                latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                log.info("所有订单任务已完成");
                Thread.sleep(1000);
                ThreadPoolUtils.execute(()->{
                    updateSendOrder(traderId,orderNo);
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 关闭线程池，等待所有任务完成
            executor.shutdown();
        } else {
            // 有间隔时间的下单，依次执行并等待
            ThreadPoolUtils.execute(()-> {
                for (int i = 0; i < orderCount; i++) {
                    final int orderId = i + 1;
                    log.info("订单 " + orderId + ": 下单手数为 " + lotsPerOrder);
                    try {
                        Thread.sleep(interval);
                        double ask = quoteClient.GetQuote(symbol).Ask;
                        double bid = quoteClient.GetQuote(symbol).Bid;
                        LocalDateTime nowdate = LocalDateTime.now();
                        ordersends(traderId, account, quoteClient, symbol, type, oc, lotsPerOrder, orderId, ask, bid, nowdate, orderNo);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt(); // 恢复中断状态
                    } catch (InvalidSymbolException e) {
                        throw new RuntimeException(e);
                    }
                    updateSendOrder(traderId, orderNo);
                }
            });
        }
    }

    private void updateSendOrder(long traderId,String orderNo) {
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))){
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>)redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        }else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION+traderId,followSysmbolSpecificationEntityList);
        }
        Map<String, FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationEntityList.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i));

        FollowOrderSendEntity sendServiceOne = followOrderSendService.getOne(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getOrderNo, orderNo));
        if (ObjectUtil.isNotEmpty(sendServiceOne)){
            //查看下单数据
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo).isNotNull(FollowOrderDetailEntity::getOrderNo));
            //保存修改信息
            //保存真实下单数
            List<FollowOrderDetailEntity> trueList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
            sendServiceOne.setTotalNum(trueList.size());
            //保存真实下单手数
            sendServiceOne.setTrueSize(list.stream().map(FollowOrderDetailEntity::getSize).reduce(BigDecimal.ZERO,BigDecimal::add));
            sendServiceOne.setSuccessNum(list.size());
            sendServiceOne.setFailNum(sendServiceOne.getTotalNum()-list.size());
            sendServiceOne.setFinishTime(LocalDateTime.now());
            sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());

            followOrderSendService.updateById(sendServiceOne);
            //进行滑点分析
            list.parallelStream().forEach(o->{
                FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.get(o.getSymbol());
                BigDecimal hd;
                if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")){
                    //如果forex 并包含JPY 也是100
                    if (o.getSymbol().contains("JPY")){
                        hd=new BigDecimal("100");
                    }else {
                        hd=new BigDecimal("10000");
                    }
                }else {
                    //如果非forex 都是 100
                    hd=new BigDecimal("100");
                }
                long seconds = DateUtil.between(DateUtil.date(o.getResponseOpenTime()), DateUtil.date(o.getRequestOpenTime()), DateUnit.MS);
                o.setOpenTimeDifference((int) seconds);
                o.setOpenPriceSlip(o.getOpenPrice().subtract(o.getRequestOpenPrice()).multiply(hd).abs());
                followOrderDetailService.updateById(o);
            });
        }else {
            log.info("未查询到订单");
        }
    }

    private void ordersends(long traderId,String account,QuoteClient quoteClient,String symbol,Integer type,OrderClient oc,double lotsPerOrder,Integer orderId,double ask,double bid,LocalDateTime nowdate,String orderNo) {
        //插入订单详情记录
        FollowOrderDetailEntity followOrderDetailEntity=new FollowOrderDetailEntity();
        followOrderDetailEntity.setTraderId(traderId);
        followOrderDetailEntity.setAccount(account);
        followOrderDetailEntity.setSymbol(symbol);
        followOrderDetailEntity.setCreator(SecurityUser.getUserId());
        followOrderDetailEntity.setCreateTime(LocalDateTime.now());
        followOrderDetailEntity.setSendNo(orderNo);
        followOrderDetailEntity.setType(type);
        try {
            double asksub = quoteClient.GetQuote(symbol).Ask;
            double bidsub = quoteClient.GetQuote(symbol).Bid;
            Order order;
            if (type.equals(Op.Buy.getValue())){
                order=oc.OrderSend(symbol, Op.Buy,lotsPerOrder,asksub,0,0,0,"comment", Integer.valueOf(RandomStringUtil.generateNumeric(5)),null);
                followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(ask));
            }else {
                order=oc.OrderSend(symbol, Op.Sell,lotsPerOrder,bidsub, 0,0,0,"comment", Integer.valueOf(RandomStringUtil.generateNumeric(5)),null);
                followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(bid));
            }
            followOrderDetailEntity.setResponseOpenTime(LocalDateTime.now());
            log.info("订单"+orderId+"开仓时刻数据价格:"+order.OpenPrice+" 时间"+order.OpenTime);
            followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
            followOrderDetailEntity.setOpenTime(order.OpenTime);
            followOrderDetailEntity.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
            followOrderDetailEntity.setOrderNo(order.Ticket);
            followOrderDetailEntity.setRequestOpenTime(nowdate);
            followOrderDetailEntity.setSize(BigDecimal.valueOf(lotsPerOrder));
            followOrderDetailEntity.setSl(BigDecimal.valueOf(order.Swap));
            followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
            followOrderDetailEntity.setTp(BigDecimal.valueOf(order.TakeProfit));
            followOrderDetailService.save(followOrderDetailEntity);
        } catch (TimeoutException e) {
            log.info("下单超时");
            followOrderDetailEntity.setRemark("下单超时");
            followOrderDetailService.save(followOrderDetailEntity);
            throw new RuntimeException(e);
        } catch (ConnectException e) {
            log.info("连接异常");
            followOrderDetailEntity.setRemark("连接异常");
            followOrderDetailService.save(followOrderDetailEntity);
            throw new RuntimeException(e);
        } catch (TradeException e) {
            log.info("交易异常"+e);
            followOrderDetailEntity.setRemark("交易异常");
            followOrderDetailService.save(followOrderDetailEntity);
            throw new RuntimeException(e);
        } catch (InvalidSymbolException e) {
            log.info("无效symbol");
            followOrderDetailEntity.setRemark("无效symbol");
            followOrderDetailService.save(followOrderDetailEntity);
            throw new RuntimeException(e);
        }
    }

    // 示例 2: 每笔订单的下单数量为 区间内的随机值，总手数不超过 总手数，订单数量不固定
    public  void executeOrdersRandomTotalLots(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,double totalLots, BigDecimal minLots,BigDecimal maxLots,Integer interval,String orderNo) {
        Random rand = new Random();
        double totalPlacedLots = 0;
        int orderCount = 0;
        List<Double> orders = new ArrayList<>();

        while (totalPlacedLots < totalLots) {
            double randomLots = roundToTwoDecimal(minLots.doubleValue() + (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());
            if (totalPlacedLots + randomLots > totalLots) {
                break;
            }
            orders.add(randomLots);
            totalPlacedLots += randomLots;
            orderCount++;
        }
        log.info("执行随机下单操作，总手数不超过" + totalLots + "，实际下单订单数: " + orderCount);
        if (orderCount==0){
            //下单异常
            throw new ServerException("请重新下单");
        }
        vo.setTotalNum(orderCount);
        followOrderSendService.save(vo);
        executeOrder(interval,orderCount,orders,traderId,account,quoteClient,symbol,type,orderNo);
    }


    // 示例 3: 每笔订单的下单数量为 区间内的随机值，总订单数量固定，总手数不限
    public void executeOrdersRandomFixedCount(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,Integer orderCount, BigDecimal minLots,BigDecimal maxLots,Integer interval,String orderNo) {
        Random rand = new Random();
        List<Double> orders = new ArrayList<>();

        for (int i = 0; i < orderCount; i++) {
            double randomLots = roundToTwoDecimal(minLots.doubleValue() + (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());
            orders.add(randomLots);
        }
        log.info("执行固定订单数量随机下单操作，共" + orderCount + "笔订单。");
        followOrderSendService.save(vo);
        executeOrder(interval,orderCount,orders,traderId,account,quoteClient,symbol,type,orderNo);
    }

    // 示例 4: 每笔订单的下单数量为 区间内的随机值，总手数和订单数量都受限制
    public void executeOrdersRandomLimited(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,Integer orderCount,double totalLots,  BigDecimal minLots,BigDecimal maxLots,Integer interval,String orderNo) {
        Random rand = new Random();
        double totalPlacedLots = 0;  // 已下单的总手数
        int orderCountNum = 0;       // 已下单的订单数量
        List<Double> orders = new ArrayList<>();

        while (true) {
            // 根据 minLots 和 maxLots 之间的范围生成随机手数
            double randomLots = roundToTwoDecimal(minLots.doubleValue() + (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());

            // 如果下单的随机手数加上已下手数大于 totalLots，则直接下剩余的手数
            if (totalPlacedLots + randomLots > totalLots) {
                randomLots = roundToTwoDecimal(totalLots - totalPlacedLots);
            }

            // 将生成的随机手数加入订单列表
            orders.add(randomLots);
            totalPlacedLots += randomLots;
            orderCountNum++;

            // 判断是否达到了总手数或总订单数的限制
            if (totalPlacedLots >= totalLots || orderCountNum >= orderCount) {
                break;
            }
        }
        log.info("执行有限订单数量随机下单操作，总手数不超过" + totalLots + "，最大订单数: " + orderCount + "，实际下单订单数: " + orderCountNum);

        if (orderCountNum == 0) {
            // 下单异常，抛出异常
            throw new ServerException("请重新下单");
        }
        // 保存实际下单的订单数量
        vo.setTotalNum(orderCountNum);
        followOrderSendService.save(vo);

        // 执行订单操作
        executeOrder(interval, orderCountNum, orders, traderId, account, quoteClient, symbol, type, orderNo);
    }

    // 保留两位小数的方法
    public static double roundToTwoDecimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void executeOrder(Integer interval,Integer orderCount,List<Double> orders,long traderId,String account,QuoteClient quoteClient,String symbol,Integer type,String orderNo) {
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)){
            oc=quoteClient.OrderClient;
        }else {
            oc = new OrderClient(quoteClient);
        }
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            ExecutorService executor = Executors.newFixedThreadPool(orderCount);
            CountDownLatch latch = new CountDownLatch(orderCount);  // 初始化一个计数器，数量为任务数
            for (int i = 0; i < orderCount; i++) {
                final int orderId = i + 1;
                int finalI = i;
                executor.submit(() -> {
                    try {
                        double ask = quoteClient.GetQuote(symbol).Ask;
                        double bid = quoteClient.GetQuote(symbol).Bid;
                        LocalDateTime nowdate = LocalDateTime.now();
                        log.info("订单 " + orderId + ": 并发下单手数为 " + orders.get(finalI));
                        ordersends(traderId,account,quoteClient,symbol,type,oc,orders.get(finalI),orderId,ask,bid,nowdate,orderNo);
                    } catch (InvalidSymbolException e) {
                        throw new RuntimeException(e);
                    }finally {
                        latch.countDown();  // 每当一个任务完成，计数器减1
                    }
                });
            }
            // 等待所有任务完成
            try {
                latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                log.info("所有订单任务已完成");
                Thread.sleep(1000);
                ThreadPoolUtils.execute(()->{
                    updateSendOrder(traderId,orderNo);
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // 关闭线程池，等待所有任务完成
            executor.shutdown();
        }else {
            // 有间隔时间的下单，依次执行并等待
            ThreadPoolUtils.execute(()-> {
                for (int i = 0; i < orderCount; i++) {
                    final int orderId = i + 1;
                    log.info("订单 " + orderId + ": 下单手数为 " + orders.get(i));
                    try {
                        // 将 interval 从秒转换为毫秒
                        Thread.sleep(interval);
                        double ask = quoteClient.GetQuote(symbol).Ask;
                        double bid = quoteClient.GetQuote(symbol).Bid;
                        LocalDateTime nowdate = LocalDateTime.now();
                        ordersends(traderId, account, quoteClient, symbol, type, oc, orders.get(i), orderId, ask, bid, nowdate, orderNo);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Thread.currentThread().interrupt(); // 恢复中断状态
                    } catch (InvalidSymbolException e) {
                        throw new RuntimeException(e);
                    }
                    updateSendOrder(traderId, orderNo);
                }
            });
        }
    }

}