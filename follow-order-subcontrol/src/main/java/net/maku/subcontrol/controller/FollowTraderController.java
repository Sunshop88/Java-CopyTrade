package net.maku.subcontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.query.*;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.subcontrol.task.ObtainOrderHistoryTask;
import net.maku.subcontrol.task.SpeedTestTask;
import net.maku.subcontrol.trader.*;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 策略账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/subcontrol/trader")
@Tag(name = "mt4账号")
@AllArgsConstructor
public class FollowTraderController {
    private static final Logger log = LoggerFactory.getLogger(FollowTraderController.class);
    private final FollowTraderService followTraderService;
    private final FollowOrderSendService followOrderSendService;
    private final FollowPlatformService followPlatformService;
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final RedisCache redisCache;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final FollowOrderDetailService detailService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowVarietyService followVarietyService;
    private final FollowOrderCloseService followOrderCloseService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowVpsService followVpsService;
    private final CacheManager cacheManager;
    private final SpeedTestTask speedTestTask;

    private final ObtainOrderHistoryTask obtainOrderHistoryTask;
    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> page(@ParameterObject @Valid FollowTraderQuery query) {
        query.setServerIp(FollowConstant.LOCAL_HOST);
        PageResult<FollowTraderVO> page = followTraderService.page(query);
        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowTraderVO> get(@PathVariable("id") Long id) {
        FollowTraderVO data = followTraderService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> save(@RequestBody @Valid FollowTraderVO vo) {
        //默认模板最新的模板id
        if (ObjectUtil.isEmpty(vo.getTemplateId())) {
            vo.setTemplateId(followVarietyService.getBeginTemplateId());
        }
        //本机处理
        try {
            FollowTraderVO followTraderVO = followTraderService.save(vo);
            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVO);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(followTraderVO.getId()));
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                followTraderService.removeById(followTraderVO.getId());
                return Result.error("账号无法连接");
            }
            LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
            leaderApiTrader1.startTrade();
            //添加订单数据
            List<FollowTraderEntity> newList = new ArrayList<>();
            convert.setIsFirstSync(1);
            obtainOrderHistoryTask.update(convert,newList);
            ThreadPoolUtils.execute(() -> {
                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                leaderApiTradersAdmin.pushRedisData(followTraderVO,leaderApiTrader.quoteClient);
                leaderApiTrader.startTrade();
                followTraderService.saveQuo(leaderApiTrader.quoteClient, convert);
            });
        } catch (Exception e) {
            log.error("保存失败" + e);
            if (e instanceof ServerException) {
                throw e;
            } else {
                throw new ServerException(e.getMessage());
            }
        }
        return Result.ok();
    }


    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> update(@RequestBody @Valid FollowTraderVO vo) {
        if (ObjectUtil.isEmpty(vo.getTemplateId())) {
            vo.setTemplateId(followVarietyService.getBeginTemplateId());
        }
        followTraderService.update(vo);
        //重连
        Boolean reconnect = reconnect(vo.getId().toString());
        if (!reconnect){
            throw new ServerException("请检查账号密码，稍后再试");
        }
        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> delete(@RequestBody List<Long> idList) {
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId, idList));
        List<FollowTraderEntity> masterList = list.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).toList();
        List<FollowTraderEntity> slaveList = list.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).toList();
        if (ObjectUtil.isNotEmpty(masterList)) {
            //查看喊单账号是否存在用户
            List<FollowTraderSubscribeEntity> followTraderSubscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, masterList.stream().map(FollowTraderEntity::getId).toList()));
            if (ObjectUtil.isNotEmpty(followTraderSubscribeEntityList)) {
                throw new ServerException("请先删除跟单用户");
            }
        }
        followTraderService.delete(idList);

        //清空缓存
        list.stream().forEach(o ->{
            leaderApiTradersAdmin.removeTrader(o.getId().toString());
            copierApiTradersAdmin.removeTrader(o.getId().toString());
            redisCache.deleteByPattern(o.getId().toString());
            //账号缓存移除
            Cache cache = cacheManager.getCache("followFollowCache");
            if (cache != null) {
                cache.evict(o); // 移除指定缓存条目
            }
        });

        slaveList.forEach(o->{
            List<FollowTraderSubscribeEntity> followTraderSubscribeEntities = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, o.getId()));
            //跟单关系缓存删除
            followTraderSubscribeEntities.forEach(o1->{
                String cacheKey = generateCacheKey(o1.getSlaveId(), o1.getMasterId());
                Cache cache = cacheManager.getCache("followSubscriptionCache");
                if (cache != null) {
                    cache.evict(cacheKey); // 移除指定缓存条目
                }
            });
        });

        masterList.forEach(o->{
            //喊单关系缓存移除
            Cache cache = cacheManager.getCache("followSubOrderCache");
            if (cache != null) {
                cache.evict(o.getId()); // 移除指定缓存条目
            }
        });

        //删除订阅关系
        followTraderSubscribeService.remove(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, idList).or().in(FollowTraderSubscribeEntity::getSlaveId, idList));
        return Result.ok();
    }


    private String generateCacheKey(Long slaveId, Long masterId) {
        if (slaveId != null && masterId != null) {
            return slaveId + "_" + masterId;
        } else {
            return "defaultKey";
        }
    }

    @GetMapping("export")
    @Operation(summary = "导出")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public void export() {
        followTraderService.export();
    }

    @GetMapping("listSymbol/{id}")
    @Operation(summary = "账号品种列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<List<FollowSysmbolSpecificationEntity>> listSymbol(@PathVariable("id") Long traderId) {
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))) {
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>) redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        } else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION + traderId, followSysmbolSpecificationEntityList);
        }
        return Result.ok(followSysmbolSpecificationEntityList);
    }

    @GetMapping("listOrderSymbol")
    @Operation(summary = "订单品种列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<List<String>> listSymbol() {
        List<FollowOrderDetailEntity> collect = detailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().select(FollowOrderDetailEntity::getSymbol).eq(FollowOrderDetailEntity::getIpAddr,FollowConstant.LOCAL_HOST).groupBy(FollowOrderDetailEntity::getSymbol)).stream().toList();
        return Result.ok(collect.stream().map(FollowOrderDetailEntity::getSymbol).toList());
    }

    @PostMapping("orderSend")
    @Operation(summary = "下单")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?> orderSend(@RequestBody @Valid FollowOrderSendVO vo) {
        FollowTraderEntity followTraderVO = followTraderService.getById(vo.getTraderId());
        if (ObjectUtil.isEmpty(followTraderVO)) {
            return Result.error("账号不存在");
        }
        if (vo.getStartSize().compareTo(vo.getEndSize())>0) {
            return Result.error("开始手数不能大于结束手数");
        }
        //检查vps是否正常
        FollowVpsEntity followVpsEntity = followVpsService.getById(followTraderVO.getServerId());
     //   ||  followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue())
        if (  followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
            throw new ServerException("VPS服务异常，请检查");
        }

        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap()
                .get(vo.getTraderId().toString());
        QuoteClient quoteClient = null;

        if (ObjectUtil.isEmpty(leaderApiTrader) || ObjectUtil.isEmpty(leaderApiTrader.quoteClient)
                || !leaderApiTrader.quoteClient.Connected()) {
            leaderApiTradersAdmin.removeTrader(vo.getTraderId().toString());
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
            if (conCodeEnum == ConCodeEnum.SUCCESS) {
                quoteClient = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                leaderApiTrader1.startTrade();
            }else if (conCodeEnum == ConCodeEnum.AGAIN){
                //重复提交
                leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
                if (ObjectUtil.isNotEmpty(leaderApiTrader)) {
                    quoteClient = leaderApiTrader.quoteClient;
                }
            }else {
                return Result.error("账号无法登录");
            }
        } else {
            quoteClient = leaderApiTrader.quoteClient;
        }

        // 查看品种匹配 模板
        List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(leaderApiTrader.getTrader().getTemplateId());
        Integer contract = followVarietyEntityList.stream().filter(o -> ObjectUtil.isNotEmpty(o.getStdSymbol()) && o.getStdSymbol().equals(vo.getSymbol())).findFirst()
                .map(FollowVarietyEntity::getStdContract)
                .orElse(0);

        String symbol1 = getSymbol(leaderApiTrader,quoteClient,vo.getTraderId(), vo.getSymbol());
        vo.setSymbol(symbol1);
        log.info("标准合约大小{}", contract);
        try {
            double ask = getQuoteOrRetry(quoteClient, vo.getSymbol());
        } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
            return Result.error(followTraderVO.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种");
        } catch (InterruptedException e) {
            return Result.error(followTraderVO.getAccount() + " 操作被中断");
        }

        boolean result = followTraderService.orderSend(vo, quoteClient, FollowTraderConvert.INSTANCE.convert(followTraderVO), contract);
        if (!result) {
            return Result.error(followTraderVO.getAccount() + "下单失败,该账号正在下单中");
        }
        return Result.ok(true);
    }

    @GetMapping("orderSendList")
    @Operation(summary = "下单列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderSendVO>> orderSendList(@ParameterObject @Valid FollowOrderSendQuery query) {
        PageResult<FollowOrderSendVO> page = followOrderSendService.page(query);
        return Result.ok(page);
    }

    @GetMapping("orderSlipPoint")
    @Operation(summary = "滑点分析列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderSlipPointVO>> orderSlipPoint(@ParameterObject @Valid FollowOrderSpliListQuery query) {

        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
            query.setSymbolList(Arrays.asList(query.getSymbol().split(",")));
        }
        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            query.setTraderIdList(Arrays.asList(query.getTraderId().split(",")));
        }
        PageResult<FollowOrderSlipPointVO> followOrderSlipPointVOPageResult = followTraderService.pageSlipPoint(query);
        Integer total = followOrderSlipPointVOPageResult.getList().stream().mapToInt(FollowOrderSlipPointVO::getSymbolNum).sum();
        followOrderSlipPointVOPageResult.getList().stream().forEach(o -> o.setTotalNum(total));
        List<FollowOrderSlipPointVO> collect = followOrderSlipPointVOPageResult.getList().stream().sorted(Comparator.comparing(FollowOrderSlipPointVO::getTraderId)).collect(Collectors.toList());
        followOrderSlipPointVOPageResult.setList(collect);
        return Result.ok(followOrderSlipPointVOPageResult);
    }

    @GetMapping("orderSlipDetail")
    @Operation(summary = "订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderDetailVO>> orderSlipDetail(@ParameterObject @Valid FollowOrderSendQuery query) {
        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        return Result.ok(followOrderDetailVOPageResult);
    }

    @GetMapping("orderDoing/{traderId}")
    @Operation(summary = "正在进行订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowOrderSendEntity> orderDoing(@PathVariable("traderId") Long traderId) {
        return Result.ok(followTraderService.orderDoing(traderId));
    }

    @PostMapping("orderClose")
    @Operation(summary = "平仓")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> orderClose(@RequestBody @Valid FollowOrderSendCloseVO vo) {
        if(vo.getIsCloseAll() ==null || vo.getIsCloseAll()== TraderRepairEnum.SEND.getType()){
            checkParams(vo);
        }
        FollowTraderEntity followTraderVO = followTraderService.getById(vo.getTraderId());
        if (ObjectUtil.isEmpty(followTraderVO)) {
            throw new ServerException("账号不存在");
        }
        //检查vps是否正常
        FollowVpsEntity followVpsEntity = followVpsService.getById(followTraderVO.getServerId());
        if (followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
            throw new ServerException("VPS服务异常，请检查");
        }
        AbstractApiTrader abstractApiTrader;
        QuoteClient quoteClient = null;
        if (followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
            abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(vo.getTraderId().toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                    quoteClient=leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    leaderApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    //重复提交
                    abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
                    if (ObjectUtil.isNotEmpty(abstractApiTrader)) {
                        quoteClient = abstractApiTrader.quoteClient;
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }else {
            abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(followTraderVO.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient=copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    copierApiTrader1.setTrader(followTraderVO);
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    //重复提交
                    CopierApiTrader copierApiTrader1  = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    if (ObjectUtil.isNotEmpty(copierApiTrader1)){
                        quoteClient = copierApiTrader1.quoteClient;
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }
        //获取vps数据
        if (ObjectUtil.isEmpty(quoteClient)){
            throw new ServerException(vo.getTraderId()+"登录异常");
        }

        if (ObjectUtil.isNotEmpty(vo.getSymbol())) {
            String symbol1 = getSymbol(abstractApiTrader,quoteClient,vo.getTraderId(), vo.getSymbol());
            vo.setSymbol(symbol1);
            try {
                // 获取报价信息
                double ask = getQuoteOrRetry(quoteClient, vo.getSymbol());
            } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
                throw new ServerException(vo.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种", e);
            } catch (InterruptedException e) {
                throw new ServerException(vo.getAccount() + " 操作被中断", e);
            }
        }
        boolean result = followTraderService.orderClose(vo, quoteClient);
        if (!result) {
            return Result.error(followTraderVO.getAccount() + "正在平仓,请稍后再试");
        }
        return Result.ok(true);
    }

    @GetMapping("traderOverview")
    @Operation(summary = "数据概览")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<TraderOverviewVO> traderOverview(HttpServletRequest request) {
        String serverIp = FollowConstant.LOCAL_HOST;
        return Result.ok(followTraderService.traderOverview(serverIp));
    }


    @GetMapping("exportOrderDetail")
    @Operation(summary = "导出订单")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Object> exportOrderDetail(@ParameterObject @Valid FollowOrderSendQuery query) {
        //设置查询数量最高100000条
        query.setPage(1);
        query.setLimit(100000);
        //处理平台查询逻辑，找出相关账号查询
        List<Long> collectPlat = new ArrayList<>();
        List<Long> collectBroke = new ArrayList<>();
        //处理券商查询逻辑，找出相关账号查询
        if (ObjectUtil.isNotEmpty(query.getBrokeName())) {
            List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(query.getBrokeName());
            if (ObjectUtil.isNotEmpty(serverEntityList)) {
                List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getPlatform, serverEntityList.stream().map(FollowBrokeServerEntity::getServerName).collect(Collectors.toList())));
                collectBroke = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
            }
        }
        if (ObjectUtil.isNotEmpty(query.getPlatform())) {
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getPlatform, query.getPlatform()));
            collectPlat = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
        }
        // 计算交集
        if (ObjectUtil.isNotEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat.retainAll(collectBroke); // collectPlat 会变成 collectPlat 和 collectBroke 的交集
        } else if (ObjectUtil.isEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat = collectBroke;
        }
        if (ObjectUtil.isNotEmpty(collectPlat)) {
            query.setTraderIdList(collectPlat);
        }

        if (ObjectUtil.isEmpty(query.getStartTime()) || ObjectUtil.isEmpty(query.getEndTime())) {
            return Result.error("开仓时间为必填项");
        }
        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        detailService.export(followOrderDetailVOPageResult.getList());
        return Result.ok();
    }

    @GetMapping("stopOrder")
    @Operation(summary = "停止下单/平仓")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> stopOrder(@Parameter(description = "type") Integer type, @Parameter(description = "traderId") String traderId) {
        Boolean result = followTraderService.stopOrder(type, traderId);
        if (!result) {
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            return Result.error(followTraderEntity.getAccount() + "暂无进行中下单/平仓操作");
        }
        return Result.ok();
    }

    @GetMapping("reconnection")
    @Operation(summary = "重连账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> reconnection(@Parameter(description = "traderId") String traderId) {
        Boolean reconnect = reconnect(traderId);
        if (!reconnect){
            throw new ServerException("请检查账号密码，稍后再试");
        }
        return Result.ok();
    }
    @GetMapping("/specificationList")
    @Operation(summary = "品种规格列表")
    public Result<PageResult<FollowSysmbolSpecificationVO>> page(@ParameterObject @Valid FollowSysmbolSpecificationQuery query) {
        PageResult<FollowSysmbolSpecificationVO> page = followSysmbolSpecificationService.page(query);
        return Result.ok(page);
    }

    private Boolean reconnect(String traderId) {
        Boolean result=false;
        FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
        if (followTraderEntity.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
            leaderApiTradersAdmin.removeTrader(traderId);
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(traderId));
            if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("喊单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            }  else if (conCodeEnum == ConCodeEnum.AGAIN){
                log.info("喊单者:[{}-{}-{}]启动重复", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
            } else {
                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                leaderApiTrader.startTrade();
                result=true;
            }
        }else {
            copierApiTradersAdmin.removeTrader(traderId);
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderService.getById(traderId));
            if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("跟单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            } else if (conCodeEnum == ConCodeEnum.AGAIN){
                log.info("跟单者:[{}-{}-{}]启动重复", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
            }  else {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                copierApiTrader.startTrade();
                result=true;
            }
        }

        return result;
    }

