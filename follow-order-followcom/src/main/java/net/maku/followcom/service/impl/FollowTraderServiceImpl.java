package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
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
import net.maku.followcom.enums.*;
import net.maku.followcom.query.DashboardAccountQuery;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.SymbolUtils;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.*;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import online.mtapi.mt4.*;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.Exception.TradeException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static online.mtapi.mt4.Op.Buy;
import static online.mtapi.mt4.Op.Sell;

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
    private final FollowVpsService followVpsService;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final FollowOrderSendService followOrderSendService;
    private final FollowOrderDetailService followOrderDetailService;
    private final RedisCache redisCache;
    private final FollowPlatformService followPlatformService;
    private final FollowOrderCloseService followOrderCloseService;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final CacheManager cacheManager;
    private final RedisUtil redisUtil;
    private final FollowVarietyService followVarietyService;


    @Autowired
    @Qualifier(value = "commonThreadPool")
    private ExecutorService commonThreadPool;

    @Override
    public PageResult<FollowTraderVO> page(FollowTraderQuery query) {
        IPage<FollowTraderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowTraderVO> followTraderVOS = FollowTraderConvert.INSTANCE.convertList(page.getRecords());
        Map<Long, List<FollowTraderSubscribeEntity>> traderSubscribes = new HashMap<>();
        //查询跟单状态是否开启，只有差策略账号的时候才做处理
        if (ObjectUtil.isNotEmpty(query.getType()) && query.getType() == TraderTypeEnum.MASTER_REAL.getType()) {
            List<Long> masterIds = followTraderVOS.stream().filter(o -> o.getType() == TraderTypeEnum.MASTER_REAL.getType()).map(FollowTraderVO::getId).toList();
            if (ObjectUtil.isNotEmpty(masterIds)) {
                List<FollowTraderSubscribeEntity> ls = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, masterIds).eq(FollowTraderSubscribeEntity::getFollowStatus, CloseOrOpenEnum.OPEN.getValue()));
                traderSubscribes = ls.stream().collect(Collectors.groupingBy(FollowTraderSubscribeEntity::getMasterId));
            }
        }
        Map<Long, List<FollowTraderSubscribeEntity>> finalTraderSubscribes = traderSubscribes;
