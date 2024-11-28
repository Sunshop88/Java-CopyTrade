package net.maku.subcontrol.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.FollowModeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
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
import net.maku.subcontrol.query.FollowOrderHistoryQuery;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.service.FollowSlaveService;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import net.maku.subcontrol.vo.FollowOrderHistoryVO;
import net.maku.subcontrol.vo.RepairSendVO;
import online.mtapi.mt4.PlacedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 跟单
 */
@RestController
@RequestMapping("/subcontrol/follow")
@Tag(name = "mt4账号")
@AllArgsConstructor
public class FollowSlaveController {
    private static final Logger log = LoggerFactory.getLogger(FollowSlaveController.class);
    private final FollowTraderService followTraderService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowOrderHistoryService followOrderHistoryService;
    private final RedisCache redisCache;
    private final FollowTestSpeedService followTestSpeedService;
    private final FollowTestDetailService followTestDetailService;
    private final FollowSlaveService followSlaveService;
    private final FollowVarietyService followVarietyService;

    @PostMapping("addSlave")
    @Operation(summary = "新增跟单账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> addSlave(@RequestBody @Valid FollowAddSalveVo vo) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getTraderId());
            if (ObjectUtil.isEmpty(followTraderEntity)) {
                throw new ServerException("请输入正确喊单账号");
            }
            //如果为固定手数和手数比例，必填参数
            if (vo.getFollowStatus().equals(FollowModeEnum.FIX.getCode()) || vo.getFollowStatus().equals(FollowModeEnum.RATIO.getCode())) {
                if (ObjectUtil.isEmpty(vo.getFollowParam())||vo.getFollowParam().compareTo(new BigDecimal("0.01"))<0) {
                    throw new ServerException("请输入正确跟单参数");
                }
            }
            //查看是否存在循环跟单情况
            FollowTraderSubscribeEntity traderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterAccount, vo.getAccount()).eq(FollowTraderSubscribeEntity::getSlaveAccount, followTraderEntity.getAccount()));
            if (ObjectUtil.isNotEmpty(traderSubscribeEntity)) {
                throw new ServerException("存在循环跟单,请检查");
            }
            FollowTraderVO followTraderVo = new FollowTraderVO();
            followTraderVo.setAccount(vo.getAccount());
            followTraderVo.setPassword(vo.getPassword());
            followTraderVo.setPlatform(vo.getPlatform());
            followTraderVo.setType(TraderTypeEnum.SLAVE_REAL.getType());
            if (ObjectUtil.isEmpty(vo.getTemplateId())) {
                vo.setTemplateId(getLatestTemplateId());
            }
            followTraderVo.setTemplateId(vo.getTemplateId());
            FollowTraderVO followTraderVO = followTraderService.save(followTraderVo);

            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVo);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(convert);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                followTraderService.removeById(followTraderVO.getId());
                return Result.error();
            }
            ThreadPoolUtils.execute(() -> {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                followTraderService.saveQuo(copierApiTrader.quoteClient, convert);
                //设置下单方式
                copierApiTrader.orderClient.PlacedType = PlacedType.forValue(vo.getPlacedType());
                //建立跟单关系
                vo.setSlaveId(followTraderVO.getId());
                vo.setSlaveAccount(vo.getAccount());
                vo.setMasterAccount(followTraderEntity.getAccount());
                followTraderSubscribeService.addSubscription(vo);
                copierApiTrader.startTrade();
                //保存状态到redis
                Map<String, Object> map = new HashMap<>();
                map.put("followStatus", vo.getFollowStatus());
                map.put("followOpen", vo.getFollowOpen());
                map.put("followClose", vo.getFollowClose());
                map.put("followRep", vo.getFollowRep());
                //设置跟单关系缓存值 保存状态
                redisCache.set(Constant.FOLLOW_MASTER_SLAVE + followTraderEntity.getId() + ":" + vo.getSlaveId(), JSONObject.toJSON(map));
            });
        } catch (Exception e) {
            log.error("跟单账号保存失败:", e);
            if(e instanceof ServerException) {
                throw e;
            }else{
                throw new ServerException("保存失败" + e);
            }

        }

        return Result.ok();
    }

    private Integer getLatestTemplateId() {
        //获取最新的模板id
        return followVarietyService.getListByTemplate().stream().map(FollowVarietyVO::getId).max(Integer::compareTo).orElse(null);
    }

    @PostMapping("updateSlave")
    @Operation(summary = "修改跟单账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> updateSlave(@RequestBody @Valid FollowUpdateSalveVo vo) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getId());
            if (ObjectUtil.isEmpty(vo.getTemplateId())) {
                vo.setTemplateId(getLatestTemplateId());
            }
            BeanUtil.copyProperties(vo, followTraderEntity);
            followTraderService.updateById(followTraderEntity);
            //查看绑定跟单账号
            FollowTraderSubscribeEntity followTraderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>()
                    .eq(FollowTraderSubscribeEntity::getSlaveId, vo.getId()));
            BeanUtil.copyProperties(vo, followTraderSubscribeEntity, "id");
            //更新订阅状态
            followTraderSubscribeService.updateById(followTraderSubscribeEntity);
            Map<String,Object> map=new HashMap<>();
            map.put("followStatus",vo.getFollowStatus());
            map.put("followOpen",vo.getFollowOpen());
            map.put("followClose",vo.getFollowClose());
            map.put("followRep",vo.getFollowRep());
            redisCache.set(Constant.FOLLOW_MASTER_SLAVE + followTraderSubscribeEntity.getMasterId() + ":" + followTraderEntity.getId(),map);
            //删除缓存
            copierApiTradersAdmin.removeTrader(followTraderEntity.getId().toString());
            //启动账户
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                return Result.error();
            }
            //重连
            reconnect(vo.getId().toString());
            ThreadPoolUtils.execute(() -> {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                //设置下单方式
                copierApiTrader.orderClient.PlacedType = PlacedType.forValue(vo.getPlacedType());
                copierApiTrader.startTrade();
            });
        } catch (Exception e) {
            throw new ServerException("修改失败" + e);
        }

        return Result.ok();
    }


    @GetMapping("slaveList")
    @Operation(summary = "跟单账号列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> slaveList(@ParameterObject @Valid FollowTraderQuery query) {
        if (ObjectUtil.isEmpty(query.getTraderId())) {
            throw new ServerException("请求异常");
        }
        List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, query.getTraderId()));
        List<Long> collect = list.stream().map(FollowTraderSubscribeEntity::getSlaveId).toList();
        Map<Long, List<FollowTraderSubscribeEntity>> map = list.stream().collect(Collectors.groupingBy(FollowTraderSubscribeEntity::getSlaveId));
        query.setTraderList(collect);
        PageResult<FollowTraderVO> page = followTraderService.page(query);
        page.getList().stream().forEach(o->{
            List<FollowTraderSubscribeEntity> subscribes = map.get(o.getId());
            if(ObjectUtil.isNotEmpty(subscribes)){
                o.setPlacedType(subscribes.get(0).getPlacedType());
                o.setFollowMode(subscribes.get(0).getFollowMode());
            } ;});
        return Result.ok(page);
    }

    @GetMapping("transferVps")
    @Operation(summary = "旧账号清理缓存")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> transferVps() {
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST));
        list.forEach(o -> leaderApiTradersAdmin.removeTrader(o.getId().toString()));
        return Result.ok(true);
    }

    @GetMapping("startNewVps")
    @Operation(summary = "新账号启动")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> startNewVps() {
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getIpAddr, FollowConstant.LOCAL_HOST));
        try {
            leaderApiTradersAdmin.startUp(list.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).toList());
            copierApiTradersAdmin.startUp(list.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).toList());
        } catch (Exception e) {
            throw new ServerException("新Vps账号启动异常" + e);
        }
        return Result.ok(true);
    }


    @GetMapping("histotyOrderList")
    @Operation(summary = "历史订单")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderHistoryVO>> histotyOrderList(@ParameterObject FollowOrderHistoryQuery followOrderHistoryQuery) {
        return Result.ok(followOrderHistoryService.page(followOrderHistoryQuery));
    }

    @PostMapping("start")
    @Operation(summary = "单个vps测速")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowTestSpeedVO> start(@RequestBody MeasureRequestVO request) {
        List<String> servers = request.getServers();
        FollowVpsEntity vpsEntity = request.getVpsEntity();
        Integer testId = request.getTestId();

        // 批量调用服务进行测速
        boolean isSuccess = followTestSpeedService.measure(servers, vpsEntity, testId);
        if (isSuccess) {
            return Result.ok();
        } else {
            // 删除当前vps相关的数据
            followTestDetailService.deleteByTestId(testId);
            return Result.error("测速失败，已删除相关数据");
        }
    }

    @PostMapping("repairSend")
    @Operation(summary = "漏单处理")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> repairSend(@RequestBody RepairSendVO repairSendVO) {
        return Result.ok(followSlaveService.repairSend(repairSendVO));
    }

    private void reconnect(String traderId) {
        try {
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
        } catch (RuntimeException e) {
            throw new ServerException("请检查账号密码，稍后再试");
        }
    }
}