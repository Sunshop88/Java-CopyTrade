package net.maku.mascontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowVpsConvert;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.framework.security.user.SecurityUser;
import org.apache.logging.log4j.core.pattern.AbstractStyleNameConverter;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/mascontrol/vps")
@Tag(name = "vps列表")
@AllArgsConstructor
@Slf4j
public class FollowVpsController {
    private final FollowVpsService followVpsService;
    private final FollowTraderService followTraderService;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final RedisCache redisCache;
    private final FollowVpsUserService followVpsUserService;
    private final MasControlService masControlService;
    private final  FollowTestDetailService followTestDetailService;
    private final RedisUtil redisUtil;

    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<PageResult<FollowVpsVO>> page(@ParameterObject @Valid FollowVpsQuery query) {

        PageResult<FollowVpsVO> page = followVpsService.page(query);
        if(ObjectUtil.isEmpty(page)){return Result.ok();}
        List<Integer> ipList = page.getList().stream().map(FollowVpsVO::getId).toList();
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(ObjectUtil.isNotEmpty(ipList), FollowTraderEntity::getServerId, ipList));
        Map<Integer, Map<Integer, List<FollowTraderEntity>>> map = list.stream().collect(Collectors.groupingBy(FollowTraderEntity::getServerId, Collectors.groupingBy(FollowTraderEntity::getType)));
        //查询订阅关系
        Map<Long, Long> subscribeMap = followTraderSubscribeService.list().stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, FollowTraderSubscribeEntity::getMasterId));
        //策略数量
        page.getList().forEach(o -> {
            Map<Integer, List<FollowTraderEntity>> vpsMap = map.get(o.getId());
            int followNum = 0;
            int traderNum = 0;
            o.setTotal(0);
            o.setEuqit(BigDecimal.ZERO);
            o.setProfit(BigDecimal.ZERO);
            o.setLots(BigDecimal.ZERO);
            if (ObjectUtil.isNotEmpty(vpsMap)) {
                List<FollowTraderEntity> followTraderEntities = vpsMap.get(TraderTypeEnum.SLAVE_REAL.getType());
                List<FollowTraderEntity> masterTraderEntities = vpsMap.get(TraderTypeEnum.MASTER_REAL.getType());
                followNum = ObjectUtil.isNotEmpty(followTraderEntities) ? followTraderEntities.size() : 0;
                traderNum = ObjectUtil.isNotEmpty(masterTraderEntities) ? masterTraderEntities.size() : 0;
                Stream<FollowTraderEntity> stream = ObjectUtil.isNotEmpty(followTraderEntities) ? followTraderEntities.stream() : Stream.empty();
                Map<Long, FollowTraderEntity> masterTrader =new HashMap<>();
                if(ObjectUtil.isNotEmpty(masterTraderEntities)){
                   masterTrader = masterTraderEntities.stream().collect(Collectors.toMap(FollowTraderEntity::getId, Function.identity()));
                }
                if (ObjectUtil.isNotEmpty(stream)) {
                    Map<Long, FollowTraderEntity> finalMasterTrader = masterTrader;
                    stream.forEach(x -> {
                        //拿到masterid
                        Long masterId = subscribeMap.get(x.getId());
                        //获取master喊单者,开启了的才统计
                        FollowTraderEntity masterTraderEntity = finalMasterTrader.get(masterId);
                        if (ObjectUtil.isNotEmpty(masterTraderEntity) && masterTraderEntity.getFollowStatus()== CloseOrOpenEnum.OPEN.getValue() && masterTraderEntity.getStatus().equals(TraderStatusEnum.NORMAL.getValue())) {
                            //获取redis内的下单信息
                            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.TRADER_USER + x.getId())) && x.getStatus() == TraderStatusEnum.NORMAL.getValue() && x.getFollowStatus() == CloseOrOpenEnum.OPEN.getValue() ) {
                                FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisCache.get(Constant.TRADER_USER + x.getId());
                                o.setTotal(o.getTotal() + followRedisTraderVO.getTotal());
                                o.setProfit(o.getProfit().add(ObjectUtil.isNotEmpty(followRedisTraderVO.getProfit()) ? followRedisTraderVO.getProfit() : BigDecimal.ZERO));
                                o.setEuqit(o.getEuqit().add(followRedisTraderVO.getEuqit()));
                                BigDecimal lots = new BigDecimal(followRedisTraderVO.getBuyNum() + "").add(new BigDecimal(followRedisTraderVO.getSellNum() + ""));
                                o.setLots(o.getLots().add(lots));
                            }
                        }
                    });
                }
            }
            o.setEuqit(o.getEuqit().setScale(2, BigDecimal.ROUND_HALF_UP));
            o.setProfit(o.getProfit().setScale(2, BigDecimal.ROUND_HALF_UP));
            o.setLots(o.getLots().setScale(2, BigDecimal.ROUND_HALF_UP));
            o.setFollowNum(followNum);
            o.setTraderNum(traderNum);

        });

        return Result.ok(page);
    }

    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<FollowVpsVO> get(@PathVariable("id") Long id) {
        FollowVpsVO followVpsVO = followVpsService.get(id);
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(ObjectUtil.isNotEmpty(id), FollowTraderEntity::getServerId, id));
        Map<Integer, List<FollowTraderEntity>> vpsMap = list.stream().collect(Collectors.groupingBy(FollowTraderEntity::getType));
        List<FollowTraderEntity> followTraderEntities = vpsMap.get(TraderTypeEnum.SLAVE_REAL.getType());
        List<FollowTraderEntity> masterTraderEntities = vpsMap.get(TraderTypeEnum.MASTER_REAL.getType());
        followVpsVO.setFollowNum(ObjectUtil.isNotEmpty(followTraderEntities) ? followTraderEntities.size() : 0);
        followVpsVO.setTraderNum(ObjectUtil.isNotEmpty(masterTraderEntities) ? masterTraderEntities.size() : 0);
        return Result.ok(followVpsVO);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> save(@RequestBody @Valid FollowVpsVO vo) {
        return masControlService.insert(vo) ? Result.ok() : Result.error();
    }

    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> update(@RequestBody @Valid FollowVpsVO vo) {
        return masControlService.update(vo) ? Result.ok() : Result.error();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> delete(@RequestBody List<Integer> idList) {
        //查看该vps是否还有用户
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getServerId, idList));
        if (ObjectUtil.isNotEmpty(list)) {
            throw new ServerException("请先清空vps内账户");
        }
        return masControlService.delete(idList) ? Result.ok() : Result.error();
    }

    @GetMapping("connect")
    @Operation(summary = "vps连接")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<Boolean> connect(@Parameter(description = "ipAddress") String ipAddress) {
        //进行连接
        try {
            InetAddress inet = InetAddress.getByName(ipAddress);
            boolean reachable = inet.isReachable(5000);
            if (!reachable) {
                return Result.error("地址错误,请检查");
            }
            // 检查端口 9001 是否可连接
            try (Socket socket = new Socket(ipAddress, 9001)) {
                // 如果可以建立连接，则返回成功
                return Result.ok(true);
            } catch (IOException e) {
                return Result.error("vps服务未启动");
            }
        } catch (UnknownHostException e) {
            throw new ServerException("地址错误,请检查");
        } catch (IOException e) {
            throw new ServerException("请求异常");
        }
    }

    @GetMapping("info")
    @Operation(summary = "vps统计")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<FollowVpsInfoVO> info() {
        return Result.ok(followVpsService.getFollowVpsInfo(followTraderService,null));
    }

    @GetMapping("listVps")
    @Operation(summary = "vps列表")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<List<FollowVpsVO>> listVps() {
        List<VpsUserVO> list;
        //除了admin都需要判断
        if (!ObjectUtil.equals(Objects.requireNonNull(SecurityUser.getUserId()).toString(), "10000")) {
            //查看当前用户拥有的vps
            if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId()))) {
                list = (List<VpsUserVO>) redisCache.get(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId());
            } else {
                List<FollowVpsUserEntity> vpsUserEntityList = followVpsUserService.list(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId, SecurityUser.getUserId()));
                List<VpsUserVO> vpsUserVOS = convertoVpsUser(vpsUserEntityList);
                redisCache.set(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId(), JSONObject.toJSON(vpsUserVOS));
                list = vpsUserVOS;
            }
        } else {
            list = followVpsService.list().stream().map(o -> {
                VpsUserVO vpsUserVO = new VpsUserVO();
                vpsUserVO.setName(o.getName());
                vpsUserVO.setId(o.getId());
                return vpsUserVO;
            }).toList();
        }
        if (ObjectUtil.isEmpty(list)) {
            return Result.ok(null);
        }
        List<Integer> ids =new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            FollowVpsVO followVpsVO = JSON.parseObject(JSON.toJSONString(list.get(i)), FollowVpsVO.class);
            ids.add(followVpsVO.getId() );
        }
        //.eq(FollowVpsEntity::getIsActive, CloseOrOpenEnum.OPEN.getValue()))
       //list.stream().map(o -> o.getId()).toList()这种写法有问题
        List<FollowVpsEntity> listvps = followVpsService.list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getId, ids).eq(FollowVpsEntity::getIsOpen, CloseOrOpenEnum.OPEN.getValue())
                .eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE.getType()));
        List<FollowVpsVO> followVpsVOS = FollowVpsConvert.INSTANCE.convertList(listvps);
        followVpsVOS.forEach(o -> {
            o.setTraderNum((int) followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.MASTER_REAL.getType()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress())));
            o.setFollowNum((int) followTraderService.count(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getType, TraderTypeEnum.SLAVE_REAL.getType()).eq(FollowTraderEntity::getIpAddr, o.getIpAddress())));
        });
        return Result.ok(followVpsVOS);
    }

    @GetMapping("transferVps")
    @Operation(summary = "转移vps数据")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<Boolean> transferVps(@Parameter(description = "oldId") Integer oldId, @Parameter(description = "newId") Integer newId, HttpServletRequest req) {
        if (ObjectUtil.isEmpty(oldId)) {
            throw new ServerException("请选择原vps");
        }
        if (ObjectUtil.isEmpty(newId)) {
            throw new ServerException("请选择目标vps");
        }
        //清理旧账号缓存
        followVpsService.transferVps(oldId, req);

        FollowVpsEntity followVpsEntity = followVpsService.getById(newId);
        //查询
       List<Long>  excludeIds=followTraderService.getShare(oldId,newId);
        //转移账号
        LambdaUpdateWrapper<FollowTraderEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(FollowTraderEntity::getServerId, newId).
                set(FollowTraderEntity::getServerName, followVpsEntity.getName()).
                set(FollowTraderEntity::getIpAddr, followVpsEntity.getIpAddress()).
                eq(FollowTraderEntity::getServerId, oldId)
                .notIn(ObjectUtil.isNotEmpty(excludeIds),FollowTraderEntity::getId, excludeIds);
        followTraderService.update(updateWrapper);
        //删除旧的账号
        if(ObjectUtil.isNotEmpty(excludeIds)){
            //查找
            LambdaQueryWrapper<FollowTraderEntity> wrapper = new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, oldId);
            List<FollowTraderEntity> list = followTraderService.list(wrapper);
            //判断是否有策略账号，如果有策略账号需要更改订阅关系
            list.forEach(o->{
                if(o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())){
                    FollowTraderEntity one = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, o.getAccount()).eq(FollowTraderEntity::getServerId, newId).one();
                    List<FollowTraderSubscribeEntity> fsList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, o.getId()));
                    //更新redis
                    fsList.forEach(vo->{
                        Map<String, Object> map = new HashMap<>();
                        map.put("followStatus", vo.getFollowStatus());
                        map.put("followOpen", vo.getFollowOpen());
                        map.put("followClose", vo.getFollowClose());
                        map.put("followRep", vo.getFollowRep());
                        //设置跟单关系缓存值 保存状态
                        redisCache.set(Constant.FOLLOW_MASTER_SLAVE + one.getId() + ":" + vo.getSlaveId(), JSONObject.toJSON(map));
                    });
                    //保存状态到redis
                    followTraderSubscribeService.update(
                            new LambdaUpdateWrapper<FollowTraderSubscribeEntity>().set(FollowTraderSubscribeEntity::getMasterId,one.getId()).eq(FollowTraderSubscribeEntity::getMasterId,o.getId())
                    );

                }
            });
            followTraderService.remove(wrapper) ;
        }
   
        //发送请求到新VPS，启动账号
        followVpsService.startNewVps(newId, req);
        return Result.ok();
    }


    @GetMapping("deleteVps")
    @Operation(summary = "清除vps数据")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<Boolean> deleteVps(@Parameter(description = "vpsId") Integer vpsId, HttpServletRequest req) {
        //清理旧账号缓存
        followVpsService.transferVps(vpsId, req);
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
        List<Long> idList = list.stream().map(FollowTraderEntity::getId).toList();
        //删除跟单关系
        if(ObjectUtil.isNotEmpty(idList)) {
            followTraderSubscribeService.remove(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, idList).or().in(FollowTraderSubscribeEntity::getSlaveId, idList));
        }

        //删除账号
        followTraderService.remove(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
        return Result.ok();
    }

    private List<VpsUserVO> convertoVpsUser(List<FollowVpsUserEntity> list) {
        return list.stream().map(o -> {
            VpsUserVO vpsUserVO = new VpsUserVO();
            vpsUserVO.setId(o.getVpsId());
            vpsUserVO.setName(o.getVpsName());
            return vpsUserVO;
        }).toList();
    }

    /**
     * 统计vps
     */
    @GetMapping("getStatByVpsId")
    @Operation(summary = "统计")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<List<List<BigDecimal>>> getStatByVpsId(@Parameter(description = "vpsId") Integer vpsId, @Parameter(description = "traderId") Long traderId) {

        return Result.ok(followVpsService.getStatByVpsId(vpsId, traderId, followTraderService));
    }

    /**
     * 获取所有VPS集合
     */
    @GetMapping("listVpsAll")
    @Operation(summary = "获取所有VPS集合")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<List<FollowVpsEntity>> listVpsAll() {
        return Result.ok(followVpsService.list());
    }

    /**
     * 修改默认节点
     */
    @PutMapping("updateServerNode")
    @Operation(summary = "修改服务器节点")
    @PreAuthorize("hasAuthority('mascontrol:vps')")
    public Result<String> importExcel(@RequestParam(value = "file") MultipartFile file ) throws Exception {
        //检查是否为Excel文件
        if (file.isEmpty() || (!file.getOriginalFilename().toLowerCase().endsWith(".xls") && !file.getOriginalFilename().toLowerCase().endsWith(".xlsx"))) {
            return Result.error("请上传Excel文件");
        }
        followTestDetailService.importByExcel(file);
        return Result.ok("修改完成");
    }

    @PutMapping("updateServer")
    @Operation(summary = "修改服务器节点")
    public Result<String> updateServer(@RequestParam(value = "oldVpsId") Integer oldVpsId ,@RequestParam(value = "vpsId") Integer vpsId ) {
        Map<Object, Object> objectObjectMap = redisUtil.hGetAll(Constant.VPS_NODE_SPEED +oldVpsId);
        objectObjectMap.forEach((k,v)->{
            redisUtil.hSet(Constant.VPS_NODE_SPEED +vpsId,String.valueOf(k),String.valueOf(v));
        });
        return Result.ok("修改完成");
    }

}