//        followTraderVOS.parallelStream().forEach(o -> {
//            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER + o.getId()))) {
//                FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + o.getId());
//                o.setBalance(followRedisTraderVO.getBalance());
//                o.setEuqit(followRedisTraderVO.getEuqit());
//                o.setFreeMargin(followRedisTraderVO.getFreeMargin());
//                o.setMarginProportion(followRedisTraderVO.getMarginProportion());
//                o.setTotal(ObjectUtil.isNotEmpty(followRedisTraderVO.getTotal()) ? followRedisTraderVO.getTotal() : 0);
//                o.setBuyNum(ObjectUtil.isNotEmpty(followRedisTraderVO.getBuyNum()) ? followRedisTraderVO.getBuyNum() : 0);
//                o.setSellNum(ObjectUtil.isNotEmpty(followRedisTraderVO.getSellNum()) ? followRedisTraderVO.getSellNum() : 0);
//              //  Integer followStatus = ObjectUtil.isNotEmpty(finalTraderSubscribes.get(o.getId())) ? CloseOrOpenEnum.OPEN.getValue() : CloseOrOpenEnum.OPEN.getValue();
//               // o.setFollowStatus(followStatus);
//                o.setProfit(followRedisTraderVO.getProfit());
//
//            }
//        });
        return new PageResult<>(followTraderVOS, page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderEntity> getWrapper(FollowTraderQuery query) {
        LambdaQueryWrapper<FollowTraderEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowTraderEntity::getDeleted, query.getDeleted());
        //根据vps地址查询
        wrapper.eq(ObjectUtil.isNotEmpty(query.getServerIp()),FollowTraderEntity::getIpAddr, query.getServerIp());
        if (ObjectUtil.isNotEmpty(query.getAccount())) {
            wrapper.eq(FollowTraderEntity::getAccount, query.getAccount());
        }
        if (ObjectUtil.isNotEmpty(query.getTraderList())) {
            wrapper.in(FollowTraderEntity::getId, query.getTraderList());
        }
        if (ObjectUtil.isNotEmpty(query.getType())) {
            wrapper.eq(FollowTraderEntity::getType, query.getType());
        }
        //根据id进行降序
        wrapper.orderByDesc(FollowTraderEntity::getId);
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
        vo.setServerIp(FollowConstant.LOCAL_HOST);
        if (ObjectUtil.isEmpty(vo.getPlatform())) {
            throw new ServerException("服务商错误");
        }
        FollowPlatformEntity followPlatform = followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, vo.getPlatform()));
        if (ObjectUtil.isEmpty(followPlatform)) {
            throw new ServerException("暂无可用服务器商");
        }
        //查看是否已存在该账号
        FollowTraderEntity followTraderEntity = this.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerName,vo.getPlatform()).eq(FollowTraderEntity::getAccount, vo.getAccount()).eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST));
        if (ObjectUtil.isNotEmpty(followTraderEntity)) {
            if (followTraderEntity.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) &&  vo.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())) {
                throw new ServerException("策略账号，不允许跟单");
            } else {
                throw new ServerException((vo.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) ? "策略" : "跟单") + "账户已存在");
            }

        }
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);
        FollowVpsEntity followVpsEntity = followVpsService.getOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress, vo.getServerIp()).eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE.getType()));
        if (ObjectUtil.isEmpty(followVpsEntity)) {
            throw new ServerException("请先添加VPS");
        }
        entity.setTemplateId(vo.getTemplateId());
        entity.setPlatformId(followPlatform.getId().intValue());
        entity.setIpAddr(vo.getServerIp());
        entity.setServerId(followVpsEntity.getId());
        entity.setServerName(followVpsEntity.getName());
        entity.setCreator(SecurityUser.getUserId());
        entity.setCreateTime(LocalDateTime.now());
        entity.setFollowStatus(vo.getFollowStatus());
        try {
            baseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ServerException("账号已存在");
        }

        FollowTraderVO followTraderVO = FollowTraderConvert.INSTANCE.convert(entity);
        followTraderVO.setId(entity.getId());
        return followTraderVO;
    }

    private void addSysmbolSpecification(FollowTraderEntity entity, QuoteClient quoteClient) {
        Long userId = SecurityUser.getUserId();
        LocalDateTime localDateTime = LocalDateTime.now();
        ThreadPoolUtils.execute(() -> {
            try {
                String[] symbols = quoteClient.Symbols();
                Arrays.stream(symbols).forEach(o -> {
                    try {
                        SymbolInfo symbolInfo = quoteClient.GetSymbolInfo(o);
                        ConGroupSec conGroupSec = quoteClient.GetSymbolGroupParams(o);
                        FollowSysmbolSpecificationVO followSysmbolSpecificationVO = new FollowSysmbolSpecificationVO();
                        followSysmbolSpecificationVO.setTraderId(entity.getId());
                        followSysmbolSpecificationVO.setDigits(symbolInfo.Digits);
                        followSysmbolSpecificationVO.setContractSize(symbolInfo.ContractSize);
                        followSysmbolSpecificationVO.setLotStep(Double.valueOf(conGroupSec.lot_step)*0.01);
                        followSysmbolSpecificationVO.setMarginCurrency(symbolInfo.MarginCurrency);
                        followSysmbolSpecificationVO.setMaxLot(Double.valueOf(conGroupSec.lot_max)*0.01);
                        followSysmbolSpecificationVO.setProfitMode(symbolInfo.ProfitMode.name());
                        followSysmbolSpecificationVO.setMinLot(Double.valueOf(conGroupSec.lot_min)*0.01);
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
                    } catch (InvalidSymbolException |ConnectException e) {
                        log.error(entity.getId()+"添加品种规格异常"+o+"异常信息"+e.getMessage());
                    }
                });
                //查询改账号的品种规格
                List<FollowSysmbolSpecificationEntity> list = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, entity.getId()));
                redisCache.set(Constant.SYMBOL_SPECIFICATION + entity.getId(), list);
                Cache cache = cacheManager.getCache("followSymbolCache");
                if (cache != null) {
                    cache.evict(entity.getId()); // 移除指定缓存条目
                }
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
        Cache cache = cacheManager.getCache("followFollowCache");
        if (cache != null) {
            cache.evict(vo.getId()); // 修改指定缓存条目
        }
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
    public boolean orderSend(FollowOrderSendVO vo, QuoteClient quoteClient, FollowTraderVO followTraderVO, Integer contract) {
        //判断是否正在下单
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND + vo.getTraderId()))) {
            return false;
        }
        //查询券商和服务商
        FollowTraderEntity followTraderEntity = this.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getId, vo.getTraderId()));

        FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(this.get(vo.getTraderId()).getPlatformId().toString());
        //创建下单记录
        String orderNo = RandomStringUtil.generateNumeric(13);
        vo.setCreator(SecurityUser.getUserId());
        vo.setCreateTime(LocalDateTime.now());
        vo.setAccount(followTraderVO.getAccount());
        vo.setOrderNo(orderNo);
        vo.setPlatform(followTraderEntity.getPlatform());
        vo.setBrokeName(followPlatform.getBrokerName());
        vo.setIpAddr(followTraderEntity.getIpAddr());
        vo.setServerName(followTraderEntity.getServerName());
        vo.setServer(followTraderEntity.getPlatform());

        double pr = 1;
        if (contract != 0) {
            //查询合约手数比例
            List<FollowSysmbolSpecificationEntity> specificationServiceByTraderId = followSysmbolSpecificationService.getByTraderId(vo.getTraderId());
            FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationServiceByTraderId.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i)).get(vo.getSymbol());
            if (ObjectUtil.isNotEmpty(followSysmbolSpecificationEntity)) {
                log.info("对应合约值{}", followSysmbolSpecificationEntity.getContractSize());
                pr = (double) contract / followSysmbolSpecificationEntity.getContractSize();
            }
        }
        log.info("合约大小值{}", pr);
        //根据情况进行下单
        if (ObjectUtil.isNotEmpty(vo.getTotalSzie()) && ObjectUtil.isNotEmpty(vo.getTotalNum()) && ObjectUtil.isEmpty(vo.getStartSize()) && ObjectUtil.isEmpty(vo.getEndSize())) {
            //情况一  总手数+订单数量情况 填写 范围未填写
            executeOrdersFixedQuantity(vo.getIpAddr(), vo.getServerName(), BigDecimal.valueOf(pr), followTraderEntity.getPlatform(), followPlatform.getBrokerName(), vo, followTraderVO.getId(), followTraderVO.getAccount(), vo.getType(), quoteClient, vo.getSymbol(), vo.getTotalSzie().doubleValue(), vo.getTotalNum(), vo.getIntervalTime(), orderNo,vo.getRemark());
        } else if (ObjectUtil.isNotEmpty(vo.getTotalSzie()) && ObjectUtil.isEmpty(vo.getTotalNum()) && ObjectUtil.isNotEmpty(vo.getStartSize()) && ObjectUtil.isNotEmpty(vo.getEndSize())) {
            //情况二  区间随机下单，订单数量不固定，总手数不超过设定值
            executeOrdersRandomTotalLots(vo.getIpAddr(), vo.getServerName(), BigDecimal.valueOf(pr), followTraderEntity.getPlatform(), followPlatform.getBrokerName(), vo, followTraderVO.getId(), followTraderVO.getAccount(), vo.getType(), quoteClient, vo.getSymbol(), vo.getTotalSzie().doubleValue(), vo.getStartSize(), vo.getEndSize(), vo.getIntervalTime(), orderNo,vo.getRemark());
        } else if (ObjectUtil.isEmpty(vo.getTotalSzie()) && ObjectUtil.isNotEmpty(vo.getTotalNum()) && ObjectUtil.isNotEmpty(vo.getStartSize()) && ObjectUtil.isNotEmpty(vo.getEndSize())) {
            //情况三  区间随机下单，订单数量固定，总手数不限制
            executeOrdersRandomFixedCount(vo.getIpAddr(), vo.getServerName(), BigDecimal.valueOf(pr), followTraderEntity.getPlatform(), followPlatform.getBrokerName(), vo, followTraderVO.getId(), followTraderVO.getAccount(), vo.getType(), quoteClient, vo.getSymbol(), vo.getTotalNum(), vo.getStartSize(), vo.getEndSize(), vo.getIntervalTime(), orderNo,vo.getRemark());
        } else if (ObjectUtil.isNotEmpty(vo.getTotalSzie()) && ObjectUtil.isNotEmpty(vo.getTotalNum()) && ObjectUtil.isNotEmpty(vo.getStartSize()) && ObjectUtil.isNotEmpty(vo.getEndSize())) {
            //区间随机下单，总手数和订单数量都受限制
            executeOrdersRandomLimited(vo.getIpAddr(), vo.getServerName(), BigDecimal.valueOf(pr), followTraderEntity.getPlatform(), followPlatform.getBrokerName(), vo, followTraderVO.getId(), followTraderVO.getAccount(), vo.getType(), quoteClient, vo.getSymbol(), vo.getTotalNum(), vo.getTotalSzie(), vo.getStartSize(), vo.getEndSize(), vo.getIntervalTime(), orderNo,vo.getRemark());
        }
        //只有间隔会创建下单标识
        if (vo.getIntervalTime() != 0) {
            redisCache.set(Constant.TRADER_SEND + vo.getTraderId(), 1);
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


        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            wrapper.eq(FollowOrderDetailEntity::getTraderId, query.getTraderId());
        }

        if (ObjectUtil.isNotEmpty(query.getTraderIdList())) {
            wrapper.in(FollowOrderDetailEntity::getTraderId, query.getTraderIdList());
        }
        //滑点分析筛选成功的数据
        if (ObjectUtil.isNotEmpty(query.getFlag()) && query.getFlag().equals(CloseOrOpenEnum.OPEN.getValue())) {
            wrapper.isNotNull(FollowOrderDetailEntity::getOpenTime);
        }
        //查看是否为平仓详情数据
        if (ObjectUtil.isNotEmpty(query.getIsClose()) && query.getIsClose().equals(CloseOrOpenEnum.OPEN.getValue())) {
            wrapper.isNotNull(FollowOrderDetailEntity::getOpenTime);
            // 使用 lambdaQuery 的嵌套条件来正确分组逻辑
            wrapper.and(subWrapper ->
                    subWrapper.isNotNull(FollowOrderDetailEntity::getCloseTime)
                            .or()
                            .isNotNull(FollowOrderDetailEntity::getRemark)
            );
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (ObjectUtil.isNotEmpty(query.getStartTime()) && ObjectUtil.isNotEmpty(query.getEndTime())) {
            // 解析字符串为 LocalDateTime
            LocalDateTime startTime = LocalDateTime.parse(query.getStartTime(), formatter);
            LocalDateTime endTime = LocalDateTime.parse(query.getEndTime(), formatter);
            // 减去 8 小时
            LocalDateTime adjustedStartTime = startTime.minusHours(8);
            LocalDateTime adjustedEndTime = endTime.minusHours(8);
            wrapper.ge(FollowOrderDetailEntity::getOpenTime, adjustedStartTime);  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getOpenTime, adjustedEndTime);    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getRequestOpenTimeStart()) && ObjectUtil.isNotEmpty(query.getRequestOpenTimeEnd())) {
            // 解析字符串为 LocalDateTime
            LocalDateTime startTime = LocalDateTime.parse(query.getRequestOpenTimeStart(), formatter);
            LocalDateTime endTime = LocalDateTime.parse(query.getRequestOpenTimeEnd(), formatter);
            wrapper.ge(FollowOrderDetailEntity::getRequestOpenTime, startTime);  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getRequestOpenTime, endTime);    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getRequestCloseTimeStart()) && ObjectUtil.isNotEmpty(query.getRequestCloseTimeEnd())) {
            // 解析字符串为 LocalDateTime
            LocalDateTime startTime = LocalDateTime.parse(query.getRequestCloseTimeStart(), formatter);
            LocalDateTime endTime = LocalDateTime.parse(query.getRequestCloseTimeEnd(), formatter);
            wrapper.ge(FollowOrderDetailEntity::getRequestCloseTime, startTime);  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getRequestCloseTime, endTime);    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getCloseStartTime()) && ObjectUtil.isNotEmpty(query.getCloseEndTime())) {
            wrapper.ge(FollowOrderDetailEntity::getCloseTime, query.getCloseStartTime());  // 大于或等于开始时间
            wrapper.le(FollowOrderDetailEntity::getCloseTime, query.getCloseEndTime());    // 小于或等于结束时间
        }
        if (ObjectUtil.isNotEmpty(query.getServerName())) {
            wrapper.like(FollowOrderDetailEntity::getServerName, query.getServerName());
        }
        if (ObjectUtil.isNotEmpty(query.getMagical())) {
            wrapper.like(FollowOrderDetailEntity::getMagical, query.getMagical());
        }
        if (ObjectUtil.isNotEmpty(query.getCloseServerName())) {
            wrapper.like(FollowOrderDetailEntity::getCloseServerName, query.getCloseServerName());
        }
        if (ObjectUtil.isNotEmpty(query.getSendNo())) {
            wrapper.eq(FollowOrderDetailEntity::getSendNo, query.getSendNo());
        }
        if (ObjectUtil.isNotEmpty(query.getOrderNo())) {
            wrapper.like(FollowOrderDetailEntity::getOrderNo, query.getOrderNo());
        }
        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
            wrapper.like(FollowOrderDetailEntity::getSymbol, query.getSymbol());
        }
        if (ObjectUtil.isNotEmpty(query.getPlacedType())) {
            wrapper.eq(FollowOrderDetailEntity::getPlacedType, query.getPlacedType());
        }
        if (ObjectUtil.isNotEmpty(query.getAccount())) {
            wrapper.like(FollowOrderDetailEntity::getAccount, query.getAccount());
        }
        if (ObjectUtil.isNotEmpty(query.getBrokeName())) {
            String[] brokerName = query.getBrokeName().split(",");
            wrapper.in(FollowOrderDetailEntity::getBrokeName, brokerName);
        }
        if (ObjectUtil.isNotEmpty(query.getPlatform())) {
            String[] platform = query.getPlatform().split(",");
            wrapper.in(FollowOrderDetailEntity::getPlatform, platform);
        }
        if (ObjectUtil.isNotEmpty(query.getCloseId())) {
            wrapper.eq(FollowOrderDetailEntity::getCloseId, query.getCloseId());
        }

        wrapper.like(ObjectUtil.isNotEmpty(query.getSourceUser()), FollowOrderDetailEntity::getSourceUser, query.getSourceUser());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getIsExternal()), FollowOrderDetailEntity::getIsExternal, query.getIsExternal());
        wrapper.orderByDesc(FollowOrderDetailEntity::getId);
        Page<FollowOrderDetailEntity> page = new Page<>(query.getPage(), query.getLimit());
        Page<FollowOrderDetailEntity> pageOrder = followOrderDetailService.page(page, wrapper);
        //查询结算汇率
        // List<FollowOrderDetailEntity> records = pageOrder.getRecords();

        return new PageResult<>(FollowOrderDetailConvert.INSTANCE.convertList(pageOrder.getRecords()), pageOrder.getTotal());
    }

    private void handleOrder(QuoteClient quoteClient, OrderClient oc, FollowOrderSendCloseVO vo) {
        //指定平仓
        List<FollowOrderDetailEntity> detailServiceOnes = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, vo.getOrderNo()));
        if (ObjectUtil.isNotEmpty(detailServiceOnes)) {
            if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
                detailServiceOnes.forEach(detailServiceOne->{
                    updateCloseOrder(detailServiceOne, quoteClient, oc, null);
                    ThreadPoolUtils.execute(() -> {
                        //进行平仓滑点分析
                        updateCloseSlip(vo.getTraderId(), vo.getSymbol(), null, 2);
                    });
                });
            }else {
                detailServiceOnes.forEach(detailServiceOne->{
                    updateCloseOrder(detailServiceOne, quoteClient, oc, null);
                    //进行平仓滑点分析
                    updateCloseSlip(vo.getTraderId(), vo.getSymbol(), null, 2);
                });
            }
        } else {
            try {
                if (ObjectUtil.isEmpty(quoteClient.GetQuote(vo.getSymbol()))) {
                    //订阅
                    quoteClient.Subscribe(vo.getSymbol());
                }
                double bid =0;
                double ask =0;
                int loopTimes=1;
                QuoteEventArgs quoteEventArgs = null;
                while (quoteEventArgs == null && quoteClient.Connected()) {
                    quoteEventArgs = quoteClient.GetQuote(vo.getSymbol());
                    if (++loopTimes > 20) {
                        break;
                    } else {
                        Thread.sleep(50);
                    }
                }
                bid =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Bid:0;
                ask =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Ask:0;
                Order order = quoteClient.GetOpenedOrder(vo.getOrderNo());
                long start = System.currentTimeMillis();
                if (order.Type.getValue() == Buy.getValue()) {
                    oc.OrderClose(vo.getSymbol(), vo.getOrderNo(), vo.getSize(), bid, 0);
                } else {
                    oc.OrderClose(vo.getSymbol(), vo.getOrderNo(), vo.getSize(), ask, 0);
                }
                long end = System.currentTimeMillis();
                log.info("MT4平仓时间差 订单:"+order.Ticket+"内部时间差:"+order.sendTimeDifference+"外部时间差:"+(end-start));
            } catch (Exception e) {
                log.error(vo.getOrderNo()+"平仓出错" + e.getMessage());
            }
        }
    }

    @Override
    public boolean orderClose(FollowOrderSendCloseVO vo, QuoteClient quoteClient) {
        //判断是否正在平仓
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE + vo.getTraderId()))) {
            return false;
        }
        //登录
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)) {
            oc = quoteClient.OrderClient;
        } else {
            oc = new OrderClient(quoteClient);
        }
        Integer interval = vo.getIntervalTime();
        Integer orderCount;
        //查看目前未平仓订单
        List<FollowOrderDetailEntity> list;
        //判断是否全平,全平走这里逻辑，处理完成退出
        if (vo.getIsCloseAll() == TraderRepairEnum.CLOSE.getType()) {
            //查找mt4订单
            List<Order> openedOrders ;
            if (ObjectUtil.isNotEmpty(vo.getProfitOrLoss())){
                if (vo.getProfitOrLoss().equals(ProfitOrLossEnum.Profit.getValue())){
                    openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order -> order.Profit>0&&(order.Type == Buy || order.Type == Sell)).collect(Collectors.toList());
                }else {
                    openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order ->  order.Profit<0&&(order.Type == Buy || order.Type == Sell)).collect(Collectors.toList());
                }
            }else {
                openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
            }
            if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
                openedOrders.forEach(order -> {
                    ThreadPoolUtils.execute(() -> {
                        FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
                        BeanUtils.copyProperties(vo, followOrderSendCloseVO);
                        followOrderSendCloseVO.setOrderNo(order.Ticket);
                        followOrderSendCloseVO.setSymbol(order.Symbol);
                        followOrderSendCloseVO.setSize(order.Lots);
                        handleOrder(quoteClient, oc, followOrderSendCloseVO);
                    });
                });
            }else {
                openedOrders.forEach(order -> {
                    FollowOrderSendCloseVO followOrderSendCloseVO = new FollowOrderSendCloseVO();
                    BeanUtils.copyProperties(vo, followOrderSendCloseVO);
                    followOrderSendCloseVO.setOrderNo(order.Ticket);
                    followOrderSendCloseVO.setSymbol(order.Symbol);
                    followOrderSendCloseVO.setSize(order.Lots);
                    handleOrder(quoteClient, oc, followOrderSendCloseVO);
                });
            }
            return true;
        }

        if (ObjectUtil.isNotEmpty(vo.getOrderNo())) {
            //指定平仓
            handleOrder(quoteClient, oc, vo);
            return true;
        }
        //查询券商和服务商
        FollowTraderEntity followTraderEntity = this.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getId, vo.getTraderId()));
        FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(followTraderEntity.getPlatformId().toString());
        //创建平仓记录
        FollowOrderCloseEntity followOrderCloseEntity = new FollowOrderCloseEntity();
        followOrderCloseEntity.setTraderId(vo.getTraderId());
        followOrderCloseEntity.setIntervalTime(vo.getIntervalTime());
        followOrderCloseEntity.setAccount(vo.getAccount());
        followOrderCloseEntity.setServer(followTraderEntity.getPlatform());
        followOrderCloseEntity.setBrokeName(followPlatform.getBrokerName());
        followOrderCloseEntity.setIpAddr(followTraderEntity.getIpAddr());
        followOrderCloseEntity.setServerName(followTraderEntity.getServerName());
        followOrderCloseEntity.setCreateTime(LocalDateTime.now());
        followOrderCloseEntity.setCreator(SecurityUser.getUserId());
        List<Integer> orderActive;
        //获取所有正在持仓订单 顺序
        //所有持仓
        List<Order> openedOrders = Arrays.stream(quoteClient.GetOpenedOrders()).filter(order -> order.Type == Buy || order.Type == Sell).collect(Collectors.toList());
        List<OrderActiveInfoVO> orderActiveInfoVOS = converOrderActive(openedOrders, vo.getAccount());

        if (vo.getFlag().equals(CloseOrOpenEnum.OPEN.getValue())) {
            followOrderCloseEntity.setType(TraderCloseEnum.BUYANDSELL.getType());
            //全平
            orderActive = orderActiveInfoVOS.stream().sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime)).map(o -> o.getOrderNo()).collect(Collectors.toList());
            list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, vo.getTraderId())
                    .eq(FollowOrderDetailEntity::getIpAddr,FollowConstant.LOCAL_HOST).isNotNull(FollowOrderDetailEntity::getOpenTime)
                    .eq(FollowOrderDetailEntity::getIsExternal,CloseOrOpenEnum.CLOSE.getValue()).isNull(FollowOrderDetailEntity::getCloseTime).orderByAsc(FollowOrderDetailEntity::getOpenTime));
            // 提取 list 中的订单号
            List<Integer> listOrderNos = list.stream().map(FollowOrderDetailEntity::getOrderNo).collect(Collectors.toList());
            log.info("持仓数量{},平台持仓数量{}", orderActive.size(), listOrderNos.size());
            orderActive.retainAll(listOrderNos);
            orderCount = orderActive.size();
            log.info("可平仓数量{}", orderActive.size());
            followOrderCloseEntity.setTotalNum(orderActive.size());
        } else {
            followOrderCloseEntity.setType(vo.getType());
            followOrderCloseEntity.setSymbol(vo.getSymbol());
            //指定平仓
            if (vo.getType().equals(TraderCloseEnum.BUYANDSELL.getType())) {
                //平仓buy和sell
                orderActive = orderActiveInfoVOS.stream().filter(o -> o.getSymbol().equals(vo.getSymbol())).sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime)).map(o -> o.getOrderNo()).collect(Collectors.toList());
            } else {
                orderActive = orderActiveInfoVOS.stream().filter(o -> o.getSymbol().equals(vo.getSymbol()) && o.getType().equals(Op.forValue(vo.getType()).name())).sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime)).map(o -> o.getOrderNo()).collect(Collectors.toList());
            }
            orderCount =ObjectUtil.isNotEmpty(vo.getNum())?vo.getNum():orderActive.size();
            LambdaQueryWrapper<FollowOrderDetailEntity> followOrderDetailw = new LambdaQueryWrapper<>();
            followOrderDetailw.eq(FollowOrderDetailEntity::getTraderId, vo.getTraderId())
                    .eq(FollowOrderDetailEntity::getSymbol, vo.getSymbol()).isNotNull(FollowOrderDetailEntity::getOpenTime)
                    .eq(FollowOrderDetailEntity::getIsExternal,CloseOrOpenEnum.CLOSE.getValue())
                    .isNull(FollowOrderDetailEntity::getCloseTime).orderByAsc(FollowOrderDetailEntity::getOpenTime);
            if (!vo.getType().equals(2)) {
                followOrderDetailw.eq(FollowOrderDetailEntity::getType, vo.getType());
            }
            list = followOrderDetailService.list(followOrderDetailw);
            // 提取 list 中的订单号
            List<Integer> listOrderNos = list.stream().map(FollowOrderDetailEntity::getOrderNo).collect(Collectors.toList());
            log.info("需要平仓数量{},持仓数量{},平台持仓数量{}", orderCount, orderActive.size(), listOrderNos.size());
            orderActive.retainAll(listOrderNos);
            log.info("可平仓数量{}", orderActive.size());
            if (orderCount > orderActive.size()) {
                //不超过可平仓总数
                orderCount = orderActive.size();
            }
            followOrderCloseEntity.setTotalNum(orderCount);
        }
        if (ObjectUtil.isEmpty(orderCount) || orderCount == 0 || list.size() == 0 || orderActive.size() == 0) {
            throw new ServerException((ObjectUtil.isNotEmpty(vo.getAccount()) ? vo.getAccount() : "") + "暂无可平仓订单");
        }
        followOrderCloseService.save(followOrderCloseEntity);
        // 无间隔时间下单时并发执行
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            Integer finalOrderCount = orderCount;
            ThreadPoolUtils.getExecutor().execute(() -> {
                if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (int i = 0; i < finalOrderCount; i++) {
                        //平仓数据处理
                        int finalI = i;
                        CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                            try {
                                //判断是否存在指定数据
                                updateCloseOrder(followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderActive.get(finalI)).eq(FollowOrderDetailEntity::getTraderId,followTraderEntity.getId())), quoteClient, oc, followOrderCloseEntity);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, ThreadPoolUtils.getExecutor());
                        futures.add(orderFuture);
                    }
                    // 使用 CompletableFuture.allOf 等待所有订单任务完成
                    CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    // 当所有订单任务完成后，执行更新操作
                    allOrdersCompleted.thenRun(() -> {
                        try {
                            log.info("所有平仓任务已完成");
                            updateCloseSlip(vo.getTraderId(), vo.getSymbol(), followOrderCloseEntity, 0);
                        } catch (Exception e) {
                            log.error("平仓任务出错", e);
                        }
                    });
                }else {
                    for (int i = 0; i < finalOrderCount; i++) {
                        //平仓数据处理
                        int finalI = i;
                        try {
                            //判断是否存在指定数据
                            updateCloseOrder(followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderActive.get(finalI)).eq(FollowOrderDetailEntity::getTraderId,followTraderEntity.getId())), quoteClient, oc, followOrderCloseEntity);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    try {
                        log.info("所有平仓任务已完成");
                        updateCloseSlip(vo.getTraderId(), vo.getSymbol(), followOrderCloseEntity, 0);
                    } catch (Exception e) {
                        log.error("平仓任务出错", e);
                    }
                }
            });
        } else {
            //只有间隔会创建平仓标识
            if (vo.getIntervalTime() != 0) {
                redisCache.set(Constant.TRADER_CLOSE + vo.getTraderId(), 1);
            }
            // 有间隔时间的下单，依次执行并等待
            Integer finalOrderCount1 = orderCount;
            AtomicInteger count = new AtomicInteger();
            ThreadPoolUtils.getExecutor().execute(() -> {
                if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (int i = 0; i < finalOrderCount1; i++) {
                        Object cacheValue = redisCache.get(Constant.TRADER_CLOSE + vo.getTraderId());
                        if (ObjectUtil.isEmpty(cacheValue) || (ObjectUtil.isNotEmpty(cacheValue) && cacheValue.equals(1))) {
                            try {
                                int finalI = i;
                                CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                                    updateCloseOrder(followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderActive.get(finalI)).eq(FollowOrderDetailEntity::getTraderId,followTraderEntity.getId())), quoteClient, oc, followOrderCloseEntity);
                                    //进行平仓滑点分析
                                    updateCloseSlip(vo.getTraderId(), vo.getSymbol(), followOrderCloseEntity, 1);
                                }, ThreadPoolUtils.getExecutor());
                                futures.add(orderFuture);

                                count.getAndIncrement();
                                if (count.get() != finalOrderCount1.intValue()) {
                                    Thread.sleep(interval);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    log.info("间隔平仓任务一共执行{}次", count);
                    // 使用 CompletableFuture.allOf 等待所有订单任务完成
                    CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                    // 当所有订单任务完成后，执行更新操作
                    allOrdersCompleted.thenRun(() -> {
                        try {
                            log.info("所有间隔平仓任务已完成");
                            followOrderCloseEntity.setStatus(CloseOrOpenEnum.OPEN.getValue());
                            followOrderCloseEntity.setFinishTime(LocalDateTime.now());
                            followOrderCloseService.updateById(followOrderCloseEntity);
                            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE + vo.getTraderId()))) {
                                redisCache.delete(Constant.TRADER_CLOSE + vo.getTraderId());
                            }
                        } catch (Exception e) {
                            log.error("平仓任务出错", e);
                        }
                    });
                }else {
                    for (int i = 0; i < finalOrderCount1; i++) {
                        Object cacheValue = redisCache.get(Constant.TRADER_CLOSE + vo.getTraderId());
                        if (ObjectUtil.isEmpty(cacheValue) || (ObjectUtil.isNotEmpty(cacheValue) && cacheValue.equals(1))) {
                            try {
                                int finalI = i;
                                updateCloseOrder(followOrderDetailService.getOne(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getOrderNo, orderActive.get(finalI)).eq(FollowOrderDetailEntity::getTraderId,followTraderEntity.getId())), quoteClient, oc, followOrderCloseEntity);
                                //进行平仓滑点分析
                                updateCloseSlip(vo.getTraderId(), vo.getSymbol(), followOrderCloseEntity, 1);

                                count.getAndIncrement();
                                if (count.get() != finalOrderCount1.intValue()) {
                                    Thread.sleep(interval);
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    log.info("所有间隔平仓任务已完成");
                    followOrderCloseEntity.setStatus(CloseOrOpenEnum.OPEN.getValue());
                    followOrderCloseEntity.setFinishTime(LocalDateTime.now());
                    followOrderCloseService.updateById(followOrderCloseEntity);
                    if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE + vo.getTraderId()))) {
                        redisCache.delete(Constant.TRADER_CLOSE + vo.getTraderId());
                    }
                }
            });

        }
        return true;
    }

    @Override
    public FollowOrderSendEntity orderDoing(Long traderId) {
        List<FollowOrderSendEntity> list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId, traderId).eq(FollowOrderSendEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()).orderByDesc(FollowOrderSendEntity::getId));
        if (ObjectUtil.isEmpty(list)) {
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
            entity.setDiff(quoteClient.ServerTimeZone() / 60);
            entity.setFreeMargin(BigDecimal.valueOf(quoteClient.AccountFreeMargin()));
            //预付款比例:账户的净值÷已用预付款
            if (BigDecimal.valueOf(quoteClient.AccountMargin()).compareTo(BigDecimal.ZERO) != 0) {
                entity.setMarginProportion(BigDecimal.valueOf(quoteClient.AccountEquity()).divide(BigDecimal.valueOf(quoteClient.AccountMargin()), 4, RoundingMode.HALF_UP));
            } else {
                entity.setMarginProportion(BigDecimal.ZERO);
            }
            entity.setLeverage(quoteClient.AccountLeverage());
            entity.setIsDemo(quoteClient._IsDemo);
            entity.setCreator(SecurityUser.getUserId());
            entity.setCreateTime(LocalDateTime.now());
            this.updateById(entity);
            //增加品种规格记录
            addSysmbolSpecification(entity, quoteClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public TraderOverviewVO traderOverview(String ip) {
        //查看所有账号
        TraderOverviewVO traderOverviewVO = new TraderOverviewVO();
        List<FollowTraderEntity> list = this.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, ip));
        traderOverviewVO.setTraderTotal(list.size());
        Integer total = 0;
        double buyNum = 0;
        double sellNum = 0;
        for (FollowTraderEntity traderEntity : list) {
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER + traderEntity.getId()))) {
                FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + traderEntity.getId());
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getTotal())) {
                    total += followRedisTraderVO.getTotal();
                }
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getBuyNum())) {
                    buyNum += followRedisTraderVO.getBuyNum();
                }
                if (ObjectUtil.isNotEmpty(followRedisTraderVO.getSellNum())) {
                    sellNum += followRedisTraderVO.getSellNum();
                }
            }
        }
        traderOverviewVO.setOrderTotal(total);
        traderOverviewVO.setBuyNum(buyNum);
        traderOverviewVO.setSellNum(sellNum);
        return traderOverviewVO;
    }

    @Override
    public Boolean stopOrder(Integer type, String traderId) {
        if (type == 0) {
            //停止下单
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND + traderId))) {
                redisCache.set(Constant.TRADER_SEND + traderId, 2);
            } else {
                //查询正在下单中的数据
                List<FollowOrderSendEntity> list = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getTraderId, traderId).eq(FollowOrderSendEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()));
                if (ObjectUtil.isNotEmpty(list)){
                    list.forEach(o->{
                        o.setStatus(CloseOrOpenEnum.OPEN.getValue());
                        followOrderSendService.updateById(o);
                    });
                }else {
                    return false;
                }
            }
        } else {
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_CLOSE + traderId))) {
                redisCache.set(Constant.TRADER_CLOSE + traderId, 2);
            } else {
                //查询正在平仓中的数据
                List<FollowOrderCloseEntity> list = followOrderCloseService.list(new LambdaQueryWrapper<FollowOrderCloseEntity>().eq(FollowOrderCloseEntity::getTraderId, traderId).eq(FollowOrderCloseEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()));
                if (ObjectUtil.isNotEmpty(list)){
                    list.forEach(o->{
                        o.setStatus(CloseOrOpenEnum.OPEN.getValue());
                        followOrderCloseService.updateById(o);
                    });
                }else {
                    return false;
                }
            }
        }
        return true;
    }

    private void updateCloseSlip(long traderId, String symbol, FollowOrderCloseEntity followOrderCloseEntity, Integer flag) {
        if (flag != 2) {
            //查看平仓所有数据
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getCloseId, followOrderCloseEntity.getId()));
            followOrderCloseEntity.setFailNum((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getRemark())).count());
            followOrderCloseEntity.setSuccessNum(list.size() - followOrderCloseEntity.getFailNum());
            if (flag == 1) {
                //间隔平仓判断
                if (followOrderCloseEntity.getTotalNum() == list.size()) {
                    followOrderCloseEntity.setFinishTime(LocalDateTime.now());
                    followOrderCloseEntity.setStatus(CloseOrOpenEnum.OPEN.getValue());
                }
            } else {
                //同步平仓直接结束
                followOrderCloseEntity.setFinishTime(LocalDateTime.now());
                followOrderCloseEntity.setStatus(CloseOrOpenEnum.OPEN.getValue());
            }
            followOrderCloseService.updateById(followOrderCloseEntity);
        }

        LambdaQueryWrapper<FollowOrderDetailEntity> followLambdaQueryWrapper = new LambdaQueryWrapper<>();
        followLambdaQueryWrapper.eq(FollowOrderDetailEntity::getTraderId, traderId)
                .isNotNull(FollowOrderDetailEntity::getClosePrice)
                .isNotNull(FollowOrderDetailEntity::getRequestClosePrice)
                .eq(FollowOrderDetailEntity::getIsExternal,CloseOrOpenEnum.CLOSE.getValue())
                .isNull(FollowOrderDetailEntity::getClosePriceSlip);
        //查询需要滑点分析的数据 有平仓价格但是无平仓滑点
        if (ObjectUtil.isNotEmpty(symbol)) {
            followLambdaQueryWrapper.eq(FollowOrderDetailEntity::getSymbol, symbol);
        }
        List<FollowOrderDetailEntity> list = followOrderDetailService.list(followLambdaQueryWrapper);
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(traderId);
        //开始平仓
        list.forEach(o -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i)).get(o.getSymbol());
                BigDecimal hd;
                if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
                    //如果forex 并包含JPY 也是100
                    if (o.getSymbol().contains("JPY")) {
                        hd = new BigDecimal("100");
                    } else {
                        hd = new BigDecimal("10000");
                    }
                } else {
                    //如果非forex 都是 100
                    hd = new BigDecimal("100");
                }
                o.setClosePriceSlip(o.getClosePrice().subtract(o.getRequestClosePrice()).multiply(hd).abs());
                followOrderDetailService.updateById(o);
            });
        });
    }

    private void updateCloseOrder(FollowOrderDetailEntity followOrderDetailEntity, QuoteClient quoteClient, OrderClient oc, FollowOrderCloseEntity followOrderCloseEntity) {
        String symbol = followOrderDetailEntity.getSymbol();
        Integer orderNo = followOrderDetailEntity.getOrderNo();
        try {
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(symbol))) {
                //订阅
                quoteClient.Subscribe(symbol);
            }
            double bid =0;
            double ask =0;
            int loopTimes=1;
            QuoteEventArgs quoteEventArgs = null;
            while (quoteEventArgs == null && quoteClient.Connected()) {
                quoteEventArgs = quoteClient.GetQuote(symbol);
                if (++loopTimes > 20) {
                    break;
                } else {
                    Thread.sleep(50);
                }
            }
            bid =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Bid:0;
            ask =ObjectUtil.isNotEmpty(quoteEventArgs)?quoteEventArgs.Ask:0;
            LocalDateTime nowdate = LocalDateTime.now();
            log.info("平仓信息{},{},{},{},{}", symbol, orderNo, followOrderDetailEntity.getSize(), bid, ask);
            if (ObjectUtil.isNotEmpty(followOrderCloseEntity)) {
                followOrderDetailEntity.setCloseId(followOrderCloseEntity.getId());
            }
            Order orderResult;
            if (followOrderDetailEntity.getType() == Buy.getValue()) {
                long start = System.currentTimeMillis();
                orderResult = oc.OrderClose(symbol, orderNo, followOrderDetailEntity.getSize().doubleValue(), bid, 0);
                long end = System.currentTimeMillis();
                log.info("MT4平仓时间差 订单:"+orderResult.Ticket+"内部时间差:"+orderResult.sendTimeDifference+"外部时间差:"+(end-start));
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(bid));
            } else {
                long start = System.currentTimeMillis();
                orderResult = oc.OrderClose(symbol, orderNo, followOrderDetailEntity.getSize().doubleValue(), ask, 0);
                long end = System.currentTimeMillis();
                log.info("MT4平仓时间差 订单:"+orderResult.Ticket+"内部时间差:"+orderResult.sendTimeDifference+"外部时间差:"+(end-start));
                followOrderDetailEntity.setRequestClosePrice(BigDecimal.valueOf(ask));
            }
            log.info("订单 " + orderNo + ": 平仓 " + orderResult);
            //保存平仓信息
            followOrderDetailEntity.setCloseTimeDifference((int)orderResult.sendTimeDifference);
            followOrderDetailEntity.setResponseCloseTime(LocalDateTime.now());
            followOrderDetailEntity.setRequestCloseTime(nowdate);
            followOrderDetailEntity.setCloseTime(orderResult.CloseTime);
            followOrderDetailEntity.setClosePrice(BigDecimal.valueOf(orderResult.ClosePrice));
            followOrderDetailEntity.setSwap(BigDecimal.valueOf(orderResult.Swap));
            followOrderDetailEntity.setCommission(BigDecimal.valueOf(orderResult.Commission));
            followOrderDetailEntity.setProfit(BigDecimal.valueOf(orderResult.Profit));
            followOrderDetailEntity.setCloseStatus(CloseOrOpenEnum.OPEN.getValue());
            FollowVpsEntity vps = followVpsService.getVps(FollowConstant.LOCAL_HOST);
            followOrderDetailEntity.setCloseServerName(vps.getName());
            followOrderDetailEntity.setCloseServerHost(quoteClient.Host+":"+quoteClient.Port);
            followOrderDetailEntity.setCloseIpAddr(FollowConstant.LOCAL_HOST);
            followOrderDetailEntity.setRemark(null);
        } catch (Exception e) {
            log.error(orderNo+"平仓出错" + e.getMessage());
            if (ObjectUtil.isNotEmpty(followOrderCloseEntity)) {
                followOrderDetailEntity.setRemark("平仓出错" + e.getMessage());
            }
        }
        followOrderDetailService.updateById(followOrderDetailEntity);
        if (ObjectUtil.isNotEmpty(followOrderCloseEntity)) {
            followOrderCloseService.updateById(followOrderCloseEntity);
        }
    }

    // 示例 1: 每笔订单的下单数量为 总手数/订单数量
    public void executeOrdersFixedQuantity(String ipAdd, String serverName, BigDecimal pr, String platform, String brokerName, FollowOrderSendVO vo, long traderId, String account, Integer type, QuoteClient quoteClient, String symbol, double totalLots, int orderCount, Integer interval, String orderNo,String remark) {
        double lotsPerOrder = roundToTwoDecimal(totalLots / orderCount);
        // 如果每笔手数小于 0.01，将每笔订单设置为 0.01，并重新计算总订单数
        if (lotsPerOrder < 0.01 || lotsPerOrder == 0.01) {
            lotsPerOrder = 0.01;
            log.info("总手数{}++每笔手数{}+++总单数{}", totalLots, lotsPerOrder, (int) Math.ceil(totalLots / lotsPerOrder));
            orderCount = (int) Math.ceil(totalLots / lotsPerOrder); // 重新计算订单数量，确保总手数匹配
        }
        lotsPerOrder = roundToTwoDecimal(lotsPerOrder);
        //判断是否超过总手数
        log.info("执行固定订单数量的下单操作，共" + orderCount + "笔订单，每笔手数: " + lotsPerOrder);

        List<Double> orders = new ArrayList<>();
        double accumulatedLots = 0.0; // 累计手数
        int remaining = 0;
        for (int i = 0; i < orderCount; i++) {
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
            accumulatedLots = accumulatedLots + lotsPerOrder;
        }
        orderCount = orderCount - remaining;
        vo.setTotalNum(orderCount);
        followOrderSendService.save(vo);
        //删除缓存
        redisCache.delete(Constant.TRADER_ORDER + traderId);
        //合约比例处理
        if (pr.compareTo(BigDecimal.ONE) != 0) {
            // 使用索引更新列表中的值
            for (int i = 0; i < orders.size(); i++) {
                BigDecimal orderValue = BigDecimal.valueOf(orders.get(i));
                //保留两位小数，四舍五入
                BigDecimal newOrderValue = orderValue.multiply(pr).setScale(2, RoundingMode.HALF_UP);

                if (newOrderValue.compareTo(new BigDecimal("0.01")) < 0) {
                    // 如果小于 0.01，则设置为 0.01
                    orders.set(i, 0.01);
                } else {
                    // 否则将新的值设置回 orders 列表
                    orders.set(i, newOrderValue.doubleValue());
                }
            }
        }
        log.info("下单数量{}", orders);
        if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
            executeOrder(ipAdd, serverName, platform, brokerName, interval, orderCount, orders, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }else {
            executeOrderNoSyn(ipAdd, serverName, platform, brokerName, interval, orderCount, orders, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }
    }

    private void updateSendOrder(long traderId, String orderNo, Integer flag) {
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> specificationEntityMap = followSysmbolSpecificationService.getByTraderId(traderId);
        FollowOrderSendEntity sendServiceOne = followOrderSendService.getOne(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getOrderNo, orderNo));
        if (ObjectUtil.isNotEmpty(sendServiceOne)) {
            //查看下单所有数据
            List<FollowOrderDetailEntity> list = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
            //保存修改信息
            //保存真实下单数
//            List<FollowOrderDetailEntity> trueList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getSendNo, orderNo));
//            sendServiceOne.setTotalNum(trueList.size());
            //保存真实下单手数
            sendServiceOne.setTrueSize(list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).map(FollowOrderDetailEntity::getSize).reduce(BigDecimal.ZERO, BigDecimal::add));
            sendServiceOne.setSuccessNum((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).count());
            sendServiceOne.setFailNum((int) list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getRemark())).count());
            if (flag == 1) {
                //间隔下单判断
                if (sendServiceOne.getTotalNum() == list.size()) {
                    sendServiceOne.setFinishTime(LocalDateTime.now());
                    sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
                }
            } else {
                //同步下单直接结束
                sendServiceOne.setFinishTime(LocalDateTime.now());
                sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
            }

            followOrderSendService.updateById(sendServiceOne);
            //进行滑点分析
            list.stream().filter(o -> ObjectUtil.isNotEmpty(o.getOpenTime())).collect(Collectors.toList()).forEach(o -> {
                ThreadPoolUtils.getExecutor().execute(()->{
                    FollowSysmbolSpecificationEntity followSysmbolSpecificationEntity = specificationEntityMap.stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i)).get(o.getSymbol());
                    BigDecimal hd;
                    if (followSysmbolSpecificationEntity.getProfitMode().equals("Forex")) {
                        //如果forex 并包含JPY 也是100
                        if (o.getSymbol().contains("JPY")) {
                            hd = new BigDecimal("100");
                        } else {
                            hd = new BigDecimal("10000");
                        }
                    } else {
                        //如果非forex 都是 100
                        hd = new BigDecimal("100");
                    }
                    o.setOpenPriceSlip(o.getOpenPrice().subtract(o.getRequestOpenPrice()).multiply(hd).abs());
                    followOrderDetailService.updateById(o);
                });
            });
        } else {
            log.info("未查询到订单");
        }
    }

    private void ordersends(String ipAdd, String serverName, String platform, String brokerName, long traderId, String account, QuoteClient quoteClient, String symbol, Integer type, OrderClient oc, double lotsPerOrder, Integer orderId, double ask, double bid, LocalDateTime nowdate, String orderNo, Integer placedType,String remark) {
        //插入订单详情记录
        FollowOrderDetailEntity followOrderDetailEntity = new FollowOrderDetailEntity();
        followOrderDetailEntity.setTraderId(traderId);
        followOrderDetailEntity.setAccount(account);
        followOrderDetailEntity.setSymbol(symbol);
        followOrderDetailEntity.setCreator(SecurityUser.getUserId());
        followOrderDetailEntity.setCreateTime(LocalDateTime.now());
        followOrderDetailEntity.setSendNo(orderNo);
        followOrderDetailEntity.setType(type);
        followOrderDetailEntity.setPlacedType(placedType);
        followOrderDetailEntity.setPlatform(platform);
        followOrderDetailEntity.setBrokeName(brokerName);
        followOrderDetailEntity.setIpAddr(ipAdd);
        followOrderDetailEntity.setServerName(serverName);
        //下单方式
        oc.PlacedType = PlacedType.forValue(placedType);
        try {
            //检查最大手数
            Object o1 = redisCache.hGet(Constant.SYSTEM_PARAM_LOTS_MAX, Constant.LOTS_MAX);
            if(ObjectUtil.isNotEmpty(o1)){
                BigDecimal max = new BigDecimal(o1.toString());
                BigDecimal lots = new BigDecimal(lotsPerOrder);
                if (lots.compareTo(max)>0) {
                    throw  new ServerException("超过最大手数限制");
                }
            }
            double asksub = quoteClient.GetQuote(symbol).Ask;
            double bidsub = quoteClient.GetQuote(symbol).Bid;
            Order order;
            followOrderDetailEntity.setSize(BigDecimal.valueOf(lotsPerOrder));
            followOrderDetailEntity.setSourceUser(account);
            followOrderDetailEntity.setServerHost(quoteClient.Host+":"+quoteClient.Port);
            if (type.equals(Buy.getValue())) {
                long start = System.currentTimeMillis();
                order = oc.OrderSend(symbol, Buy, lotsPerOrder, asksub, 0, 0, 0,ObjectUtil.isNotEmpty(remark)?remark:"", Integer.valueOf(RandomStringUtil.generateNumeric(5)), null);
                long end = System.currentTimeMillis();
                log.info("MT4下单时间差 订单:"+order.Ticket+"内部时间差:"+order.sendTimeDifference+"外部时间差:"+(end-start));
                followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(asksub));
            } else {
                long start = System.currentTimeMillis();
                order = oc.OrderSend(symbol, Sell, lotsPerOrder, bidsub, 0, 0, 0, ObjectUtil.isNotEmpty(remark)?remark:"", Integer.valueOf(RandomStringUtil.generateNumeric(5)), null);
                long end = System.currentTimeMillis();
                log.info("MT4下单时间差 订单:"+order.Ticket+"内部时间差:"+order.sendTimeDifference+"外部时间差:"+(end-start));
                followOrderDetailEntity.setRequestOpenPrice(BigDecimal.valueOf(bidsub));
            }
            followOrderDetailEntity.setOpenTimeDifference((int) order.sendTimeDifference);
            followOrderDetailEntity.setResponseOpenTime(LocalDateTime.now());
            log.info("下单详情 账号:"+traderId+"平台:"+platform+"节点:"+oc.QuoteClient.Host+":"+oc.QuoteClient.Port);
            log.info("订单" + orderId + "开仓时刻数据价格:" + order.OpenPrice + " 时间" + order.OpenTime);
            followOrderDetailEntity.setCommission(BigDecimal.valueOf(order.Commission));
            followOrderDetailEntity.setOpenTime(order.OpenTime);
            followOrderDetailEntity.setOpenPrice(BigDecimal.valueOf(order.OpenPrice));
            followOrderDetailEntity.setOrderNo(order.Ticket);
            followOrderDetailEntity.setRequestOpenTime(nowdate);
            followOrderDetailEntity.setSl(BigDecimal.valueOf(order.StopLoss));
            followOrderDetailEntity.setSwap(BigDecimal.valueOf(order.Swap));
            followOrderDetailEntity.setTp(BigDecimal.valueOf(order.TakeProfit));
            followOrderDetailEntity.setRateMargin(order.RateMargin);
            followOrderDetailEntity.setMagical(order.MagicNumber);
        }catch (ServerException e){
            followOrderDetailEntity.setRemark( e.getMessage());
        } catch (TimeoutException e) {
            log.info("下单超时");
            followOrderDetailEntity.setRemark("下单超时" + e.getMessage());
        } catch (ConnectException e) {
            log.info("连接异常");
            followOrderDetailEntity.setRemark("连接异常" + e.getMessage());
        } catch (TradeException | RuntimeException e) {
            log.info("交易异常" + e);
            followOrderDetailEntity.setRemark("交易异常" + e.getMessage());
        } catch (InvalidSymbolException e) {
            log.info("无效symbol");
            followOrderDetailEntity.setRemark("无效symbol" + e.getMessage());
        }
        followOrderDetailService.save(followOrderDetailEntity);
    }


    // 示例 2: 每笔订单的下单数量为 区间内的随机值，总手数不超过 总手数，订单数量不固定
    public void executeOrdersRandomTotalLots(String ipAdd, String serverName, BigDecimal pr, String platform, String brokerName, FollowOrderSendVO vo, long traderId, String account, Integer type, QuoteClient quoteClient, String symbol, double totalLots, BigDecimal minLots, BigDecimal maxLots, Integer interval, String orderNo,String remark) {
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
        //过滤0值
        orders = orders.stream().filter(o -> !o.equals(0.0)).collect(Collectors.toList());
        int orderCount = orders.size();
        log.info("执行随机下单操作，总手数不超过 " + totalLots + "，实际下单订单数: " + orderCount);

        if (orderCount == 0) {
            // 若订单数为 0，抛出异常
            throw new ServerException("请重新下单");
        }

        vo.setTotalNum(orderCount);
        followOrderSendService.save(vo);
        redisCache.delete(Constant.TRADER_ORDER + traderId);
        if (pr.compareTo(BigDecimal.ONE) != 0) {
            // 使用索引更新列表中的值
            for (int i = 0; i < orders.size(); i++) {
                BigDecimal orderValue = BigDecimal.valueOf(orders.get(i));
                BigDecimal newOrderValue = orderValue.multiply(pr);

                if (newOrderValue.compareTo(new BigDecimal("0.01")) < 0) {
                    // 如果小于 0.01，则设置为 0.01
                    orders.set(i, 0.01);
                } else {
                    // 否则将新的值设置回 orders 列表
                    orders.set(i, newOrderValue.doubleValue());
                }
            }
        }
        log.info("下单数量{}", orders);
        if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
            executeOrder(ipAdd, serverName, platform, brokerName, interval, orderCount, orders, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }else {
            executeOrderNoSyn(ipAdd, serverName, platform, brokerName, interval, orderCount, orders, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }
    }


    // 示例 3: 每笔订单的下单数量为 区间内的随机值，总订单数量固定，总手数不限
    public void executeOrdersRandomFixedCount(String ipAdd, String serverName, BigDecimal pr, String platform, String brokerName, FollowOrderSendVO vo, long traderId, String account, Integer type, QuoteClient quoteClient, String symbol, Integer orderCount, BigDecimal minLots, BigDecimal maxLots, Integer interval, String orderNo,String remark) {
        Random rand = new Random();
        List<Double> orders = new ArrayList<>();

        for (int i = 0; i < orderCount; i++) {
            double randomLots = roundToTwoDecimal(minLots.doubleValue() + (maxLots.doubleValue() - minLots.doubleValue()) * rand.nextDouble());
            orders.add(randomLots);
        }
        log.info("执行固定订单数量随机下单操作，共" + orderCount + "笔订单。");
        followOrderSendService.save(vo);
        redisCache.delete(Constant.TRADER_ORDER + traderId);
        if (pr.compareTo(BigDecimal.ONE) != 0) {
            // 使用索引更新列表中的值
            for (int i = 0; i < orders.size(); i++) {
                BigDecimal orderValue = BigDecimal.valueOf(orders.get(i));
                BigDecimal newOrderValue = orderValue.multiply(pr);

                if (newOrderValue.compareTo(new BigDecimal("0.01")) < 0) {
                    // 如果小于 0.01，则设置为 0.01
                    orders.set(i, 0.01);
                } else {
                    // 否则将新的值设置回 orders 列表
                    orders.set(i, newOrderValue.doubleValue());
                }
            }
        }
        log.info("下单数量{}", orders);
        if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
            executeOrder(ipAdd, serverName, platform, brokerName, interval, orderCount, orders, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }else {
            executeOrderNoSyn(ipAdd, serverName, platform, brokerName, interval, orderCount, orders, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }
    }

    // 示例 4: 每笔订单的下单数量为 区间内的随机值，总手数和订单数量都受限制
    public void executeOrdersRandomLimited(String ipAdd, String serverName, BigDecimal pr, String platform, String brokerName, FollowOrderSendVO vo, long traderId, String account, Integer type, QuoteClient quoteClient, String symbol, Integer orderCount, BigDecimal totalLots, BigDecimal minLots, BigDecimal maxLots, Integer interval, String orderNo,String remark) {
        Random rand = new Random();
        BigDecimal totalPlacedLots = BigDecimal.ZERO;  // 已下单的总手数
        int orderCountNum = 0;                         // 已下单的订单数量
        List<BigDecimal> orders = new ArrayList<>();
        while (totalPlacedLots.compareTo(totalLots) < 0 && orderCountNum < orderCount) {
            // 生成随机手数，并四舍五入保留两位小数
            BigDecimal randomLots = minLots.add(maxLots.subtract(minLots).multiply(new BigDecimal(rand.nextDouble())))
                    .setScale(2, RoundingMode.HALF_UP);

            // 检查生成的随机手数是否超过剩余手数
            if (totalPlacedLots.add(randomLots).compareTo(totalLots) > 0) {
                randomLots = totalLots.subtract(totalPlacedLots).setScale(2, RoundingMode.HALF_UP);
            }

            // 防止生成的订单手数为 0
            if (randomLots.compareTo(BigDecimal.ZERO) > 0) {
                orders.add(randomLots);
                totalPlacedLots = totalPlacedLots.add(randomLots);
                orderCountNum++;
            }
        }

        // 如果还有剩余手数，按比例分配给每个订单
        BigDecimal remainingLots = totalLots.subtract(totalPlacedLots).setScale(2, RoundingMode.HALF_UP);
        if (remainingLots.compareTo(BigDecimal.ZERO) > 0 && !orders.isEmpty()) {
            BigDecimal lotsToAddPerOrder = remainingLots.divide(new BigDecimal(orderCountNum), 2, RoundingMode.DOWN);

            BigDecimal cumulativeRemainder = remainingLots.subtract(lotsToAddPerOrder.multiply(new BigDecimal(orderCountNum)));

            for (int i = 0; i < orders.size(); i++) {
                BigDecimal updatedOrder = orders.get(i).add(lotsToAddPerOrder).setScale(2, RoundingMode.HALF_UP);
                if (cumulativeRemainder.compareTo(BigDecimal.ZERO) > 0) {
                    updatedOrder = updatedOrder.add(new BigDecimal("0.01"));
                    cumulativeRemainder = cumulativeRemainder.subtract(new BigDecimal("0.01"));
                }
                orders.set(i, updatedOrder);
            }
        }

        // 最终确认总手数并调整误差
        BigDecimal finalTotal = orders.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (finalTotal.compareTo(totalLots) > 0) {
            BigDecimal excess = finalTotal.subtract(totalLots);
            for (int i = 0; i < orders.size() && excess.compareTo(BigDecimal.ZERO) > 0; i++) {
                BigDecimal order = orders.get(i);
                BigDecimal adjustment = order.min(excess).setScale(2, RoundingMode.HALF_UP);
                orders.set(i, order.subtract(adjustment));
                excess = excess.subtract(adjustment);
            }
        } else if (finalTotal.compareTo(totalLots) < 0) {
            BigDecimal deficit = totalLots.subtract(finalTotal);
            int randomOrderIndex = rand.nextInt(orders.size());
            orders.set(randomOrderIndex, orders.get(randomOrderIndex).add(deficit).setScale(2, RoundingMode.HALF_UP));
        }

        // 过滤 0 值
        orders = orders.stream().filter(o -> o.compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
        log.info("执行有限订单数量随机下单操作，总手数不超过" + totalLots + "，最大订单数: " + orderCount + "，实际下单订单数: " + orders.size());

        if (orders.isEmpty()) {
            // 下单异常，抛出异常
            throw new ServerException("请重新下单");
        }
        // 保存实际下单的订单数量
        vo.setTotalNum(orders.size());
        followOrderSendService.save(vo);
        redisCache.delete(Constant.TRADER_ORDER + traderId);
        if (pr.compareTo(BigDecimal.ONE) != 0) {
            // 使用索引更新列表中的值
            for (int i = 0; i < orders.size(); i++) {
                BigDecimal orderValue = orders.get(i);
                BigDecimal newOrderValue = orderValue.multiply(pr);

                if (newOrderValue.compareTo(new BigDecimal("0.01")) < 0) {
                    // 如果小于 0.01，则设置为 0.01
                    orders.set(i, new BigDecimal("0.01"));
                } else {
                    // 否则将新的值设置回 orders 列表
                    orders.set(i, newOrderValue);
                }
            }
        }
        log.info("下单数量{}", orders);
        // 执行订单操作
        List<Double> collect = orders.stream().map(BigDecimal::doubleValue).collect(Collectors.toList());
        if (followVpsService.getVps(FollowConstant.LOCAL_HOST).getIsSyn().equals(CloseOrOpenEnum.OPEN.getValue())){
            executeOrder(ipAdd, serverName, platform, brokerName, interval, orderCountNum, collect, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }else {
            executeOrderNoSyn(ipAdd, serverName, platform, brokerName, interval, orderCountNum, collect, traderId, account, quoteClient, symbol, type, orderNo, vo.getPlacedType(),remark);
        }
    }

    // 保留两位小数的方法
    public static double roundToTwoDecimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void executeOrder(String ipAdd, String serverName, String platform, String brokerName, Integer interval, Integer orderCount, List<Double> orders, long traderId, String account, QuoteClient quoteClient, String symbol, Integer type, String orderNo, Integer placedType,String remark) {
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)) {
            oc = quoteClient.OrderClient;
        } else {
            oc = new OrderClient(quoteClient);
        }
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            ThreadPoolUtils.getExecutor().execute(() -> {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < orderCount; i++) {
                    int orderId = i + 1;
                    int finalI = i;
                    CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                        try {
                            double ask = quoteClient.GetQuote(symbol).Ask;
                            double bid = quoteClient.GetQuote(symbol).Bid;
                            LocalDateTime nowdate = LocalDateTime.now();
                            log.info("订单 " + orderId + ": 并发下单手数为 " + orders.get(finalI));
                            ordersends(ipAdd, serverName, platform, brokerName, traderId, account, quoteClient, symbol, type, oc, orders.get(finalI), orderId, ask, bid, nowdate, orderNo, placedType,remark);
                        } catch (Exception e) {
                            log.info("订单交易异常" + e.getMessage());
                        }
                    }, ThreadPoolUtils.getExecutor());
                    futures.add(orderFuture);
                }
                CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                // 当所有订单任务完成后，执行更新操作
                allOrdersCompleted.thenRun(() -> {
                    try {
                        log.info("开始更新订单状态");
                        updateSendOrder(traderId, orderNo, 0);  // 确保所有订单完成后再执行
                    } catch (Exception e) {
                        log.error("下单任务出错", e);
                    }
                });
            });
        } else {
            // 有间隔时间的下单，依次执行并等待
            ThreadPoolUtils.getExecutor().execute(() -> {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int i = 0; i < orderCount; i++) {
                    Object cacheValue = redisCache.get(Constant.TRADER_SEND + traderId);
                    if (ObjectUtil.isEmpty(cacheValue) || (ObjectUtil.isNotEmpty(cacheValue) && cacheValue.equals(1))) {
                        int finalI = i;
                        CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
                            int orderId = finalI + 1;
                            log.info("订单 " + orderId + ": 下单手数为 " + orders.get(finalI));
                            try {
                                // 将 interval 从秒转换为毫秒
                                double ask = quoteClient.GetQuote(symbol).Ask;
                                double bid = quoteClient.GetQuote(symbol).Bid;
                                LocalDateTime nowdate = LocalDateTime.now();
                                ordersends(ipAdd, serverName, platform, brokerName, traderId, account, quoteClient, symbol, type, oc, orders.get(finalI), orderId, ask, bid, nowdate, orderNo, placedType,remark);
                                Thread.sleep(100);
                                updateSendOrder(traderId, orderNo, 1);
                            } catch (InvalidSymbolException | NullPointerException | InterruptedException e) {
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
                            }
                        }, ThreadPoolUtils.getExecutor());
                        futures.add(orderFuture);

                        if (i != orderCount - 1) {
                            try {
                                Thread.sleep(interval);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                CompletableFuture<Void> allOrdersCompleted = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                // 当所有订单任务完成后，执行更新操作
                allOrdersCompleted.thenRun(() -> {
                    try {
                        log.info("所有间隔订单任务已完成");
                        FollowOrderSendEntity sendServiceOne = followOrderSendService.getOne(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getOrderNo, orderNo));
                        sendServiceOne.setFinishTime(LocalDateTime.now());
                        sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
                        followOrderSendService.updateById(sendServiceOne);

                        try {
                            Thread.sleep(200);
                            //删除缓存
                            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND + traderId))) {
                                redisCache.delete(Constant.TRADER_SEND + traderId);
                            }
                            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_ORDER + traderId))) {
                                redisCache.delete(Constant.TRADER_ORDER + traderId);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                    } catch (Exception e) {
                        log.error("间隔下单任务出错", e);
                    }
                });
            });
        }
    }

    private void executeOrderNoSyn(String ipAdd, String serverName, String platform, String brokerName, Integer interval, Integer orderCount, List<Double> orders, long traderId, String account, QuoteClient quoteClient, String symbol, Integer type, String orderNo, Integer placedType,String remark) {
        OrderClient oc;
        if (ObjectUtil.isNotEmpty(quoteClient.OrderClient)) {
            oc = quoteClient.OrderClient;
        } else {
            oc = new OrderClient(quoteClient);
        }
        if (ObjectUtil.isEmpty(interval) || interval == 0) {
            ThreadPoolUtils.getExecutor().execute(() -> {
                for (int i = 0; i < orderCount; i++) {
                    int orderId = i + 1;
                    int finalI = i;
                    try {
                        double ask = quoteClient.GetQuote(symbol).Ask;
                        double bid = quoteClient.GetQuote(symbol).Bid;
                        LocalDateTime nowdate = LocalDateTime.now();
                        log.info("订单 " + orderId + ": 并发下单手数为 " + orders.get(finalI));
                        ordersends(ipAdd, serverName, platform, brokerName, traderId, account, quoteClient, symbol, type, oc, orders.get(finalI), orderId, ask, bid, nowdate, orderNo, placedType,remark);
                    } catch (Exception e) {
                        log.info("订单交易异常" + e.getMessage());
                    }
                }
                // 当所有订单任务完成后，执行更新操作
                try {
                    log.info("开始更新订单状态");
                    updateSendOrder(traderId, orderNo, 0);  // 确保所有订单完成后再执行
                } catch (Exception e) {
                    log.error("下单任务出错", e);
                }
            });
        } else {
            // 有间隔时间的下单，依次执行并等待
            ThreadPoolUtils.getExecutor().execute(() -> {
                for (int i = 0; i < orderCount; i++) {
                    Object cacheValue = redisCache.get(Constant.TRADER_SEND + traderId);
                    if (ObjectUtil.isEmpty(cacheValue) || (ObjectUtil.isNotEmpty(cacheValue) && cacheValue.equals(1))) {
                        int finalI = i;
                        int orderId = finalI + 1;
                        log.info("订单 " + orderId + ": 下单手数为 " + orders.get(finalI));
                        try {
                            // 将 interval 从秒转换为毫秒
                            double ask = quoteClient.GetQuote(symbol).Ask;
                            double bid = quoteClient.GetQuote(symbol).Bid;
                            LocalDateTime nowdate = LocalDateTime.now();
                            ordersends(ipAdd, serverName, platform, brokerName, traderId, account, quoteClient, symbol, type, oc, orders.get(finalI), orderId, ask, bid, nowdate, orderNo, placedType,remark);
                            Thread.sleep(100);
                            updateSendOrder(traderId, orderNo, 1);
                        } catch (InvalidSymbolException | NullPointerException | InterruptedException e) {
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
                        }
                        if (i != orderCount - 1) {
                            try {
                                Thread.sleep(interval);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                try {
                    log.info("所有间隔订单任务已完成");
                    FollowOrderSendEntity sendServiceOne = followOrderSendService.getOne(new LambdaQueryWrapper<FollowOrderSendEntity>().eq(FollowOrderSendEntity::getOrderNo, orderNo));
                    sendServiceOne.setFinishTime(LocalDateTime.now());
                    sendServiceOne.setStatus(CloseOrOpenEnum.OPEN.getValue());
                    followOrderSendService.updateById(sendServiceOne);

                    try {
                        Thread.sleep(200);
                        //删除缓存
                        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_SEND + traderId))) {
                            redisCache.delete(Constant.TRADER_SEND + traderId);
                        }
                        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_ORDER + traderId))) {
                            redisCache.delete(Constant.TRADER_ORDER + traderId);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                } catch (Exception e) {
                    log.error("间隔下单任务出错", e);
                }
            });
        }
    }


    private List<OrderActiveInfoVO> converOrderActive(List<Order> openedOrders, String account) {
        List<OrderActiveInfoVO> collect = new ArrayList<>();
        for (Order o : openedOrders) {
            OrderActiveInfoVO orderActiveInfoVO = new OrderActiveInfoVO(); // 从对象池中借用对象
            resetOrderActiveInfoVO(orderActiveInfoVO, o, account);
            collect.add(orderActiveInfoVO);
        }
        //顺序返回
        return collect.stream()
                .sorted(Comparator.comparing(OrderActiveInfoVO::getOpenTime))
                .collect(Collectors.toList());
    }

    private void resetOrderActiveInfoVO(OrderActiveInfoVO vo, Order order, String account) {
        vo.setAccount(account);
        vo.setLots(order.Lots);
        vo.setComment(order.Comment);
        vo.setOrderNo(order.Ticket);
        vo.setCommission(order.Commission);
        vo.setSwap(order.Swap);
        vo.setProfit(order.Profit);
        vo.setSymbol(order.Symbol);
        vo.setOpenPrice(order.OpenPrice);
        vo.setMagicNumber(order.MagicNumber);
        vo.setType(order.Type.name());
        vo.setOpenTime(order.OpenTime);
        vo.setStopLoss(order.StopLoss);
        vo.setTakeProfit(order.TakeProfit);
    }

    /**
     * 根据vpsId,排除新vps已经存在的
     * @param oldId 旧vpsID
     * @param newId 新vpdId
     * */
    @Override
    public List<Long> getShare(Integer oldId, Integer newId) {
        return baseMapper.getShare(oldId,newId);
    }

    @Override
    @Cacheable(
            value = "followFollowCache",
            key = "#masterId ?: 'defaultKey'",
            unless = "#result == null"
    )
    public FollowTraderEntity getFollowById(Long masterId) {
        return this.getById(masterId);
    }
    @Override
    public String getAccountCount(String serverName) {
        long count = this.count(new LambdaQueryWrapper<FollowTraderEntity>()
                .eq(FollowTraderEntity::getPlatform, serverName));
        return String.valueOf(count);
    }

    @Override
    public String getDefaultAccountCount(String serverName,String defaultServerNode) {
        long count = this.count(new LambdaQueryWrapper<FollowTraderEntity>()
                .eq(FollowTraderEntity::getPlatform, serverName)
                .eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue())
                .ne(FollowTraderEntity::getLoginNode,defaultServerNode));
        return String.valueOf(count);
    }

    @Override
    public IPage<DashboardAccountDataVO> getAccountDataPage(IPage<FollowTraderEntity> page, DashboardAccountQuery vo) {
        return baseMapper.getAccountDataPage(page,vo);
    }

    @Override
    public  List<FollowTraderCountVO> getAccountCounts() {
//        //我想查询serverName和所对应的账号数量
//        List<FollowTraderEntity> list = list();
//        for (FollowTraderEntity followTraderEntity : list) {
//            long count = this.count(new LambdaQueryWrapper<FollowTraderEntity>()
//                    .eq(FollowTraderEntity::getPlatform, followTraderEntity.getServerName())
//                    .eq(FollowTraderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue()));
//
//        }
        return baseMapper.getServerAccounts();


    }

    @Override
    public List<FollowTraderCountVO> getDefaultAccountCounts() {
        return baseMapper.getDefaultAccountCounts();
    }

    @Override
    public List<FollowTraderCountVO> getServerNodeCounts() {
        return baseMapper.getServerNodeCounts();
    }

    @Override
    public void getFollowRelation(FollowTraderEntity followTraderEntity, String account, String platform) {
        FollowPlatformEntity followPlatform= followPlatformService.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, platform));
        if (ObjectUtil.isNotEmpty(followPlatform)){
            addFollowRelation(followTraderEntity.getAccount()+"#"+followTraderEntity.getPlatformId(),account+"#"+followPlatform.getId());
        }else {
            throw new ServerException("平台异常");
        }
    }

    @Override
    public void removeRelation(FollowTraderEntity o, String account, Integer platformId) {
        removeFollowRelation(account+"#"+platformId,o.getAccount()+"#"+o.getPlatformId());
    }

    @Override
    public List<FollowSendAccountListVO> accountPage() {
        List<FollowSendAccountListVO> listVOArrayList = new ArrayList<>();
        List<FollowTraderEntity> list = this.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST).eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()));
        list.forEach(o->{
            FollowSendAccountListVO followSendAccountListVO = new FollowSendAccountListVO();
            followSendAccountListVO.setId(o.getId());
            followSendAccountListVO.setAccount(o.getAccount());
            List<FollowTraderSubscribeEntity> subscribeOrder = followTraderSubscribeService.getSubscribeOrder(o.getId());
            List<FollowSendAccountEntity> followSendAccountEntityList= new ArrayList<>();
            subscribeOrder.forEach(sub->{
                followSendAccountEntityList.add(FollowSendAccountEntity.builder().id(sub.getSlaveId()).masterId(sub.getMasterId()).account(sub.getSlaveAccount()).build());
            });
            followSendAccountListVO.setFollowSendAccountEntityList(followSendAccountEntityList);
            listVOArrayList.add(followSendAccountListVO);
        });
        return listVOArrayList;
    }

    @Override
    public List<FollowTraderEntity> listByServerName(String name) {
        //根据名称查询列表信息
        LambdaQueryWrapper<FollowTraderEntity> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(FollowTraderEntity::getPlatform, name)
                .eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST
                );
        // 执行查询并返回结果
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 添加跟单关系
     * @param callerId 喊单者ID
     * @param followerId 跟单者ID
     * @return 是否添加成功
     */
    public boolean addFollowRelation(String callerId, String followerId) {
        // 检查是否会形成环
        if (checkCycle(callerId, followerId)) {
            throw new ServerException("存在循环跟单,请检查");
        }

        // 将跟单关系存储到Redis
        String key = Constant.FOLLOW_RELATION_KEY + callerId;
        return redisUtil.sSet(key, followerId) > 0;
    }

    /**
     * 检查添加新的跟单关系是否会形成环
     * @param callerId 喊单者ID
     * @param followerId 跟单者ID
     * @return 是否会形成环
     */
    private boolean checkCycle(String callerId, String followerId) {
        Set<String> visited = new HashSet<>();
        return dfs(followerId, callerId, visited);
    }

    /**
     * 深度优先搜索检测环
     * @param current 当前节点
     * @param target 目标节点(原始喊单者)
     * @param visited 已访问节点集合
     * @return 是否存在环
     */
    private boolean dfs(String current, String target, Set<String> visited) {
        // 如果当前节点已经是目标节点，说明形成了环
        if (current.equals(target)) {
            return true;
        }

        // 将当前节点标记为已访问
        visited.add(current);

        // 获取当前节点的所有跟单关系
        String key = Constant.FOLLOW_RELATION_KEY + current;
        Set<Object> followers = redisUtil.sGet(key);

        if (followers != null) {
            // 遍历所有跟单关系
            for (Object follower : followers) {
                String followerId = follower.toString();
                if (!visited.contains(followerId)) {
                    if (dfs(followerId, target, visited)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 移除跟单关系
     * @param callerId 喊单者ID
     * @param followerId 跟单者ID
     * @return 移除的数量
     */
    public long removeFollowRelation(String callerId, String followerId) {
        String key = Constant.FOLLOW_RELATION_KEY + callerId;
        return redisUtil.setRemove(key, followerId);
    }
}