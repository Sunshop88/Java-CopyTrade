package net.maku.subcontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.followcom.vo.FollowAddSalveVo;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    private final FollowTraderSubscribeService followTraderSubscribeService;
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


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowTraderVO> get(@PathVariable("id") Long id){
        FollowTraderVO data = followTraderService.get(id);

        return Result.ok(data);
    }
}