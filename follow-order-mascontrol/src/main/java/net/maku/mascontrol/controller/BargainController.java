package net.maku.mascontrol.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.dto.MasToSubOrderCloseDto;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowGroupQuery;
import net.maku.followcom.query.FollowOrderInstructQuery;
import net.maku.followcom.query.FollowOrderInstructSubQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.mascontrol.vo.FollowOrderHistoryQuery;
import net.maku.mascontrol.vo.FollowOrderHistoryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * Author:  zsd
 * Date:  2025/2/25/周二 15:11
 */
@RestController
@RequestMapping("/bargain")
@Tag(name = "交易")
@AllArgsConstructor
public class BargainController {
    private static final Logger log = LoggerFactory.getLogger(BargainController.class);
    private final FollowVpsService followVpsService;
    private BargainService bargainService;
    private final FollowTraderUserService followTraderUserService;
    private final FollowTraderService followTraderService;
    private final FollowOrderInstructSubService followOrderInstructSubService;
    private final FollowOrderInstructService followOrderInstructService;

    @GetMapping("histotyOrderList")
    @Operation(summary = "历史订单")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderHistoryVO>> histotyOrderList(@ParameterObject FollowOrderHistoryQuery followOrderHistoryQuery,HttpServletRequest request) {
        Integer vpsId = followOrderHistoryQuery.getVpsId();
        Long userId = followOrderHistoryQuery.getTraderUserId();
        List<FollowTraderEntity> list = getByUserId(userId);
        if(list==null||list.size()<=0){
            throw new ServerException("vps不存在");
        }
    /*    FollowVpsEntity vps = followVpsService.getById(vpsId);
        if (vps==null) {
            throw new ServerException("vps不存在");
        }*/
        followOrderHistoryQuery.setTraderId(list.get(0).getId());
        Result result = RestUtil.sendRequest(request, list.get(0).getIpAddr(), HttpMethod.GET, FollowConstant.HISTOTY_ORDER_LIST, followOrderHistoryQuery,null);
        return result;
    }


    private List<FollowTraderEntity> getByUserId(Long traderUserId) {
        FollowTraderUserEntity traderUser = followTraderUserService.getById(traderUserId);
        if(traderUser==null){
            throw  new ServerException("账号用户不存在");
        }
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, traderUser.getAccount()).eq(FollowTraderEntity::getPlatformId, traderUser.getPlatformId()));
        return list;
    }
    @GetMapping("reconnection")
    @Operation(summary = "重连账号")
    public Result<Boolean> reconnection(@Parameter(description = "traderUserId") String traderUserId,HttpServletRequest request) {
        List<FollowTraderEntity> users = getByUserId(Long.parseLong(traderUserId));
        if(ObjectUtil.isEmpty(users)){
            throw new ServerException("账号未挂靠vps");

        }
        users.forEach(user -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("traderId", user.getId());
            Result result = RestUtil.sendRequest(request, user.getIpAddr(), HttpMethod.GET, FollowConstant.RECONNECTION, jsonObject,null);
        });
        return Result.ok();
    }

    @PostMapping("orderClose")
    @Operation(summary = "平仓")
    public Result<Boolean> orderClose(@RequestBody @Valid BargainCloseVO vo,HttpServletRequest request) {
        List<FollowTraderEntity> users = getByUserId(vo.getTraderUserId());

        if(ObjectUtil.isEmpty(users)){
            throw new ServerException("账号未挂靠vps");
        }
        users.forEach(user -> {
            FollowOrderSendCloseVO closeVO=new FollowOrderSendCloseVO();
            closeVO.setTraderId(user.getId());
            closeVO.setAccount(user.getAccount());
            BeanUtil.copyProperties(vo, closeVO);
            Result result = RestUtil.sendRequest(request, user.getIpAddr(), HttpMethod.POST, FollowConstant.RECONNECTION, closeVO,null);
        });
        return Result.ok();
    }

    @PostMapping("masOrderSend")
    @Operation(summary = "交易下单")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public void  masOrderSend(@RequestBody @Valid MasOrderSendDto vo, HttpServletRequest request) {
        bargainService.masOrderSend(vo,request);
    }

    @PostMapping("masOrderClose")
    @Operation(summary = "交易平仓")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public void  masOrderClose(@RequestBody @Valid MasToSubOrderCloseDto vo, HttpServletRequest request) {
        bargainService.masOrderClose(vo,request);
    }

    @GetMapping("historySubcommands")
    @Operation(summary = "历史子指令分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderInstructSubVO>> page(@ParameterObject @Valid FollowOrderInstructSubQuery query){
        PageResult<FollowOrderInstructSubVO> page = followOrderInstructSubService.page(query);

        return Result.ok(page);
    }

    @GetMapping("historyCommands")
    @Operation(summary = "历史总指令分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderInstructVO>> page(@ParameterObject @Valid FollowOrderInstructQuery query){
        PageResult<FollowOrderInstructVO> page = followOrderInstructService.page(query);

        return Result.ok(page);
    }
}
