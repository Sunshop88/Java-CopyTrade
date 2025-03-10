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
import net.maku.followcom.convert.FollowOrderDetailConvert;
import net.maku.followcom.dto.MasOrderSendDto;
import net.maku.followcom.dto.MasToSubOrderCloseDto;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.*;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.mascontrol.vo.FollowOrderHistoryQuery;
import net.maku.mascontrol.vo.FollowOrderHistoryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private final FollowSysmbolSpecificationService followSysmbolSpecificationService;
    private final  FollowOrderDetailService followOrderDetailService;
    private final RedisCache redisCache;
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

        Result result = RestUtil.sendRequest(request,   list.get(0).getIpAddr(), HttpMethod.GET, FollowConstant.HISTOTY_ORDER_LIST, followOrderHistoryQuery,null);
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
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        users.forEach(user -> {
            ThreadPoolUtils.getExecutor().execute(()->{
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("traderId", user.getId());
                Result result = RestUtil.sendRequest(request, user.getIpAddr(), HttpMethod.GET, FollowConstant.RECONNECTION, jsonObject,headerApplicationJsonAndToken);
            });

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
            Result result = RestUtil.sendRequest(request, user.getIpAddr(), HttpMethod.POST, FollowConstant.FOLLOW_ORDERCLOSE, closeVO,null);
        });
        return Result.ok();
    }

    @PostMapping("repairOrderClose")
    @Operation(summary = "一键漏平")
    public Result<Boolean> repairOrderClose(@RequestBody TraderUserClose  vo,HttpServletRequest request) {

        List<FollowTraderEntity> users = getByUserId(vo.getTraderUserId());
        HttpHeaders headerApplicationJsonAndToken = RestUtil.getHeaderApplicationJsonAndToken(request);
        users.forEach(user -> {

            ThreadPoolUtils.getExecutor().execute(()->{
                List<RepairCloseVO> repairCloseVO=new ArrayList<>();
                RepairCloseVO closeVO=new RepairCloseVO();
                closeVO.setSlaveId(user.getId());
                closeVO.setVpsId(user.getServerId());
                repairCloseVO.add(closeVO);
                Result result = RestUtil.sendRequest(request, user.getIpAddr(), HttpMethod.POST, FollowConstant.FOLLOW_ALL_ORDERCLOSE, repairCloseVO,headerApplicationJsonAndToken);
            });

        });

        return Result.ok();
    }

    @PostMapping("masOrderSend")
    @Operation(summary = "交易下单")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?>  masOrderSend(@RequestBody @Valid MasOrderSendDto vo, HttpServletRequest request) {
        bargainService.masOrderSend(vo,request);
        return Result.ok();
    }

    @PostMapping("masOrderClose")
    @Operation(summary = "交易平仓")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?>   masOrderClose(@RequestBody @Valid MasToSubOrderCloseDto vo, HttpServletRequest request) {
        bargainService.masOrderClose(vo,request);
        return Result.ok();
    }

    @GetMapping("historySubcommands")
    @Operation(summary = "历史子指令分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<List<FollowOrderInstructSubVO>> page(@RequestParam String sendNo){
        FollowOrderDetailQuery ordreQuery=new FollowOrderDetailQuery();
        ordreQuery.setSendNo(sendNo);
        ordreQuery.setPage(1);
        ordreQuery.setLimit(1000);
        PageResult<FollowOrderDetailVO> page = followOrderDetailService.page(ordreQuery);
        List<FollowOrderInstructSubVO> followOrderInstructSubVOS = FollowOrderDetailConvert.INSTANCE.convertPage(page.getList());

        return Result.ok(followOrderInstructSubVOS);
    }

    @GetMapping("historyCommands")
    @Operation(summary = "历史总指令分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderInstructVO>> page(@ParameterObject @Valid FollowOrderInstructQuery query){
        PageResult<FollowOrderInstructVO> page = followOrderInstructService.page(query);

        return Result.ok(page);
    }

    @GetMapping("/specificationList")
    @Operation(summary = "品种规格列表")
    public Result<PageResult<FollowSysmbolSpecificationVO>> page(@ParameterObject @Valid FollowSysmbolSpecificationQuery query) {
        List<FollowTraderEntity> list = getByUserId(query.getTraderUserId());
        PageResult<FollowSysmbolSpecificationVO> page =null;
        if(ObjectUtil.isNotEmpty(list)){
            query.setTraderId(list.get(0).getId());
            page = followSysmbolSpecificationService.page(query);
        }

        return Result.ok(page);
    }
}
