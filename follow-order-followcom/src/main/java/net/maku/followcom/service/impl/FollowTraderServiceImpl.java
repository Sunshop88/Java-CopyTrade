package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowOrderDetailConvert;
import net.maku.followcom.convert.FollowSysmbolSpecificationConvert;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.dao.FollowTraderDao;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SpringContextUtils;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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
    @Autowired
    @Qualifier(value = "commonThreadPool")
    private ExecutorService commonThreadPool;

    @Override
    public PageResult<FollowTraderVO> page(FollowTraderQuery query) {
        IPage<FollowTraderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowTraderVO> followTraderVOS = FollowTraderConvert.INSTANCE.convertList(page.getRecords());
        followTraderVOS.parallelStream().forEach(o->{
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER+o.getId()))){
                FollowRedisTraderVO followRedisTraderVO =(FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + o.getId());
                o.setBalance(followRedisTraderVO.getBalance());
                o.setEuqit(followRedisTraderVO.getEuqit());
                o.setFreeMargin(followRedisTraderVO.getFreeMargin());
                o.setMarginProportion(followRedisTraderVO.getMarginProportion());
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getTotal())){
                    o.setTotal(followRedisTraderVO.getTotal());
                }else {
                    o.setTotal(0);
                }
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getBuyNum())){
                    o.setBuyNum(followRedisTraderVO.getBuyNum());
                }else {
                    o.setBuyNum(0);
                }
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getSellNum())){
                    o.setSellNum(followRedisTraderVO.getSellNum());
                }else {
                    o.setSellNum(0);
                }
            }
        });
        return new PageResult<>(followTraderVOS, page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderEntity> getWrapper(FollowTraderQuery query){
        LambdaQueryWrapper<FollowTraderEntity> wrapper = Wrappers.lambdaQuery();
        //查询指定VPS下的账号
        wrapper.eq(FollowTraderEntity::getDeleted,query.getDeleted());
        //根据vps地址查询
        wrapper.eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST);
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
        FollowVpsEntity followVpsEntity = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress,FollowConstant.LOCAL_HOST));
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
        //判断是否正在下单
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND+vo.getTraderId()))) {
            return false;
        }
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
        //只有间隔会创建下单标识
        if (vo.getIntervalTime()!=0){
            redisCache.set(Constant.TRADER_SEND+vo.getTraderId(),1);
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
        if (ObjectUtil.isNotEmpty(query.getTraderId())){
            wrapper.eq(FollowOrderDetailEntity::getTraderId,query.getTraderId());
        }

        if (ObjectUtil.isNotEmpty(query.getTraderIdList())){
            wrapper.in(FollowOrderDetailEntity::getTraderId,query.getTraderIdList());
        }
        //滑点分析筛选成功的数据
        if (query.getFlag().equals(CloseOrOpenEnum.OPEN.getValue())){
            wrapper.isNotNull(FollowOrderDetailEntity::getOpenTime);
        }
        if (ObjectUtil.isNotEmpty(query.getStartTime())&&ObjectUtil.isNotEmpty(query.getEndTime())){
            wrapper.ge(FollowOrderDetailEntity::getOpenTime, query.getStartTime());  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getOpenTime, query.getEndTime());    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getCloseStartTime())&&ObjectUtil.isNotEmpty(query.getCloseEndTime())){
            wrapper.ge(FollowOrderDetailEntity::getCloseTime, query.getCloseStartTime());  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getCloseTime, query.getCloseEndTime());    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getSendNo())){
            wrapper.eq(FollowOrderDetailEntity::getSendNo,query.getSendNo());
        }
        if (ObjectUtil.isNotEmpty(query.getOrderNo())){
            wrapper.eq(FollowOrderDetailEntity::getOrderNo,query.getOrderNo());
        }
        if (ObjectUtil.isNotEmpty(query.getSymbol())){
            wrapper.like(FollowOrderDetailEntity::getSymbol,query.getSymbol());
        }
        if (ObjectUtil.isNotEmpty(query.getPlacedType())){
            wrapper.eq(FollowOrderDetailEntity::getPlacedType,query.getPlacedType());
        }
        if (ObjectUtil.isNotEmpty(query.getAccount())){
            wrapper.eq(FollowOrderDetailEntity::getAccount,query.getAccount());
        }
        wrapper.orderByDesc(FollowOrderDetailEntity::getCreateTime);
        Page<FollowOrderDetailEntity> page = new Page<>(query.getPage(), query.getLimit());
        Page<FollowOrderDetailEntity> pageOrder = followOrderDetailService.page(page, wrapper);
        return  new PageResult<>(FollowOrderDetailConvert.INSTANCE.convertList(pageOrder.getRecords()), pageOrder.getTotal());
    }

    @Override
    public boolean orderClose(FollowOrderCloseVO vo,QuoteClient quoteClient) {
        //判断是否正在平仓
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE+vo.getTraderId()))) {
            return false;
        }
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
        if (ObjectUtil.isNotEmpty(vo.getOrderNo())){
            //指定平仓
            FollowOrderDetailEntity detailServiceOne = followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, vo.getOrderNo()));
            if (ObjectUtil.isNotEmpty(detailServiceOne)){
                updateCloseOrder(detailServiceOne, quoteClient, oc);
                ThreadPoolUtils.execute(()->{
                    //进行平仓滑点分析
                    updateCloseSlip(vo.getTraderId(), vo.getSymbol());
                });
            }else {
                try{
                    if (ObjectUtil.isEmpty(quoteClient.GetQuote(vo.getSymbol()))){
                        //订阅
                        quoteClient.Subscribe(vo.getSymbol());
                    }
                    //非平台平仓
                    double  bid = quoteClient.GetQuote(vo.getSymbol()).Bid;
                    double  ask = quoteClient.GetQuote(vo.getSymbol()).Ask;
                    if (vo.getType()==Op.Buy.getValue()){
                        oc.OrderClose(vo.getSymbol(), vo.getOrderNo(), vo.getSize(), bid, 0);
                    }else {
                        oc.OrderClose(vo.getSymbol(),  vo.getOrderNo(),vo.getSize(), ask, 0);
                    }
                } catch (InvalidSymbolException | TimeoutException | ConnectException | TradeException e) {
                    log.info("平仓出错"+e.getMessage());
                    throw new RuntimeException(e);
                }
            }
            return true;
        }
        List<Integer> orderActive;
        List<OrderActiveInfoVO> orderActiveInfoVOS = List.of();
        //获取所有正在持仓订单
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_ACTIVE + vo.getTraderId()))){
            orderActiveInfoVOS = (List<OrderActiveInfoVO>) redisCache.get(Constant.TRADER_ACTIVE + vo.getTraderId());
        }
        if (vo.getFlag().equals(CloseOrOpenEnum.OPEN.getValue())){
            //全平
            orderActive=orderActiveInfoVOS.stream().map(o->o.getOrderNo()).collect(Collectors.toList());
            list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, vo.getTraderId()).isNotNull(FollowOrderDetailEntity::getOpenTime).isNull(FollowOrderDetailEntity::getCloseTime).orderByAsc(FollowOrderDetailEntity::getOpenTime));
            // 提取 list 中的订单号
            List<Integer> listOrderNos = list.stream().map(FollowOrderDetailEntity::getOrderNo).collect(Collectors.toList());
            log.info("持仓数量{},平台持仓数量{}",orderActive.size(),listOrderNos.size());
            orderActive.retainAll(listOrderNos);
            orderCount=orderActive.size();
            log.info("可平仓数量{}",orderActive.size());
        }else {
            //指定平仓
            if (vo.getType()==2){
                //平仓buy和sell
                orderActive=orderActiveInfoVOS.stream().filter(o->o.getSymbol().equals(vo.getSymbol())).map(o->o.getOrderNo()).collect(Collectors.toList());
            }else {
                orderActive=orderActiveInfoVOS.stream().filter(o->o.getSymbol().equals(vo.getSymbol())&&o.getType().equals(Op.forValue(vo.getType()).name())).map(o->o.getOrderNo()).collect(Collectors.toList());
            }
            orderCount = vo.getNum();
            LambdaQueryWrapper<FollowOrderDetailEntity> followOrderDetailw = new LambdaQueryWrapper<>();
            followOrderDetailw.eq(FollowOrderDetailEntity::getTraderId, vo.getTraderId())
                    .eq(FollowOrderDetailEntity::getSymbol,vo.getSymbol()).isNotNull(FollowOrderDetailEntity::getOpenTime)
                    .isNull(FollowOrderDetailEntity::getCloseTime).orderByAsc(FollowOrderDetailEntity::getOpenTime);
            if (!vo.getType().equals(2)){
                followOrderDetailw.eq(FollowOrderDetailEntity::getType,vo.getType());
            }
            list = followOrderDetailService.list(followOrderDetailw);
            // 提取 list 中的订单号
            List<Integer> listOrderNos = list.stream().map(FollowOrderDetailEntity::getOrderNo).collect(Collectors.toList());
            log.info("持仓数量{},平台持仓数量{}",orderActive.size(),listOrderNos.size());
            orderActive.retainAll(listOrderNos);
            log.info("可平仓数量{}",orderActive.size());
            if (orderCount>orderActive.size()){
                //不超过可平仓总数
                orderCount=orderActive.size();
            }
        }
        if (ObjectUtil.isEmpty(orderCount)||orderCount==0||list.size()==0||orderActive.size()==0){
            throw new ServerException(vo.getAccount()+"暂无可平仓订单");
        }
        // 无间隔时间下单时并发执行
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            commonThreadPool.execute(()-> {
                CountDownLatch latch = new CountDownLatch(orderActive.size());  // 初始化一个计数器，数量为任务数
                for (int i = 0; i < orderActive.size(); i++) {
                    //平仓数据处理
                    try {
                        int finalI = i;
                        ThreadPoolUtils.execute(() -> {
                            //判断是否存在指定数据
                            updateCloseOrder(followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo,orderActive.get(finalI))), quoteClient, oc);
                        });
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }finally {
                        latch.countDown();  // 每当一个任务完成，计数器减1
                    }
                }
                // 等待所有任务完成
                ThreadPoolUtils.execute(()-> {
                    try {
                        latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                        log.info("所有平仓任务已完成");
                        //进行平仓滑点分析
                        updateCloseSlip(vo.getTraderId(),vo.getSymbol());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            });

        } else {
            //只有间隔会创建平仓标识
            if (vo.getIntervalTime()!=0){
                redisCache.set(Constant.TRADER_CLOSE+vo.getTraderId(),1);
            }
            // 有间隔时间的下单，依次执行并等待
            commonThreadPool.execute(()-> {
                CountDownLatch latch = new CountDownLatch(orderActive.size());  // 初始化一个计数器，数量为任务数
                for (int i = 0; i < orderActive.size(); i++) {
                    if (ObjectUtil.isEmpty(redisCache.get(Constant.TRADER_CLOSE+vo.getTraderId()))||redisCache.get(Constant.TRADER_CLOSE+vo.getTraderId()).equals(1)) {
                        try {
                            int finalI = i;
                            ThreadPoolUtils.execute(()-> {
                                updateCloseOrder(followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo,orderActive.get(finalI))), quoteClient, oc);
                                //进行平仓滑点分析
                                updateCloseSlip(vo.getTraderId(), vo.getSymbol());
                            });
                            Thread.sleep(interval);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            latch.countDown();  // 每当一个任务完成，计数器减1
                        }
                    }else {
                        latch.countDown();
                    }
                }
                // 等待所有任务完成
                ThreadPoolUtils.execute(()-> {
                    try {
                        latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                        log.info("所有间隔平仓任务已完成");
                        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE+vo.getTraderId()))){
                            redisCache.delete(Constant.TRADER_CLOSE+vo.getTraderId());
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
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
            if (BigDecimal.valueOf(quoteClient.AccountMargin()).compareTo(BigDecimal.ZERO)!=0){
                entity.setMarginProportion(BigDecimal.valueOf(quoteClient.AccountEquity()).divide(BigDecimal.valueOf(quoteClient.AccountMargin()),4, RoundingMode.HALF_UP));
            }else {
                entity.setMarginProportion(BigDecimal.ZERO);
            }
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

    @Override
    public TraderOverviewVO traderOverview() {
        //查看所有账号
        TraderOverviewVO traderOverviewVO = new TraderOverviewVO();
        List<FollowTraderEntity> list = this.list();
        traderOverviewVO.setTraderTotal(list.size());
        Integer total=0;
        double buyNum=0;
        double sellNum=0;
        for (FollowTraderEntity traderEntity:list){
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER+traderEntity.getId()))){
                FollowRedisTraderVO followRedisTraderVO =(FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + traderEntity.getId());
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getTotal())){
                    total+=followRedisTraderVO.getTotal();
                }
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getBuyNum())){
                    buyNum+=followRedisTraderVO.getBuyNum();
                }
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getSellNum())){
                    sellNum+=followRedisTraderVO.getSellNum();
                }
            }
        }
        traderOverviewVO.setOrderTotal(total);
        traderOverviewVO.setBuyNum(buyNum);
        traderOverviewVO.setSellNum(sellNum);
        return traderOverviewVO;
    }

    @Override
    public Boolean stopOrder(Integer type,String traderId) {
        if (type==0){
            //停止下单
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND+traderId))){
                redisCache.set(Constant.TRADER_SEND+traderId,2);
            }else {
                return false;
            }
        }else {
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE+traderId))) {
                redisCache.set(Constant.TRADER_CLOSE + traderId, 2);
            }else {
                return false;
            }
        }
        return true;
    }

    private void updateCloseSlip(long traderId,String symbol) {
        LambdaQueryWrapper<FollowOrderDetailEntity> followLambdaQueryWrapper = new LambdaQueryWrapper<>();
        followLambdaQueryWrapper.eq(FollowOrderDetailEntity::getTraderId, traderId)
                .isNotNull(FollowOrderDetailEntity::getClosePrice)
                .isNull(FollowOrderDetailEntity::getClosePriceSlip);
        //查询需要滑点分析的数据 有平仓价格但是无平仓滑点
        if (ObjectUtil.isNotEmpty(symbol)){
            followLambdaQueryWrapper.eq(FollowOrderDetailEntity::getSymbol, symbol);
        }
        List<FollowOrderDetailEntity> list=followOrderDetailService.list(followLambdaQueryWrapper);
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
        //开始平仓
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
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(symbol))){
                //订阅
                quoteClient.Subscribe(symbol);
            }
            double  bid = quoteClient.GetQuote(symbol).Bid;
            double  ask = quoteClient.GetQuote(symbol).Ask;
            LocalDateTime nowdate = LocalDateTime.now();
            log.info("平仓信息{},{},{},{},{}",symbol,orderNo,followOrderDetailEntity.getSize(),bid,ask);
            Order orderResult;
            if (followOrderDetailEntity.getType()==Op.Buy.getValue()){
                orderResult = oc.OrderClose(symbol, orderNo, followOrderDetailEntity.getSize().doubleValue(), 0.0, 0);
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(bid));
            }else {
                orderResult = oc.OrderClose(symbol, orderNo,followOrderDetailEntity.getSize().doubleValue(), 0.0, 0);
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(ask));
            }
            log.info("订单 " + orderNo + ": 平仓 "+orderResult);
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
            log.info("平仓出错"+e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // 示例 1: 每笔订单的下单数量为 总手数/订单数量
    public void executeOrdersFixedQuantity(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,double totalLots, int orderCount,Integer interval,String orderNo) {
        double lotsPerOrder = roundToTwoDecimal(totalLots / orderCount);
        //判断是否超过总手数
        log.info("执行固定订单数量的下单操作，共" + orderCount + "笔订单，每笔手数: " + lotsPerOrder);

        List<Double> orders = new ArrayList<>();
        double accumulatedLots = 0.0; // 累计手数
        int remaining=0;
        for (int i=0;i<orderCount;i++){
            if (accumulatedLots + lotsPerOrder > totalLots) {
                lotsPerOrder = totalLots - accumulatedLots; // 调整为剩余的手数
            }
            // 如果是最后一笔订单，确保精确匹配总手数
            if (i == orderCount - 1) {
                lotsPerOrder = totalLots - accumulatedLots; // 最后一笔直接分配剩余手数
            }
            lotsPerOrder = Math.round(lotsPerOrder * 100) / 100.0; // 保留两位小数
            orders.add(lotsPerOrder);
            if (accumulatedLots >= totalLots) {
                remaining = orderCount - i;  // 剩余未执行的任务数
                break;
            }
            accumulatedLots=accumulatedLots+lotsPerOrder;
        }
        orderCount=orderCount-remaining;
        vo.setTotalNum(orderCount);
        followOrderSendService.save(vo);
        //删除缓存
        redisCache.delete(Constant.TRADER_ORDER+traderId);
        executeOrder(interval,orderCount,orders,traderId,account,quoteClient,symbol,type,orderNo,vo.getPlacedType());
    }

    private void updateSendOrder(long traderId,String orderNo,Integer flag) {
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
            //查看下单所有数据
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
            //保存修改信息
            //保存真实下单数
//            List<FollowOrderDetailEntity> trueList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
//            sendServiceOne.setTotalNum(trueList.size());
            //保存真实下单手数
            sendServiceOne.setTrueSize(list.stream().filter(o->ObjectUtil.isNotEmpty(o.getOpenTime())).map(FollowOrderDetailEntity::getSize).reduce(BigDecimal.ZERO,BigDecimal::add));
            sendServiceOne.setSuccessNum((int)list.stream().filter(o->ObjectUtil.isNotEmpty(o.getOpenTime())).count());
            sendServiceOne.setFailNum((int)list.stream().filter(o->ObjectUtil.isNotEmpty(o.getRemark())).count());
            if (flag==1){
                //间隔下单判断
                if (sendServiceOne.getTotalNum()==list.size()){
                    sendServiceOne.setFinishTime(LocalDateTime.now());
                    sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
                }
            }else {
                //同步下单直接结束
                sendServiceOne.setFinishTime(LocalDateTime.now());
                sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
            }

            followOrderSendService.updateById(sendServiceOne);
            //删除缓存
            redisCache.delete(Constant.TRADER_ORDER+traderId);
            //进行滑点分析
            list.stream().filter(o->ObjectUtil.isNotEmpty(o.getOpenTime())).collect(Collectors.toList()).parallelStream().forEach(o->{
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

    private void ordersends(long traderId,String account,QuoteClient quoteClient,String symbol,Integer type,OrderClient oc,double lotsPerOrder,Integer orderId,double ask,double bid,LocalDateTime nowdate,String orderNo,Integer placedType) {
        //插入订单详情记录
        FollowOrderDetailEntity followOrderDetailEntity=new FollowOrderDetailEntity();
        followOrderDetailEntity.setTraderId(traderId);
        followOrderDetailEntity.setAccount(account);
        followOrderDetailEntity.setSymbol(symbol);
        followOrderDetailEntity.setCreator(SecurityUser.getUserId());
        followOrderDetailEntity.setCreateTime(LocalDateTime.now());
        followOrderDetailEntity.setSendNo(orderNo);
        followOrderDetailEntity.setType(type);
        followOrderDetailEntity.setPlacedType(placedType);
        //下单方式
        oc.PlacedType=PlacedType.forValue(placedType);
        try {
            double asksub = quoteClient.GetQuote(symbol).Ask;
            double bidsub = quoteClient.GetQuote(symbol).Bid;
            Order order;
            if (type.equals(Op.Buy.getValue())){
                order=oc.OrderSend(symbol, Op.Buy,lotsPerOrder,asksub,0,0,0,"", Integer.valueOf(RandomStringUtil.generateNumeric(5)),null);
                followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(ask));
            }else {
                order=oc.OrderSend(symbol, Op.Sell,lotsPerOrder,bidsub, 0,0,0,"", Integer.valueOf(RandomStringUtil.generateNumeric(5)),null);
                followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(bid));
            }
            followOrderDetailEntity.setResponseOpenTime(LocalDateTime.now());
            log.info("订单"+orderId+"开仓时刻数据价格:"+order.OpenPrice+" 时间"+order.OpenTime);
            followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
            followOrderDetailEntity.setOpenTime(DateUtil.toLocalDateTime(DateUtil.offsetHour(DateUtil.date(order.OpenTime),-8)));
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
            followOrderDetailEntity.setRemark("下单超时"+e.getMessage());
            followOrderDetailService.save(followOrderDetailEntity);
        } catch (ConnectException e) {
            log.info("连接异常");
            followOrderDetailEntity.setRemark("连接异常"+e.getMessage());
            followOrderDetailService.save(followOrderDetailEntity);
        } catch (TradeException e) {
            log.info("交易异常"+e);
            followOrderDetailEntity.setRemark("交易异常"+e.getMessage());
            followOrderDetailService.save(followOrderDetailEntity);
        } catch (InvalidSymbolException e) {
            log.info("无效symbol");
            followOrderDetailEntity.setRemark("无效symbol"+e.getMessage());
            followOrderDetailService.save(followOrderDetailEntity);
        }catch (RuntimeException e) {
            log.info("交易异常"+e);
            followOrderDetailEntity.setRemark("交易异常"+e.getMessage());
            followOrderDetailService.save(followOrderDetailEntity);
        }
    }

    // 示例 2: 每笔订单的下单数量为 区间内的随机值，总手数不超过 总手数，订单数量不固定
    public  void executeOrdersRandomTotalLots(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,double totalLots, BigDecimal minLots,BigDecimal maxLots,Integer interval,String orderNo) {
        Random rand = new Random();
        double totalPlacedLots = 0;
        List<Double> orders = new ArrayList<>();

        // 随机生成订单，直到接近 totalLots
        while (totalPlacedLots < totalLots) {
            double randomLots = roundToTwoDecimal(minLots.doubleValue() +
                    (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());

            // 检查是否会超出总手数，如果是，跳出循环
            if (totalPlacedLots + randomLots > totalLots) {
                break;
            }
            orders.add(randomLots);
            totalPlacedLots += randomLots;
        }

        // 计算总手数差值
        double remainingDiff = roundToTwoDecimal(totalLots - totalPlacedLots);
        if (remainingDiff > 0 && !orders.isEmpty()) {
            // 找到订单列表中最小的订单手数
            int minOrderIndex = 0;
            double minOrder = orders.get(0);
            for (int i = 1; i < orders.size(); i++) {
                if (orders.get(i) < minOrder) {
                    minOrder = orders.get(i);
                    minOrderIndex = i;
                }
            }
            // 将差值加到最小的订单上，确保总手数完全分配
            orders.set(minOrderIndex, roundToTwoDecimal(minOrder + remainingDiff));
        }
        int orderCount = orders.size();
        log.info("执行随机下单操作，总手数不超过 " + totalLots + "，实际下单订单数: " + orderCount);

        if (orderCount == 0) {
            // 若订单数为 0，抛出异常
            throw new ServerException("请重新下单");
        }

        vo.setTotalNum(orderCount);
        followOrderSendService.save(vo);
        redisCache.delete(Constant.TRADER_ORDER +traderId);
        executeOrder(interval,orderCount,orders,traderId,account,quoteClient,symbol,type,orderNo,vo.getPlacedType());
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
        redisCache.delete(Constant.TRADER_ORDER +traderId);
        executeOrder(interval,orderCount,orders,traderId,account,quoteClient,symbol,type,orderNo,vo.getPlacedType());
    }

    // 示例 4: 每笔订单的下单数量为 区间内的随机值，总手数和订单数量都受限制
    public void executeOrdersRandomLimited(FollowOrderSendVO vo,long traderId,String account,Integer type,QuoteClient quoteClient,String symbol,Integer orderCount,double totalLots,  BigDecimal minLots,BigDecimal maxLots,Integer interval,String orderNo) {
        Random rand = new Random();
        double totalPlacedLots = 0;  // 已下单的总手数
        int orderCountNum = 0;       // 已下单的订单数量
        List<Double> orders = new ArrayList<>();



        // 随机生成订单手数
        while (true) {
            // 在 minLots 和 maxLots 范围内生成随机手数
            double randomLots = roundToTwoDecimal(minLots.doubleValue() + (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());

            // 如果当前生成的手数加上已分配的手数超过 totalLots，则将剩余手数分配给当前订单
            if (totalPlacedLots + randomLots > totalLots) {
                randomLots = roundToTwoDecimal(totalLots - totalPlacedLots);
            }

            // 将生成的手数加入订单列表
            orders.add(randomLots);
            totalPlacedLots += randomLots;
            orderCountNum++;

            // 如果已分配的手数达到 totalLots 或下单数量达到 orderCount，则停止
            if (totalPlacedLots >= totalLots || orderCountNum >= orderCount) {
                break;
            }
        }

        // 检查并分配剩余手数，如果存在浮动精度问题
        double remainingLots = roundToTwoDecimal(totalLots - totalPlacedLots);
        if (remainingLots > 0) {
            double lotsToAddPerOrder = roundToTwoDecimal(remainingLots / orderCountNum);

            // 遍历订单，按比例分配剩余手数
            for (int i = 0; i < orders.size(); i++) {
                double updatedOrder = roundToTwoDecimal(orders.get(i) + lotsToAddPerOrder);
                orders.set(i, updatedOrder);
            }

            // 如果仍有剩余手数，将其均匀分配到所有订单中，防止遗漏
            remainingLots = roundToTwoDecimal(totalLots - orders.stream().mapToDouble(Double::doubleValue).sum());
            if (remainingLots > 0) {
                for (int i = 0; i < orders.size() && remainingLots > 0; i++) {
                    double updatedOrder = roundToTwoDecimal(orders.get(i) + remainingLots / orderCountNum);
                    orders.set(i, updatedOrder);
                    remainingLots -= remainingLots / orderCountNum;
                }
            }
        }
        // 最终确认总手数是否等于 totalLots
        double finalTotal = roundToTwoDecimal(orders.stream().mapToDouble(Double::doubleValue).sum());

        if (Math.abs(finalTotal - totalLots) > 0) {
            // 计算需要分配的差值
            double remainingDiff = roundToTwoDecimal(totalLots - finalTotal);

            // 将差值加到最小订单中
            int minOrderIndex = 0;
            double minOrder = orders.get(0);
            for (int i = 1; i < orders.size(); i++) {
                if (orders.get(i) < minOrder) {
                    minOrder = orders.get(i);
                    minOrderIndex = i;
                }
            }
            // 将差值加到最小订单中
            double updatedOrder = roundToTwoDecimal(orders.get(minOrderIndex) + remainingDiff);
            orders.set(minOrderIndex, updatedOrder);
        }
        log.info("执行有限订单数量随机下单操作，总手数不超过" + totalLots + "，最大订单数: " + orderCount + "，实际下单订单数: " + orderCountNum);

        if (orderCountNum == 0) {
            // 下单异常，抛出异常
            throw new ServerException("请重新下单");
        }
        // 保存实际下单的订单数量
        vo.setTotalNum(orderCountNum);
        followOrderSendService.save(vo);
        redisCache.delete(Constant.TRADER_ORDER +traderId);
        // 执行订单操作
        executeOrder(interval, orderCountNum, orders, traderId, account, quoteClient, symbol, type, orderNo,vo.getPlacedType());
    }

    // 保留两位小数的方法
    public static double roundToTwoDecimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void executeOrder(Integer interval,Integer orderCount,List<Double> orders,long traderId,String account,QuoteClient quoteClient,String symbol,Integer type,String orderNo,Integer placedType) {
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)){
            oc=quoteClient.OrderClient;
        }else {
            oc = new OrderClient(quoteClient);
        }
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            commonThreadPool.execute(()-> {
                CountDownLatch latch = new CountDownLatch(orderCount);  // 初始化一个计数器，数量为任务数
                for (int i = 0; i < orderCount; i++) {
                    final int orderId = i + 1;
                    int finalI = i;
                    ThreadPoolUtils.execute(() -> {
                        try {
                            double ask = quoteClient.GetQuote(symbol).Ask;
                            double bid = quoteClient.GetQuote(symbol).Bid;
                            LocalDateTime nowdate = LocalDateTime.now();
                            log.info("订单 " + orderId + ": 并发下单手数为 " + orders.get(finalI));
                            ordersends(traderId, account, quoteClient, symbol, type, oc, orders.get(finalI), orderId, ask, bid, nowdate, orderNo, placedType);
                        } catch (InvalidSymbolException e) {
                            log.info("订单交易异常" + traderId);
                        } finally {
                            latch.countDown();  // 每当一个任务完成，计数器减1
                        }
                    });
                }
                ThreadPoolUtils.execute(()->{
                    // 等待所有任务完成
                    try {
                        latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                        log.info("所有订单任务已完成");
                        updateSendOrder(traderId,orderNo,0);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            });
        }else {
            // 有间隔时间的下单，依次执行并等待
            commonThreadPool.execute(()-> {
                CountDownLatch latch = new CountDownLatch(orderCount);  // 初始化一个计数器，数量为任务数
                for (int i = 0; i < orderCount; i++) {
                    if (ObjectUtil.isEmpty(redisCache.get(Constant.TRADER_SEND+traderId))||redisCache.get(Constant.TRADER_SEND+traderId).equals(1)) {
                        int finalI = i;
                        ThreadPoolUtils.execute(() -> {
                            final int orderId = finalI + 1;
                            log.info("订单 " + orderId + ": 下单手数为 " + orders.get(finalI));
                            try {
                                // 将 interval 从秒转换为毫秒
                                double ask = quoteClient.GetQuote(symbol).Ask;
                                double bid = quoteClient.GetQuote(symbol).Bid;
                                LocalDateTime nowdate = LocalDateTime.now();
                                ordersends(traderId, account, quoteClient, symbol, type, oc, orders.get(finalI), orderId, ask, bid, nowdate, orderNo, placedType);
                                updateSendOrder(traderId, orderNo, 1);
                            } catch (InvalidSymbolException | NullPointerException e) {
                                log.info("订单交易异常&获取价格异常" + traderId + e.getMessage());
                                //直接结束订单
                                FollowOrderSendEntity sendServiceOne = followOrderSendService.getOne(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getOrderNo, orderNo));
                                //查看下单所有数据
                                List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
                                //保存真实下单手数
                                sendServiceOne.setTrueSize(list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).map(FollowOrderDetailEntity::getSize).reduce(BigDecimal.ZERO, BigDecimal::add));
                                sendServiceOne.setSuccessNum((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).count());
                                sendServiceOne.setFailNum((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getRemark())).count());
                                sendServiceOne.setFinishTime(LocalDateTime.now());
                                sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
                                followOrderSendService.updateById(sendServiceOne);
                                //删除缓存
                                redisCache.delete(Constant.TRADER_ORDER + traderId);
                            } finally {
                                latch.countDown();  // 每当一个任务完成，计数器减1
                            }
                        });
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }else {
                        latch.countDown();
                    }
                }
                ThreadPoolUtils.execute(()->{
                    // 等待所有任务完成
                    try {
                        latch.await();  // 这里会阻塞，直到计数器变为0，表示所有任务都已完成
                        log.info("所有间隔订单任务已完成");
                        //更新状态
                        FollowOrderSendEntity sendServiceOne = followOrderSendService.getOne(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getOrderNo, orderNo));
                        sendServiceOne.setFinishTime(LocalDateTime.now());
                        sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
                        followOrderSendService.updateById(sendServiceOne);
                        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND + traderId))){
                            redisCache.delete(Constant.TRADER_SEND + traderId);
                        }
                        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_ORDER + traderId))) {
                            redisCache.delete(Constant.TRADER_ORDER+traderId);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            });
        }
    }

}