//    @GetMapping("traderSymbol")
//    @Operation(summary = "获取对应Symbol")
//    @PreAuthorize("hasAuthority('mascontrol:trader')")
//    public Result<String> traderSymbol(@Parameter(description = "symbol") String symbol, @Parameter(description = "traderId") Long traderId) {
//        String symbol1 = getSymbol(traderId, symbol);
//        return Result.ok(symbol1);
//    }

    @GetMapping("orderCloseList")
    @Operation(summary = "平仓列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderCloseVO>> orderSendList(@ParameterObject @Valid FollowOrderCloseQuery query) {
        PageResult<FollowOrderCloseVO> page = followOrderCloseService.page(query);
        return Result.ok(page);
    }

    private void  checkParams(FollowOrderSendCloseVO vo){
//        if (vo.getNum()==null && vo.getFlag().equals( TraderRepairEnum.SEND.getType())) {
//            throw new ServerException("总单数最少一单");
//        }
        if(ObjectUtil.isEmpty(vo.getSymbol()) && vo.getFlag().equals( CloseOrOpenEnum.CLOSE.getValue())){
            throw  new ServerException("品种不能为空");
        }
        if(ObjectUtil.isEmpty(vo.getType()) && vo.getFlag().equals( CloseOrOpenEnum.CLOSE.getValue())){
            throw  new ServerException("订单方向不能为空");
        }
        if(ObjectUtil.isEmpty(vo.getFlag())){
            throw  new ServerException("是否全平不能为空");
        }
    }

    private String getSymbol(AbstractApiTrader leaderApiTrader,QuoteClient quoteClient,Long traderId, String symbol) {

        //查询平台信息
        FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(followTraderService.getFollowById(traderId).getPlatformId().toString());
        //获取symbol信息
        Map<String, FollowSysmbolSpecificationEntity> specificationServiceByTraderId = followSysmbolSpecificationService.getByTraderId(traderId);

        FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
        if (ObjectUtil.isNotEmpty(symbol)) {
            //查看品种列表
            // 查看品种匹配 模板
            List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(followTraderEntity.getTemplateId());
            List<FollowVarietyEntity> list = followVarietyEntityList.stream().filter(o ->ObjectUtil.isNotEmpty(o.getBrokerName())&& o.getBrokerName().equals(followPlatform.getBrokerName()) && o.getStdSymbol().equals(symbol)).toList();
            QuoteEventArgs eventArgs;
            for (FollowVarietyEntity o : list) {
                if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())) {
                    //查看品种规格
                    if (ObjectUtil.isNotEmpty(specificationServiceByTraderId.get(o.getBrokerSymbol()))) {
                        //获取报价
                        eventArgs= getEventArgs(leaderApiTrader,quoteClient,o.getBrokerSymbol());
                        if (ObjectUtil.isNotEmpty(eventArgs)){
                            return o.getBrokerSymbol();
                        }
                    }
                }
            }
        }
        return symbol;
    }

    private QuoteEventArgs getEventArgs(AbstractApiTrader leaderApiTrader,QuoteClient quoteClient,String symbol){
        QuoteEventArgs eventArgs = null;
        try {
            if (ObjectUtil.isEmpty(leaderApiTrader.quoteClient.GetQuote(symbol))){
                //订阅
                leaderApiTrader.quoteClient.Subscribe(symbol);
            }
            while (eventArgs==null && quoteClient.Connected()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                eventArgs=quoteClient.GetQuote(symbol);
            }
            eventArgs = leaderApiTrader.quoteClient.GetQuote(symbol);
            return eventArgs;
        }catch (InvalidSymbolException | TimeoutException | ConnectException e) {
            log.info("获取报价失败,品种不正确,请先配置品种");
            return eventArgs;
        }
    }

    private double getQuoteOrRetry(QuoteClient quoteClient, String symbol) throws InvalidSymbolException, TimeoutException, ConnectException, InterruptedException {
        double ask = -1; // 或者其他合适的默认值
        int maxRetries = 5; // 最大重试次数
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                // 检查报价并订阅
                QuoteEventArgs quote = quoteClient.GetQuote(symbol);
                if (ObjectUtil.isEmpty(quote)) {
                    quoteClient.Subscribe(symbol);
                    attempts++;
                    Thread.sleep(100); // 等待一段时间再重试
                    continue; // 继续下一个尝试
                }
                ask = quote.Ask;
                break; // 成功获取报价，退出循环
            } catch (NullPointerException e) {
                attempts++;
                if (attempts < maxRetries) {
                    Thread.sleep(100); // 等待后再重试
                } else {
                    throw new ServerException("获取报价失败，达到最大重试次数", e);
                }
            }
        }
        if (ask < 0) {
            throw new ServerException("无法获取有效报价");
        }
        return ask;
    }

    @PostMapping("reconnectionServer")
    @Operation(summary = "重连服务器账号")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<Map<String, Boolean>> reconnectionServer(@RequestBody FollowPlatformQuery query) {
        // 查询serverName中的所有信息
        List<FollowTraderEntity> list = followTraderService.listByServerName(query.getServer());
        log.info("查询到serverName为{}的账号数为{}", query.getServer(), list.size());
        Map<String, Boolean> reconnectResults = new HashMap<>();

        for (FollowTraderEntity followTraderEntity : list) {
            String traderId = followTraderEntity.getId().toString();
            Boolean reconnect = reconnect(traderId);
            reconnectResults.put(traderId, reconnect);
        }

        return Result.ok(reconnectResults);
    }

    @PostMapping("test")
    @Operation(summary = "手动定时测速任务")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> test() throws IOException {
        speedTestTask.weeklySpeedTest();
        return Result.ok("手动测速成功");
    }

    @PostMapping("/synchData/{traderId}")
    @Operation(summary = "同步数据")
    public Result<String> synchData(@PathVariable("traderId") Long traderId) throws IOException {
        if(traderId==null){
            throw new ServerException("账号id不能为空");
        }
        FollowTraderEntity traderEntity = followTraderService.getById(traderId);
        if(traderEntity==null){
            throw new ServerException("账号id不存在");
        }
        List<FollowTraderEntity> newList = new ArrayList<>();
        obtainOrderHistoryTask.update(traderEntity,newList);
        return Result.ok("同步成功");
    }
}