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
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
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
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.vo.FollowAddSalveVo;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 跟单账号
 */
@RestController
@RequestMapping("/subcontrol/trader")
@Tag(name="mt4账号")
@AllArgsConstructor
public class FollowSlaveController {
    private static final Logger log = LoggerFactory.getLogger(FollowSlaveController.class);
    private final FollowTraderService followTraderService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowBrokeServerService followBrokeServerService;

    @PostMapping("addSlave")
    @Operation(summary = "新增跟单账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> addSlave(@RequestBody FollowAddSalveVo vo){
        try {
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
                copierApiTrader.startTrade();
                followTraderService.saveQuo(copierApiTrader.quoteClient,convert);
                //建立跟单关系

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