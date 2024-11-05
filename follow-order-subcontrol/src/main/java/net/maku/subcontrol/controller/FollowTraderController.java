package net.maku.subcontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.query.FollowOrderCloseQuery;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.query.FollowTraderQuery;
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
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.util.RestUtil;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/subcontrol/trader")
@Tag(name="mt4账号")
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

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> page(@ParameterObject @Valid FollowTraderQuery query){
        query.setServerIp(FollowConstant.LOCAL_HOST);
        PageResult<FollowTraderVO> page = followTraderService.page(query);
        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowTraderVO> get(@PathVariable("id") Long id){
        FollowTraderVO data = followTraderService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> save(@RequestBody FollowTraderVO vo){
        String serverIp = FollowConstant.LOCAL_HOST;
        log.info("localhost{}",serverIp);
        vo.setServerIp(serverIp);
        if (ObjectUtil.isEmpty(vo.getPlatform())){
            throw new ServerException("服务商错误");
        }
        List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(vo.getPlatform());
        if (ObjectUtil.isEmpty(serverEntityList)){
            throw new ServerException("暂无可用服务器商");
        }
        //查看是否已存在该账号
        FollowTraderEntity followTraderEntity = followTraderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, vo.getAccount()).eq(FollowTraderEntity::getPlatform, vo.getPlatform()).eq(FollowTraderEntity::getIpAddr,serverIp));
        if (ObjectUtil.isNotEmpty(followTraderEntity)){
            throw new ServerException("该账号已存在");
        }
        //本机处理
        try {
            FollowTraderVO followTraderVO = followTraderService.save(vo);
            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(vo);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(convert);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)){
                followTraderService.removeById(followTraderVO.getId());
                return Result.error();
            }
            ThreadPoolUtils.execute(()->{
                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                leaderApiTrader.startTrade();
                followTraderService.saveQuo(leaderApiTrader.quoteClient,convert);
            });
        }catch (Exception e){
            log.error("保存失败"+e);
        }

        return Result.ok();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> update(@RequestBody @Valid FollowTraderVO vo){
        followTraderService.update(vo);

        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> delete(@RequestBody List<Long> idList){
        followTraderService.delete(idList);
        //清空缓存
        idList.stream().forEach(o-> leaderApiTradersAdmin.removeTrader(o.toString()));
        return Result.ok();
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
    public Result<List<FollowSysmbolSpecificationEntity> > listSymbol(@PathVariable("id") Long traderId){
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))){
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>)redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        }else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION+traderId,followSysmbolSpecificationEntityList);
        }
        return Result.ok(followSysmbolSpecificationEntityList);
    }

    @GetMapping("listOrderSymbol")
    @Operation(summary = "订单品种列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<List<String> > listSymbol(){
        List<String> collect = detailService.list().stream().map(FollowOrderDetailEntity::getSymbol).distinct().collect(Collectors.toList());
        return Result.ok(collect);
    }

    @PostMapping("orderSend")
    @Operation(summary = "下单")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?> orderSend(@RequestBody @Valid FollowOrderSendVO vo) {
        if ((ObjectUtil.isNotEmpty(vo.getStartSize())&&vo.getStartSize().compareTo(new BigDecimal("0.01"))<0)||(ObjectUtil.isNotEmpty(vo.getEndSize())&&vo.getEndSize().compareTo(new BigDecimal("0.01"))<0)){
            return  Result.error("区间值不小于0.01");
        }
        // 本地处理逻辑
        FollowTraderVO followTraderVO = followTraderService.get(vo.getTraderId());
        if (ObjectUtil.isEmpty(followTraderVO)) {
            return Result.error("账号不存在");
        }

        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap()
                .get(vo.getTraderId().toString());
        QuoteClient quoteClient = null;

        if (ObjectUtil.isEmpty(leaderApiTrader) || ObjectUtil.isEmpty(leaderApiTrader.quoteClient)
                || !leaderApiTrader.quoteClient.Connected()) {
            quoteClient = followPlatformService.tologin(
                    followTraderVO.getAccount(),
                    followTraderVO.getPassword(),
                    followTraderVO.getPlatform()
            );
            if (ObjectUtil.isEmpty(quoteClient)) {
                return Result.error("账号无法登录");
            }
        } else {
            quoteClient = leaderApiTrader.quoteClient;
        }

        Integer contract = followVarietyService.list(
                        new LambdaQueryWrapper<FollowVarietyEntity>()
                                .eq(FollowVarietyEntity::getStdSymbol, vo.getSymbol()))
                .stream().filter(o->ObjectUtil.isNotEmpty(o.getStdSymbol())).findFirst()
                .map(FollowVarietyEntity::getStdContract)
                .orElse(0);

        String symbol1 = getSymbol(vo.getTraderId(), vo.getSymbol());
        vo.setSymbol(symbol1);
        log.info("标准合约大小{}",contract);
        try {
            double ask = getQuoteOrRetry(quoteClient, vo.getSymbol());
        } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
            return Result.error(followTraderVO.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种");
        } catch (InterruptedException e) {
            return Result.error(followTraderVO.getAccount() + " 操作被中断");
        }

        boolean result = followTraderService.orderSend(vo, quoteClient, followTraderVO, contract);
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
        return  Result.ok(page);
    }

    @GetMapping("orderSlipPoint")
    @Operation(summary = "滑点分析列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public  Result<PageResult<FollowOrderSlipPointVO>>  orderSlipPoint(@ParameterObject @Valid FollowOrderSpliListQuery query) {

        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
            query.setSymbolList(Arrays.asList(query.getSymbol().split(",")));
        }
        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            query.setTraderIdList(Arrays.asList(query.getTraderId().split(",")));
        }
        PageResult<FollowOrderSlipPointVO> followOrderSlipPointVOPageResult = followTraderService.pageSlipPoint(query);
        Integer total = followOrderSlipPointVOPageResult.getList().stream().mapToInt(FollowOrderSlipPointVO::getSymbolNum).sum();;
        followOrderSlipPointVOPageResult.getList().stream().forEach(o->o.setTotalNum(total));
        List<FollowOrderSlipPointVO> collect = followOrderSlipPointVOPageResult.getList().stream().sorted(Comparator.comparing(FollowOrderSlipPointVO::getTraderId)).collect(Collectors.toList());
        followOrderSlipPointVOPageResult.setList(collect);
        return Result.ok(followOrderSlipPointVOPageResult);
    }

    @GetMapping("orderSlipDetail")
    @Operation(summary = "订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderDetailVO>>  orderSlipDetail(@ParameterObject @Valid FollowOrderSendQuery query) {
//        if (ObjectUtil.isEmpty(query.getStartTime())|| ObjectUtil.isEmpty(query.getEndTime())) {
//            return Result.ok();
//        }

        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        return  Result.ok(followOrderDetailVOPageResult);
    }

    @GetMapping("orderDoing/{traderId}")
    @Operation(summary = "正在进行订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowOrderSendEntity>  orderDoing(@PathVariable("traderId") Long traderId) {
        return  Result.ok(followTraderService.orderDoing(traderId));
    }

    @PostMapping("orderClose")
    @Operation(summary = "平仓")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> orderClose(@RequestBody FollowOrderSendCloseVO vo){
        FollowTraderVO followTraderVO = followTraderService.get(vo.getTraderId());
        if (ObjectUtil.isEmpty(followTraderVO)){
            throw new ServerException("账号不存在");
        }

        LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
        QuoteClient quoteClient = null;
        if (ObjectUtil.isEmpty(leaderApiTrader)||ObjectUtil.isEmpty(leaderApiTrader.quoteClient)||!leaderApiTrader.quoteClient.Connected()){
            quoteClient = followPlatformService.tologin(followTraderVO.getAccount(), followTraderVO.getPassword(), followTraderVO.getPlatform());
            if (ObjectUtil.isEmpty(quoteClient)){
                throw new ServerException("账号无法登录");
            }
        }else {
            quoteClient=leaderApiTrader.quoteClient;
        }

        if (ObjectUtil.isNotEmpty(vo.getSymbol())){
            String symbol1 = getSymbol(vo.getTraderId(), vo.getSymbol());
            vo.setSymbol(symbol1);
            try {
                // 获取报价信息
                double ask = getQuoteOrRetry(quoteClient,vo.getSymbol());
            } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
                throw new ServerException(vo.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种", e);
            } catch (InterruptedException e) {
                throw new ServerException(vo.getAccount() + " 操作被中断", e);
            }
        }
        boolean result = followTraderService.orderClose(vo,quoteClient);
        if (!result){
            return Result.error(followTraderVO.getAccount()+"正在平仓,请稍后再试");
        }
        return Result.ok(true);
    }

    @GetMapping("traderOverview")
    @Operation(summary = "数据概览")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<TraderOverviewVO>  traderOverview(HttpServletRequest request) {
        String serverIp = (String) request.getAttribute("serverIp");
        return  Result.ok(followTraderService.traderOverview(serverIp));
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
        List<Long> collectPlat=new ArrayList<>() ;
        List<Long> collectBroke=new ArrayList<>() ;
        //处理券商查询逻辑，找出相关账号查询
        if (ObjectUtil.isNotEmpty(query.getBrokeName())){
            List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(query.getBrokeName());
            if (ObjectUtil.isNotEmpty(serverEntityList)){
                List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getPlatform, serverEntityList.stream().map(FollowBrokeServerEntity::getServerName).collect(Collectors.toList())));
                collectBroke = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
            }
        }
        if (ObjectUtil.isNotEmpty(query.getPlatform())){
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getPlatform, query.getPlatform()));
            collectPlat = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
        }
        // 计算交集
        if (ObjectUtil.isNotEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat.retainAll(collectBroke); // collectPlat 会变成 collectPlat 和 collectBroke 的交集
        }else  if (ObjectUtil.isEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat=collectBroke;
        }
        if (ObjectUtil.isNotEmpty(collectPlat)){
            query.setTraderIdList(collectPlat);
        }

        if (ObjectUtil.isEmpty(query.getStartTime())|| ObjectUtil.isEmpty(query.getEndTime())) {
            return Result.error("开仓时间为必填项");
        }
        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        detailService.export(followOrderDetailVOPageResult.getList());
        return Result.ok();
    }

    @GetMapping("stopOrder")
    @Operation(summary = "停止下单/平仓")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> stopOrder(@Parameter(description = "type") Integer type, @Parameter(description = "traderId") String traderId) {
        Boolean result = followTraderService.stopOrder(type, traderId);
        if (!result){
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            return Result.error(followTraderEntity.getAccount()+"暂无进行中下单/平仓操作");
        }
        return Result.ok();
    }

    @GetMapping("reconnection")
    @Operation(summary = "重连账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> reconnection(@Parameter(description = "traderId") String traderId) {
        try{
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(traderId));
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
            if (conCodeEnum != ConCodeEnum.SUCCESS && !followTraderEntity.getStatus().equals(TraderStatusEnum.ERROR.getValue())) {
                followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                followTraderService.updateById(followTraderEntity);
                log.error("喊单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                throw new ServerException("重连失败");
            } else {
                log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                leaderApiTrader.startTrade();
            }
        }catch (RuntimeException e){
            throw new ServerException("请检查账号密码，稍后再试");
        }
        return Result.ok();
    }

    @GetMapping("traderSymbol")
    @Operation(summary = "获取对应Symbol")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String>  traderSymbol(@Parameter(description = "symbol") String symbol, @Parameter(description = "traderId") Long traderId) {
        String symbol1 = getSymbol(traderId, symbol);
        return  Result.ok(symbol1);
    }

    @GetMapping("orderCloseList")
    @Operation(summary = "平仓列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderCloseVO>> orderSendList(@ParameterObject @Valid FollowOrderCloseQuery query) {
        PageResult<FollowOrderCloseVO> page = followOrderCloseService.page(query);
        return  Result.ok(page);
    }

    private String getSymbol(Long traderId,String symbol){
        FollowTraderVO followTraderVO = followTraderService.get(traderId);
        //查询平台信息
        FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderVO.getPlatformId());
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))){
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>)redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        }else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION+traderId,followSysmbolSpecificationEntityList);
        }

        if (ObjectUtil.isNotEmpty(symbol)){
            //查看品种列表
            List<FollowVarietyEntity> list = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getBrokerName, followPlatform.getBrokerName()).eq(FollowVarietyEntity::getStdSymbol, symbol));
            for (FollowVarietyEntity o:list){
                if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                    //查看品种规格
                    Optional<FollowSysmbolSpecificationEntity> specificationEntity = followSysmbolSpecificationEntityList.stream().filter(fl -> ObjectUtil.equals(o.getBrokerSymbol(), fl.getSymbol())).findFirst();
                    if (specificationEntity.isPresent()){
                        return o.getBrokerSymbol();
                    }
                }
            }
        }
        return symbol;
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
}