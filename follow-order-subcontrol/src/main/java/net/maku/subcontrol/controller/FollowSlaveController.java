package net.maku.subcontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.followcom.vo.FollowAddSalveVo;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.PlacedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 跟单
 */
@RestController
@RequestMapping("/subcontrol/follow")
@Tag(name="mt4账号")
@AllArgsConstructor
public class FollowSlaveController {
    private static final Logger log = LoggerFactory.getLogger(FollowSlaveController.class);
    private final FollowTraderService followTraderService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowVpsService followVpsService;
    @PostMapping("addSlave")
    @Operation(summary = "新增跟单账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> addSlave(@RequestBody FollowAddSalveVo vo){
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getTraderId());
            if (ObjectUtil.isEmpty(followTraderEntity)){
                throw new ServerException("请输入正确喊单账号");
            }
            FollowTraderVO followTraderVo = new FollowTraderVO();
            followTraderVo.setAccount(vo.getAccount());
            followTraderVo.setPassword(vo.getPassword());
            followTraderVo.setPlatform(vo.getPlatform());
            followTraderVo.setType(TraderTypeEnum.SLAVE_REAL.getType());
            FollowTraderVO followTraderVO = followTraderService.save(followTraderVo);
            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVo);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(convert);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)){
                followTraderService.removeById(followTraderVO.getId());
                return Result.error();
            }
            ThreadPoolUtils.execute(()->{
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                followTraderService.saveQuo(copierApiTrader.quoteClient,convert);
                //设置下单方式
                copierApiTrader.orderClient.PlacedType=PlacedType.forValue(vo.getPlacedType());;
                //建立跟单关系
                vo.setSlaveAccount(followTraderVO.getId());
                followTraderSubscribeService.addSubscription(vo);
                copierApiTrader.startTrade();
            });
        }catch (Exception e){
            log.error("保存失败"+e);
        }

        return Result.ok();
    }


    @GetMapping("slaveList")
    @Operation(summary = "跟单账号列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> slaveList(@ParameterObject @Valid FollowTraderQuery query){
        if (ObjectUtil.isEmpty(query.getTraderId())){
            throw new ServerException("请求异常");
        }
        List<Long> collect = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, query.getTraderId())).stream().map(FollowTraderSubscribeEntity::getSlaveId).toList();
        query.setTraderList(collect);
        PageResult<FollowTraderVO> page = followTraderService.page(query);
        return Result.ok(page);
    }

    @GetMapping("transferVps")
    @Operation(summary = "旧账号清理缓存")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> transferVps(@PathVariable("oldId") Long oldId){
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, oldId));
        list.forEach(o->leaderApiTradersAdmin.removeTrader(o.getId().toString()));
        return Result.ok(true);
    }

    @GetMapping("startNewVps")
    @Operation(summary = "新账号启动")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> startNewVps(@PathVariable("newId") Long newId){
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, newId));
        try {
            leaderApiTradersAdmin.startUp(list.stream().filter(o->o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).toList());
            copierApiTradersAdmin.startUp(list.stream().filter(o->o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).toList());
        }catch (Exception e){
            throw new ServerException("新Vps账号启动异常"+e);
        }
        return Result.ok(true);
    }
}