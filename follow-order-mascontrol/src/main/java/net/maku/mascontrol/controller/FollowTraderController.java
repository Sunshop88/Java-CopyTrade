package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.query.FollowOrderSendQuery;
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
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.service.FollowPlatformService;
import net.maku.mascontrol.trader.LeaderApiTrader;
import net.maku.mascontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.QuoteClient;
import org.bouncycastle.cms.PasswordRecipientId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/trader")
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
                vo.setStatus(CloseOrOpenEnum.OPEN.getValue());
                followTraderService.removeById(vo);
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
        QuoteClient quoteClient=leaderApiTrader.quoteClient;
        if (ObjectUtil.isEmpty(leaderApiTrader.quoteClient)||!leaderApiTrader.quoteClient.Connected()){
            quoteClient = followPlatformService.tologin(followTraderVO.getAccount(), followTraderVO.getPassword(), followTraderVO.getPlatform());
            if (ObjectUtil.isEmpty(quoteClient)){
                throw new ServerException("账号无法登录");
            }
        }
        boolean result = followTraderService.orderSend(vo,quoteClient,followTraderVO);
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
        if (ObjectUtil.isNotEmpty(query.getPlatForm())){
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getPlatform, query.getPlatForm()));
            List<String> collect = list.stream().map(entity -> String.valueOf(entity.getId())).collect(Collectors.toList());
            String traderId = collect.stream().collect(Collectors.joining(","));
            if (ObjectUtil.isEmpty(query.getTraderId())){
                query.setTraderId(traderId);
            }else {
                query.setTraderId(traderId+","+query.getTraderId());
            }
        }
        PageResult<FollowOrderSlipPointVO> followOrderSlipPointVOPageResult = followTraderService.pageSlipPoint(query);
        followOrderSlipPointVOPageResult.getList().stream().forEach(o->{
            Integer total = followOrderSlipPointVOPageResult.getList().stream().filter(order->o.getTraderId()==order.getTraderId()).mapToInt(FollowOrderSlipPointVO::getSymbolNum).sum();;
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
        return  Result.ok(followTraderService.orderSlipDetail(query));
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
        QuoteClient quoteClient=leaderApiTrader.quoteClient;
        if (ObjectUtil.isEmpty(leaderApiTrader.quoteClient)||!leaderApiTrader.quoteClient.Connected()){
            quoteClient = followPlatformService.tologin(followTraderVO.getAccount(), followTraderVO.getPassword(), followTraderVO.getPlatform());
            if (ObjectUtil.isEmpty(quoteClient)){
                throw new ServerException("账号无法登录");
            }
        }
        boolean result = followTraderService.orderClose(vo,quoteClient);
        return Result.ok(result);
    }

}