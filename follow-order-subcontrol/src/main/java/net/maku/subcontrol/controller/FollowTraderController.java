package net.maku.subcontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.*;
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
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> page(@ParameterObject @Valid FollowTraderQuery query){
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
        if (ObjectUtil.isEmpty(vo.getPlatform())){
            throw new ServerException("服务商错误");
        }
        List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(vo.getPlatform());
        if (ObjectUtil.isEmpty(serverEntityList)){
            throw new ServerException("暂无可用服务器商");
        }
        //查看是否已存在该账号
        FollowTraderEntity followTraderEntity = followTraderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, vo.getAccount()).eq(FollowTraderEntity::getPlatform, vo.getPlatform()));
        if (ObjectUtil.isNotEmpty(followTraderEntity)){
            throw new ServerException("该账号已存在");
        }
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
    public Result<Boolean> orderSend(@RequestBody FollowOrderSendVO vo){
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

        //查询平台信息
        FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderVO.getPlatformId());
        //查看品种列表
        List<FollowVarietyEntity> list = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getBrokerName, followPlatform.getBrokerName()).eq(FollowVarietyEntity::getStdSymbol, vo.getSymbol()));
        for (FollowVarietyEntity o:list){
            if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                try {
                    quoteClient.Subscribe(o.getBrokerSymbol());
                    vo.setSymbol(o.getBrokerSymbol());
                    break;
                }catch (Exception e) {
                    log.error("订阅失败: " + e.getMessage());
                }
            }
        }

        try {
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(vo.getSymbol()))){
                //订阅
                quoteClient.Subscribe(vo.getSymbol());
            }
            double ask = quoteClient.GetQuote(vo.getSymbol()).Ask;
        } catch (InvalidSymbolException e) {
            throw new ServerException(followTraderVO.getAccount()+"品种不正确,请先配置品种");
        } catch (RuntimeException e) {
            throw new ServerException(followTraderVO.getAccount()+"该品种未订阅成功");
        } catch (TimeoutException e) {
            throw new ServerException(followTraderVO.getAccount()+"订阅品种超时");
        } catch (ConnectException e) {
            throw new RuntimeException(followTraderVO.getAccount()+"订阅品种失败");
        }
        boolean result = followTraderService.orderSend(vo,quoteClient,followTraderVO);
        if (!result){
            return Result.error(followTraderVO.getAccount()+"下单失败");
        }
        return Result.ok(result);
    }

    @GetMapping("orderSendList")
    @Operation(summary = "下单列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderSendVO>> orderSendList(@ParameterObject @Valid FollowOrderSendQuery query) {
        PageResult<FollowOrderSendVO> page = followOrderSendService.page(query);
        page.getList().stream().forEach(o-> {
            FollowTraderEntity followTraderEntity = followTraderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getId, o.getTraderId()));
            o.setPlatform(followTraderEntity.getPlatform());
            FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderEntity.getPlatformId());
            o.setBrokeName(followPlatform.getBrokerName());
        });
        return  Result.ok(page);
    }

    @GetMapping("orderSlipPoint")
    @Operation(summary = "滑点分析列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public  Result<PageResult<FollowOrderSlipPointVO>>  orderSlipPoint(@ParameterObject @Valid FollowOrderSpliListQuery query) {
        //处理平台查询逻辑，找出相关账号查询
        if (ObjectUtil.isNotEmpty(query.getPlatform())){
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getPlatform, query.getPlatform()));
            List<String> collect = list.stream().map(entity -> String.valueOf(entity.getId())).collect(Collectors.toList());
            String traderId = collect.stream().collect(Collectors.joining(","));
            if (ObjectUtil.isEmpty(query.getTraderId())){
                query.setTraderId(traderId);
            }else {
                query.setTraderId(traderId+","+query.getTraderId());
            }
        }
        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
            query.setSymbolList(Arrays.asList(query.getSymbol().split(",")));
        }
        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            query.setTraderIdList(Arrays.asList(query.getTraderId().split(",")));
        }
        PageResult<FollowOrderSlipPointVO> followOrderSlipPointVOPageResult = followTraderService.pageSlipPoint(query);
        Integer total = followOrderSlipPointVOPageResult.getList().stream().mapToInt(FollowOrderSlipPointVO::getSymbolNum).sum();;
        followOrderSlipPointVOPageResult.getList().stream().forEach(o->{
            FollowTraderEntity followTraderEntity = followTraderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getId, o.getTraderId()));
            o.setAccount(followTraderEntity.getAccount());
            o.setPlatform(followTraderEntity.getPlatform());
            o.setTraderId(o.getTraderId());
            FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderEntity.getPlatformId());
            o.setBrokeName(followPlatform.getBrokerName());
            o.setTotalNum(total);
        });
        List<FollowOrderSlipPointVO> collect = followOrderSlipPointVOPageResult.getList().stream().sorted(Comparator.comparing(FollowOrderSlipPointVO::getTraderId)).collect(Collectors.toList());
        followOrderSlipPointVOPageResult.setList(collect);
        return Result.ok(followOrderSlipPointVOPageResult);
    }

    @GetMapping("orderSlipDetail")
    @Operation(summary = "订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderDetailVO>>  orderSlipDetail(@ParameterObject @Valid FollowOrderSendQuery query) {
        //处理平台查询逻辑，找出相关账号查询
        List<Long> collectPlat=new ArrayList<>() ;
        List<Long> collectBroke=new ArrayList<>() ;
        //处理券商查询逻辑，找出相关账号查询
        if (ObjectUtil.isNotEmpty(query.getBrokeName())){
            String[] split = query.getBrokeName().split(",");
            List<FollowPlatformEntity> serverEntityList = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().in(FollowPlatformEntity::getBrokerName,Arrays.asList(split)));
            if (ObjectUtil.isNotEmpty(serverEntityList)){
                List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getPlatformId, serverEntityList.stream().map(FollowPlatformEntity::getId).collect(Collectors.toList())));
                collectBroke = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
            }
            if (ObjectUtil.isEmpty(collectBroke)) {
                return Result.ok(new PageResult<>(new ArrayList<>(), 0)); // 返回空结果
            }
        }
        if (ObjectUtil.isNotEmpty(query.getPlatform())){
            String[] split = query.getPlatform().split(",");
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getPlatform, Arrays.asList(split)));
            collectPlat = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
            if (ObjectUtil.isEmpty(collectPlat)) {
                return Result.ok(new PageResult<>(new ArrayList<>(), 0)); // 返回空结果
            }
        }
        // 计算交集
        if (ObjectUtil.isNotEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat.retainAll(collectBroke); // collectPlat 会变成 collectPlat 和 collectBroke 的交集
        }else if (ObjectUtil.isEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat=collectBroke;
        }
        if (ObjectUtil.isNotEmpty(collectPlat)){
            query.setTraderIdList(collectPlat);
        }

        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        //查看券商和服务器
        followOrderDetailVOPageResult.getList().parallelStream().forEach(o->{
            FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderService.getById(o.getTraderId()).getPlatformId());
            o.setBrokeName(followPlatform.getBrokerName());
            o.setPlatform(followPlatform.getServer());
        });
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
    public Result<Boolean> orderClose(@RequestBody FollowOrderCloseVO vo){
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
            String symbol=vo.getSymbol();  //查询平台信息
            FollowTraderEntity followTraderEntity = followTraderService.getOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getId, vo.getTraderId()));
            FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderEntity.getPlatformId());
            List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.list(new LambdaQueryWrapper<FollowVarietyEntity>().eq(FollowVarietyEntity::getBrokerName, followPlatform.getBrokerName()).eq(FollowVarietyEntity::getStdSymbol, symbol));
            for (FollowVarietyEntity o:followVarietyEntityList){
                if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())){
                    symbol=o.getBrokerSymbol();
                }
                try {
                    quoteClient.Subscribe(symbol);
                    break;
                }catch (InvalidSymbolException | TimeoutException | ConnectException  e) {
                    throw new ServerException(followTraderVO.getAccount()+"获取报价失败,品种不正确,请先配置品种");
                }
            }
        }
        boolean result = followTraderService.orderClose(vo,quoteClient);
        if (!result){
            return Result.error(followTraderVO.getAccount()+"平仓失败");
        }
        return Result.ok(result);
    }

    @GetMapping("traderOverview")
    @Operation(summary = "数据概览")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<TraderOverviewVO>  traderOverview() {
        return  Result.ok(followTraderService.traderOverview());
    }


    @GetMapping("exportOrderDetail")
    @Operation(summary = "导出订单")
    @OperateLog(type = OperateTypeEnum.EXPORT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public void exportOrderDetail(@ParameterObject @Valid FollowOrderSendQuery query) {
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

        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        //查看券商和服务器
        followOrderDetailVOPageResult.getList().parallelStream().forEach(o->{
            FollowPlatformEntity followPlatform = followPlatformService.getById(followTraderService.getById(o.getTraderId()).getPlatformId());
            o.setBrokeName(followPlatform.getBrokerName());
            o.setPlatform(followPlatform.getServer());
        });
        detailService.export(followOrderDetailVOPageResult.getList());
    }

    @GetMapping("stopOrder")
    @Operation(summary = "停止下单/平仓")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> stopOrder(@Parameter(description = "type") Integer type, @Parameter(description = "traderId") String traderId) {
        return Result.ok(followTraderService.stopOrder(type,traderId));
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
}