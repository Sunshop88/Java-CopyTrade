package net.maku.subcontrol.controller;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.dto.MasToSubOrderSendDto;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.*;
import net.maku.followcom.query.*;
import net.maku.followcom.service.*;
import net.maku.followcom.util.AesUtils;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.framework.operatelog.annotations.OperateLog;
import net.maku.framework.operatelog.enums.OperateTypeEnum;
import net.maku.subcontrol.service.FollowApiService;
import net.maku.subcontrol.task.ObtainOrderHistoryTask;
import net.maku.subcontrol.task.SpeedTestTask;
import net.maku.subcontrol.trader.*;
import net.maku.followcom.vo.FollowSendAccountListVO;
import online.mtapi.mt4.Exception.ConnectException;
import online.mtapi.mt4.Exception.InvalidSymbolException;
import online.mtapi.mt4.Exception.TimeoutException;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.QuoteEventArgs;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.baomidou.mybatisplus.extension.toolkit.Db.list;

/**
 * 策略账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@RestController
@RequestMapping("/subcontrol/trader")
@Tag(name = "mt4账号")
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
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowVpsService followVpsService;
    private final CacheManager cacheManager;
    private final SpeedTestTask speedTestTask;
    private final RedisUtil redisUtil;
    private final ObtainOrderHistoryTask obtainOrderHistoryTask;
    private final SourceService sourceService;
    private final FollowService followService;
    private final FollowApiService followApiService;
    private final FollowTraderUserService followTraderUserService;
    @GetMapping("page")
    @Operation(summary = "分页")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowTraderVO>> page(@ParameterObject @Valid FollowTraderQuery query) {
        query.setServerIp(FollowConstant.LOCAL_HOST);
        PageResult<FollowTraderVO> page = followTraderService.page(query);
        return Result.ok(page);
    }


    @GetMapping("{id}")
    @Operation(summary = "信息")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowTraderVO> get(@PathVariable("id") Long id) {
        FollowTraderVO data = followTraderService.get(id);

        return Result.ok(data);
    }

    @PostMapping
    @Operation(summary = "保存")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> save(@RequestBody @Valid FollowTraderVO vo) {
        //默认模板最新的模板id
        long newId=0;
        long newUserId=0;
        long sourceId=0;
        if (ObjectUtil.isEmpty(vo.getTemplateId())) {
            vo.setTemplateId(followVarietyService.getBeginTemplateId());
        }
        String password = vo.getPassword();
        //本机处理
        try {
//            vo.setPassword(AesUtils.aesEncryptStr(vo.getPassword()));
            FollowTraderVO followTraderVO = followTraderService.save(vo);
            newId=followTraderVO.getId();
            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVO);
            convert.setId(followTraderVO.getId());
            if(ObjectUtil.isEmpty(vo.getIsSyncLogin())){
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(followTraderVO.getId()));
                if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                    followTraderService.removeById(followTraderVO.getId());
                    return Result.error("账号无法连接");
                }
                exec(followTraderVO, convert);
            }else{
                ThreadPoolUtils.execute(()->{
                    ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(followTraderVO.getId()));
                    if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                    //    followTraderService.removeById(followTraderVO.getId());
                    }else{
                        exec(followTraderVO, convert);
                    }

                });
            }

            //添加trader_user
            if (ObjectUtil.isEmpty(vo.getIsAdd()) || vo.getIsAdd()) {
                List<FollowTraderUserEntity> entities = followTraderUserService.list(new LambdaQueryWrapper<FollowTraderUserEntity>().eq(FollowTraderUserEntity::getAccount, vo.getAccount()).eq(FollowTraderUserEntity::getPlatform, vo.getPlatform()));
                if (ObjectUtil.isEmpty(entities)) {
                    FollowTraderUserVO followTraderUserVO = new FollowTraderUserVO();
                    followTraderUserVO.setAccount(vo.getAccount());
                    followTraderUserVO.setPassword(password);
                    followTraderUserVO.setPlatform(vo.getPlatform());
                    followTraderUserVO.setAccountType(AccountTypeEnum.MT4.getType());
                    Long id = followPlatformService.list(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, vo.getPlatform())).getFirst().getId();
                    followTraderUserVO.setPlatformId(Math.toIntExact(id));
                    followTraderUserVO.setStatus(CloseOrOpenEnum.OPEN.getValue());
                    followTraderUserVO.setServerNode(followTraderVO.getServerNode());
                    followTraderUserService.save(followTraderUserVO);
                    //查询最新的id
                    newUserId=followTraderUserService.getOne(new QueryWrapper<FollowTraderUserEntity>().orderByDesc("id").last("limit 1")).getId();
                }else {
                    entities.forEach(e -> {
                        e.setAccount(vo.getAccount());
                        e.setPassword(password);
                        e.setPlatform(vo.getPlatform());
                        e.setAccountType(AccountTypeEnum.MT4.getType());
                        e.setStatus(CloseOrOpenEnum.OPEN.getValue());
                        followTraderUserService.updateById(e);
                    });
                }
            }
            //保存从表数据
            if(TraderTypeEnum.MASTER_REAL.getType().equals(vo.getType())){
                SourceInsertVO sourceInsertVO=new SourceInsertVO();
                sourceInsertVO.setServerId(Integer.valueOf(convert.getServerId()));
                sourceInsertVO.setPlatformId(convert.getPlatformId());
                sourceInsertVO.setAccount(Long.valueOf(vo.getAccount()));
                sourceInsertVO.setPassword(vo.getPassword());
                sourceInsertVO.setRemark(vo.getRemark());
                sourceInsertVO.setStatus(true);
                sourceInsertVO.setId(followTraderVO.getId());
                Integer id = sourceService.add(sourceInsertVO);
                sourceId=id;
            }

        } catch (Exception e) {
            followTraderService.removeById(newId);
            if (newUserId!=0){
                followTraderUserService.removeById(newUserId);
            }
            if (sourceId!=0){
                sourceService.removeById(sourceId);
            }
            log.error("保存失败" + e);
            if (e instanceof ServerException) {
                throw e;
            } else {
                throw new ServerException(e.getMessage());
            }
        }

        return Result.ok();
    }

    private void exec(FollowTraderVO followTraderVO, FollowTraderEntity convert) {
        LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
        leaderApiTrader1.startTrade();
        //添加订单数据
        List<FollowTraderEntity> newList = new ArrayList<>();
        convert.setIsFirstSync(1);
        obtainOrderHistoryTask.update(convert,newList);
        ThreadPoolUtils.execute(() -> {
            LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
            leaderApiTradersAdmin.pushRedisData(followTraderVO,leaderApiTrader.quoteClient);
            leaderApiTrader.startTrade();
            followTraderService.saveQuo(leaderApiTrader.quoteClient, convert);
        });
    }


    @PutMapping
    @Operation(summary = "修改")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> update(@RequestBody @Valid FollowTraderVO vo) {
        FollowTraderVO old = followTraderService.get(vo.getId());
        if (ObjectUtil.isEmpty(vo.getTemplateId())) {
            vo.setTemplateId(followVarietyService.getBeginTemplateId());
        }
        if(ObjectUtil.isNotEmpty(vo.getPassword())){
            vo.setPassword(vo.getPassword());
        }
        followTraderService.update(vo);
        //修改trader_user
        LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FollowTraderUserEntity::getAccount, old.getAccount());
        updateWrapper.eq(FollowTraderUserEntity::getPlatformId, old.getPlatformId());
        updateWrapper.set(ObjectUtil.isNotEmpty(vo.getPassword()),FollowTraderUserEntity::getPassword, vo.getPassword());
        followTraderUserService.update(updateWrapper);
        //重连
        if(ObjectUtil.isNotEmpty(vo.getPassword()) && !old.getPassword().equals(vo.getPassword())){
            Boolean reconnect = reconnect(vo.getId().toString());
            if (!reconnect){
                throw new ServerException("请检查账号密码，稍后再试");
            }
        }
      //  Boolean reconnect = reconnect(vo.getId().toString());

        //修改从库
        SourceUpdateVO sourceUpdateVO=new SourceUpdateVO();
     //   sourceUpdateVO.setServerId(Integer.valueOf(vo.getServerId()));
        sourceUpdateVO.setPassword(vo.getPassword());
        sourceUpdateVO.setRemark(vo.getRemark());
        sourceUpdateVO.setStatus(vo.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue()));
        sourceUpdateVO.setId(vo.getId());
        sourceService.edit(sourceUpdateVO);
        return Result.ok();
    }

    @DeleteMapping
    @Operation(summary = "删除")
    @OperateLog(type = OperateTypeEnum.DELETE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<String> delete(@RequestBody List<Long> idList) {
        List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId, idList));

        List<FollowTraderEntity> masterList = list.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).toList();
        List<FollowTraderEntity> slaveList = list.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).toList();
        if (ObjectUtil.isNotEmpty(masterList)) {
            //查看喊单账号是否存在用户
            List<FollowTraderSubscribeEntity> followTraderSubscribeEntityList = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId, masterList.stream().map(FollowTraderEntity::getId).toList()));
            if (ObjectUtil.isNotEmpty(followTraderSubscribeEntityList)) {
                throw new ServerException("请先删除跟单用户");
            }
        }
        followTraderService.delete(idList);
        list.forEach(e -> {
            List<FollowTraderEntity> list1 = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, e.getAccount()).eq(FollowTraderEntity::getPlatformId, e.getPlatformId()));
            if (list1.size() <= 0) {
                LambdaUpdateWrapper<FollowTraderUserEntity> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(FollowTraderUserEntity::getAccount, e.getAccount());
                updateWrapper.eq(FollowTraderUserEntity::getPlatformId, e.getPlatformId());
                updateWrapper.set(FollowTraderUserEntity::getStatus,CloseOrOpenEnum.CLOSE.getValue());
                followTraderUserService.update(updateWrapper);
            }
        });
        //清空缓存
        list.stream().forEach(o ->{
            leaderApiTradersAdmin.removeTrader(o.getId().toString());
            copierApiTradersAdmin.removeTrader(o.getId().toString());
            redisCache.deleteByPattern(o.getId().toString());
            //账号缓存移除
            Cache cache = cacheManager.getCache("followFollowCache");
            if (cache != null) {
                cache.evict(o.getId()); // 移除指定缓存条目
            }
            //删除从表
            followService.del(o.getId());
            sourceService.del(o.getId());
        });

        slaveList.forEach(o->{

            List<FollowTraderSubscribeEntity> followTraderSubscribeEntities = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, o.getId()));

            //跟单关系缓存删除
            followTraderSubscribeEntities.forEach(o1->{
                redisUtil.hDel(Constant.REPAIR_SEND+o1.getMasterAccount()+":"+o1.getMasterId(),o1.getSlaveAccount().toString());
                redisUtil.hDel(Constant.REPAIR_CLOSE+o1.getMasterAccount()+":"+o1.getMasterId(),o1.getSlaveAccount().toString());
                FollowTraderEntity master = followTraderService.getFollowById(o1.getMasterId());
                //删除跟单漏单缓存
                redisCache.delete(Constant.FOLLOW_REPAIR_SEND + FollowConstant.LOCAL_HOST + "#"+master.getPlatform()+"#"+o.getPlatform()+"#"+o1.getSlaveAccount() + "#" + o1.getMasterAccount());
                redisCache.delete(Constant.FOLLOW_REPAIR_CLOSE + FollowConstant.LOCAL_HOST+ "#"+master.getPlatform()+"#"+o.getPlatform()+"#" +o1.getSlaveAccount() + "#" + o1.getMasterAccount());
                String cacheKey = generateCacheKey(o1.getSlaveId(), o1.getMasterId());
                Cache cache = cacheManager.getCache("followSubscriptionCache");
                if (cache != null) {
                    cache.evict(cacheKey); // 移除指定缓存条目
                }

                //删除跟单对应主账号缓存
                Cache cache1 = cacheManager.getCache("followSubOrderCache");
                if (cache1 != null) {
                    cache1.evict(o1.getMasterId()); // 移除指定缓存条目
                }

                Cache cache3= cacheManager.getCache("followSubTraderCache");
                if (cache3 != null) {
                    cache3.evict(o1.getSlaveId()); // 移除指定缓存条目
                }
                followTraderService.removeRelation(o,o1.getMasterAccount(),master.getPlatformId());
            });

            //删除订阅关系
            followTraderSubscribeService.remove(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getSlaveId, o.getId()));
        });

        //删除订阅关系
        followTraderSubscribeService.remove(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().in(FollowTraderSubscribeEntity::getMasterId,idList));

        masterList.forEach(o->{
            //喊单关系缓存移除
            Cache cache = cacheManager.getCache("followSubOrderCache");
            if (cache != null) {
                cache.evict(o.getId()); // 移除指定缓存条目
            }
        });

        return Result.ok();
    }


    private String generateCacheKey(Long slaveId, Long masterId) {
        if (slaveId != null && masterId != null) {
            return slaveId + "_" + masterId;
        } else {
            return "defaultKey";
        }
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
    public Result<List<FollowSysmbolSpecificationEntity>> listSymbol(@PathVariable("id") Long traderId) {
        List<FollowSysmbolSpecificationEntity> followSysmbolSpecificationEntityList;
        if (ObjectUtil.isNotEmpty(redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId))) {
            followSysmbolSpecificationEntityList = (List<FollowSysmbolSpecificationEntity>) redisCache.get(Constant.SYMBOL_SPECIFICATION + traderId);
        } else {
            //查询改账号的品种规格
            followSysmbolSpecificationEntityList = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
            redisCache.set(Constant.SYMBOL_SPECIFICATION + traderId, followSysmbolSpecificationEntityList);
        }
        return Result.ok(followSysmbolSpecificationEntityList);
    }

    @GetMapping("listOrderSymbol")
    @Operation(summary = "订单品种列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<List<String>> listSymbol() {
        List<FollowOrderSendEntity> collect = followOrderSendService.list(new LambdaQueryWrapper<FollowOrderSendEntity>().select(FollowOrderSendEntity::getSymbol).eq(FollowOrderSendEntity::getIpAddr,FollowConstant.LOCAL_HOST)).stream().distinct().toList();
        return Result.ok(collect.stream().map(FollowOrderSendEntity::getSymbol).toList());
    }

    @PostMapping("orderSend")
    @Operation(summary = "下单")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<?> orderSend(@RequestBody @Valid FollowOrderSendVO vo) {
        FollowTraderEntity followTraderVO = followTraderService.getById(vo.getTraderId());
        if (ObjectUtil.isEmpty(followTraderVO)) {
            return Result.error("账号不存在");
        }
        if (vo.getStartSize().compareTo(vo.getEndSize())>0) {
            return Result.error("开始手数不能大于结束手数");
        }


        QuoteClient quoteClient = null;
        AbstractApiTrader abstractApiTrader = null;
        //检查vps是否正常
        FollowVpsEntity followVpsEntity = followVpsService.getById(followTraderVO.getServerId());
        if (  followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
            throw new ServerException("VPS服务异常，请检查");
        }

        if (followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) || followTraderVO.getType().equals(TraderTypeEnum.BARGAIN.getType())){
            abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap()
                    .get(vo.getTraderId().toString());

            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                    || !abstractApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(vo.getTraderId().toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    leaderApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                        log.info(followTraderVO.getId().toString()+"重复提交并等待完成");
                        quoteClient = leaderApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }else {
                    return Result.error("账号无法登录");
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }else {
            abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap()
                    .get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                    || !abstractApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(vo.getTraderId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    copierApiTrader.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (copierApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                        log.info(followTraderVO.getId()+"重复提交并等待完成");
                        quoteClient = copierApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }else {
                    return Result.error("账号无法登录");
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }

        }

        // 查看品种匹配 模板
        List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(followTraderVO.getTemplateId());
        Integer contract = followVarietyEntityList.stream().filter(o -> ObjectUtil.isNotEmpty(o.getStdSymbol()) && o.getStdSymbol().equals(vo.getSymbol())).findFirst()
                .map(FollowVarietyEntity::getStdContract)
                .orElse(0);

        String symbol1 = getSymbol(quoteClient,vo.getTraderId(), vo.getSymbol());
        vo.setSymbol(symbol1);
        log.info("标准合约大小{}", contract);
        try {
            double ask = getQuoteOrRetry(quoteClient, vo.getSymbol());
        } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
            return Result.error(followTraderVO.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种");
        } catch (InterruptedException e) {
            return Result.error(followTraderVO.getAccount() + " 操作被中断");
        }

        boolean result = followTraderService.orderSend(vo, quoteClient, FollowTraderConvert.INSTANCE.convert(followTraderVO), contract);
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
        return Result.ok(page);
    }

    @GetMapping("orderSlipPoint")
    @Operation(summary = "滑点分析列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderSlipPointVO>> orderSlipPoint(@ParameterObject @Valid FollowOrderSpliListQuery query) {

        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
            query.setSymbolList(Arrays.asList(query.getSymbol().split(",")));
        }
        if (ObjectUtil.isNotEmpty(query.getTraderId())) {
            query.setTraderIdList(Arrays.asList(query.getTraderId().split(",")));
        }
        PageResult<FollowOrderSlipPointVO> followOrderSlipPointVOPageResult = followTraderService.pageSlipPoint(query);
        Integer total = followOrderSlipPointVOPageResult.getList().stream().mapToInt(FollowOrderSlipPointVO::getSymbolNum).sum();
        followOrderSlipPointVOPageResult.getList().stream().forEach(o -> o.setTotalNum(total));
        List<FollowOrderSlipPointVO> collect = followOrderSlipPointVOPageResult.getList().stream().sorted(Comparator.comparing(FollowOrderSlipPointVO::getTraderId)).collect(Collectors.toList());
        followOrderSlipPointVOPageResult.setList(collect);
        return Result.ok(followOrderSlipPointVOPageResult);
    }

    @GetMapping("orderSlipDetail")
    @Operation(summary = "订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderDetailVO>> orderSlipDetail(@ParameterObject @Valid FollowOrderSendQuery query) {
        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        return Result.ok(followOrderDetailVOPageResult);
    }

    @GetMapping("orderDoing/{traderId}")
    @Operation(summary = "正在进行订单详情")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowOrderSendEntity> orderDoing(@PathVariable("traderId") Long traderId) {
        return Result.ok(followTraderService.orderDoing(traderId));
    }

    @PostMapping("orderClose")
    @Operation(summary = "平仓")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> orderClose(@RequestBody @Valid FollowOrderSendCloseVO vo) {
        if(vo.getIsCloseAll() ==null || vo.getIsCloseAll()== TraderRepairEnum.SEND.getType()){
            checkParams(vo);
        }
        FollowTraderEntity followTraderVO = followTraderService.getById(vo.getTraderId());
        vo.setAccount(followTraderVO.getAccount());
        if (ObjectUtil.isEmpty(followTraderVO)) {
            throw new ServerException("账号不存在");
        }
        //检查vps是否正常
        FollowVpsEntity followVpsEntity = followVpsService.getById(followTraderVO.getServerId());
        if (followVpsEntity.getIsOpen().equals(CloseOrOpenEnum.CLOSE.getValue()) || followVpsEntity.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
            throw new ServerException("VPS服务异常，请检查");
        }
        AbstractApiTrader abstractApiTrader;
        QuoteClient quoteClient = null;
        if (followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) || followTraderVO.getType().equals(TraderTypeEnum.BARGAIN.getType())){
            abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(vo.getTraderId().toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS ) {
                    quoteClient=leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    leaderApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                        log.info(followTraderVO.getId().toString()+"重复提交并等待完成");
                        quoteClient = leaderApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }else {
            abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient) || !abstractApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(followTraderVO.getId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient=copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader1 = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    copierApiTrader1.setTrader(followTraderVO);
                    copierApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (copierApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                        log.info(followTraderVO.getId()+"重复提交并等待完成");
                        quoteClient = copierApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }
        //获取vps数据
        if (ObjectUtil.isEmpty(quoteClient)){
            throw new ServerException(vo.getAccount()+"登录异常");
        }

        if (ObjectUtil.isNotEmpty(vo.getSymbol())) {
            String symbol1 = getSymbol(quoteClient,vo.getTraderId(), vo.getSymbol());
            vo.setSymbol(symbol1);
            try {
                // 获取报价信息
                double ask = getQuoteOrRetry(quoteClient, vo.getSymbol());
            } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
                throw new ServerException(followTraderVO.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种", e);
            } catch (InterruptedException e) {
                throw new ServerException(followTraderVO.getAccount() + " 操作被中断", e);
            }
        }
        boolean result = followTraderService.orderClose(vo, quoteClient);
        if (!result) {
            return Result.error(followTraderVO.getAccount() + "正在平仓,请稍后再试");
        }
        return Result.ok(true);
    }

    @GetMapping("traderOverview")
    @Operation(summary = "数据概览")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<TraderOverviewVO> traderOverview(HttpServletRequest request) {
        String serverIp = FollowConstant.LOCAL_HOST;
        return Result.ok(followTraderService.traderOverview(serverIp));
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
        List<Long> collectPlat = new ArrayList<>();
        List<Long> collectBroke = new ArrayList<>();
        //处理券商查询逻辑，找出相关账号查询
        if (ObjectUtil.isNotEmpty(query.getBrokeName())) {
            List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(query.getBrokeName());
            if (ObjectUtil.isNotEmpty(serverEntityList)) {
                List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getPlatform, serverEntityList.stream().map(FollowBrokeServerEntity::getServerName).collect(Collectors.toList())));
                collectBroke = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
            }
        }
        if (ObjectUtil.isNotEmpty(query.getPlatform())) {
            List<FollowTraderEntity> list = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getPlatform, query.getPlatform()));
            collectPlat = list.stream().map(entity -> entity.getId()).collect(Collectors.toList());
        }
        // 计算交集
        if (ObjectUtil.isNotEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat.retainAll(collectBroke); // collectPlat 会变成 collectPlat 和 collectBroke 的交集
        } else if (ObjectUtil.isEmpty(collectPlat) && ObjectUtil.isNotEmpty(collectBroke)) {
            collectPlat = collectBroke;
        }
        if (ObjectUtil.isNotEmpty(collectPlat)) {
            query.setTraderIdList(collectPlat);
        }

        if (ObjectUtil.isEmpty(query.getStartTime()) || ObjectUtil.isEmpty(query.getEndTime())) {
            return Result.error("开仓时间为必填项");
        }
        PageResult<FollowOrderDetailVO> followOrderDetailVOPageResult = followTraderService.orderSlipDetail(query);
        detailService.export(followOrderDetailVOPageResult.getList());
        return Result.ok();
    }

    @GetMapping("stopOrder")
    @Operation(summary = "停止下单/平仓")
    @OperateLog(type = OperateTypeEnum.UPDATE)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> stopOrder(@Parameter(description = "type") Integer type, @Parameter(description = "traderId") String traderId) {
        Boolean result = followTraderService.stopOrder(type, traderId);
        if (!result) {
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            return Result.error(followTraderEntity.getAccount() + "暂无进行中下单/平仓操作");
        }
        return Result.ok();
    }

    @GetMapping("reconnection")
    @Operation(summary = "重连账号")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<Boolean> reconnection(@Parameter(description = "traderId") String traderId) {
        //批量重连
        String[] split = traderId.split(",");
        Arrays.asList(split).forEach(o->{
            Boolean reconnect = reconnect(o);
            if (!reconnect){
                log.info("重连失败，请检查账号密码"+o);
            }
        });
        return Result.ok();
    }
    @GetMapping("/specificationList")
    @Operation(summary = "品种规格列表")
    public Result<PageResult<FollowSysmbolSpecificationVO>> page(@ParameterObject @Valid FollowSysmbolSpecificationQuery query) {
        PageResult<FollowSysmbolSpecificationVO> page = followSysmbolSpecificationService.page(query);
        return Result.ok(page);
    }
    @GetMapping("/getSpecificationList")
    @Operation(summary = "品种规格列表")
    public Result<List<FollowSysmbolSpecificationEntity>> getSpecificationList(@ParameterObject FollowSysmbolSpecificationQuery query) {
        List<FollowSysmbolSpecificationEntity>  symbols= followTraderService.getSpecificationList(query);

        return Result.ok(symbols);
    }



  

    private Boolean reconnect(String traderId) {
        Boolean result=false;
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
            if (followTraderEntity.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) || followTraderEntity.getType().equals(TraderTypeEnum.BARGAIN.getType())){
                leaderApiTradersAdmin.removeTrader(traderId);
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderService.getById(traderId));
                if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                    followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                    followTraderEntity.setStatusExtra(conCodeEnum.getDescription());
                    followTraderService.updateById(followTraderEntity);
                    log.error("喊单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                    throw new ServerException("重连失败");
                }  else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                    // 开始等待直到获取到copierApiTrader1
                    while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                        log.info(traderId+"重复提交并等待完成");
                    }else {
                        log.info(traderId+"重复提交并等待失败");
                    }
                    result=true;
                } else {
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(traderId);
                    //判断是否获取过品种规格
                    List<FollowSysmbolSpecificationEntity> list = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
                    if(ObjectUtil.isEmpty(list)) {
                        followTraderService.addSysmbolSpecification(followTraderEntity,leaderApiTrader.quoteClient);
                    }
                    log.info("喊单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), leaderApiTrader.quoteClient.Host, leaderApiTrader.quoteClient.Port);
                    leaderApiTrader.startTrade();
                    result=true;
                }
            }else {
                copierApiTradersAdmin.removeTrader(traderId);
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderService.getById(traderId));
                if (conCodeEnum != ConCodeEnum.SUCCESS&&conCodeEnum != ConCodeEnum.AGAIN) {
                    followTraderEntity.setStatus(TraderStatusEnum.ERROR.getValue());
                    followTraderEntity.setStatusExtra(conCodeEnum.getDescription());
                    followTraderService.updateById(followTraderEntity);
                    log.error("跟单者:[{}-{}-{}]重连失败，请校验", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName());
                    throw new ServerException("重连失败");
                } else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                    // 开始等待直到获取到copierApiTrader1
                    while (copierApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                        log.info(traderId+"重复提交并等待完成");
                        result=true;
                    }else {
                        log.info(traderId+"重复提交并等待失败");
                    }
                }  else {

                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(traderId);
                    //判断是否获取过品种规格
                    List<FollowSysmbolSpecificationEntity> list = followSysmbolSpecificationService.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId));
                    if(ObjectUtil.isEmpty(list)) {
                        followTraderService.addSysmbolSpecification(followTraderEntity,copierApiTrader.quoteClient);
                    }
                    log.info("跟单者:[{}-{}-{}-{}]在[{}:{}]重连成功", followTraderEntity.getId(), followTraderEntity.getAccount(), followTraderEntity.getServerName(), followTraderEntity.getPassword(), copierApiTrader.quoteClient.Host, copierApiTrader.quoteClient.Port);
                    copierApiTrader.startTrade();
                    result=true;
                }
            }
        }catch (Exception e){
            log.info("重连错误"+e.getMessage());
            result=false;
        }
        return result;
    }

//    @GetMapping("traderSymbol")
//    @Operation(summary = "获取对应Symbol")
//    @PreAuthorize("hasAuthority('mascontrol:trader')")
//    public Result<String> traderSymbol(@Parameter(description = "symbol") String symbol, @Parameter(description = "traderId") Long traderId) {
//        String symbol1 = getSymbol(traderId, symbol);
//        return Result.ok(symbol1);
//    }

    @GetMapping("orderCloseList")
    @Operation(summary = "平仓列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderCloseVO>> orderSendList(@ParameterObject @Valid FollowOrderCloseQuery query) {
        PageResult<FollowOrderCloseVO> page = followOrderCloseService.page(query);
        return Result.ok(page);
    }

    private void  checkParams(FollowOrderSendCloseVO vo){
//        if (vo.getNum()==null && vo.getFlag().equals( TraderRepairEnum.SEND.getType())) {
//            throw new ServerException("总单数最少一单");
//        }
        if(ObjectUtil.isEmpty(vo.getSymbol()) && vo.getFlag().equals( CloseOrOpenEnum.CLOSE.getValue())){
            throw  new ServerException("品种不能为空");
        }
        if(ObjectUtil.isEmpty(vo.getType()) && vo.getFlag().equals( CloseOrOpenEnum.CLOSE.getValue())){
            throw  new ServerException("订单方向不能为空");
        }
        if(ObjectUtil.isEmpty(vo.getFlag())){
            throw  new ServerException("是否全平不能为空");
        }
    }

    private String getSymbol(QuoteClient quoteClient,Long traderId, String symbol) {

        //查询平台信息
        FollowPlatformEntity followPlatform = followPlatformService.getPlatFormById(followTraderService.getFollowById(traderId).getPlatformId().toString());
        //获取symbol信息
        List<FollowSysmbolSpecificationEntity> specificationServiceByTraderId = followSysmbolSpecificationService.getByTraderId(traderId);

        FollowTraderEntity followTraderEntity = followTraderService.getById(traderId);
        if (ObjectUtil.isNotEmpty(symbol)) {
            //增加forex
            int flag=0;
            String forex = symbol+followTraderEntity.getForex();
            if(ObjectUtil.isNotEmpty(followTraderEntity.getForex()) && forex.contains(symbol)){
                List<FollowSysmbolSpecificationEntity> list = specificationServiceByTraderId.stream().filter(item -> item.getSymbol().equals(forex)).toList();
                for (FollowSysmbolSpecificationEntity o : list) {
                    log.info("品种规格获取报价"+o.getSymbol());
                    //获取报价
                    QuoteEventArgs eventArgs= getEventArgs(quoteClient,o.getSymbol());
                    if (ObjectUtil.isNotEmpty(eventArgs)) {
                        return o.getSymbol();
                    }
                }
                flag=1;
            }
            String cfd =  symbol+followTraderEntity.getCfd();
            if(ObjectUtil.isNotEmpty(followTraderEntity.getCfd()) && cfd.contains(symbol)){
                List<FollowSysmbolSpecificationEntity> list = specificationServiceByTraderId.stream().filter(item -> item.getSymbol().equals(cfd)).toList();
                for (FollowSysmbolSpecificationEntity o : list) {
                    log.info("品种规格获取报价"+o.getSymbol());
                    //获取报价
                    QuoteEventArgs eventArgs= getEventArgs(quoteClient,o.getSymbol());
                    if (ObjectUtil.isNotEmpty(eventArgs)) {
                        return o.getSymbol();
                    }
                }
                flag=1;
            }
            if (flag==1){
                log.info("未精准匹配成功"+traderId);
            }else {
                // 先查品种规格
                List<FollowSysmbolSpecificationEntity> specificationEntity = specificationServiceByTraderId.stream().filter(item -> item.getSymbol().contains(symbol)).toList();
                if (ObjectUtil.isNotEmpty(specificationEntity)){
                    for (FollowSysmbolSpecificationEntity o : specificationEntity) {
                        log.info("品种规格获取报价"+o.getSymbol());
                        //获取报价
                        QuoteEventArgs eventArgs= getEventArgs(quoteClient,o.getSymbol());
                        if (ObjectUtil.isNotEmpty(eventArgs)) {
                            return o.getSymbol();
                        }
                    }
                }
            }
            // 查看品种匹配 模板
            List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(followTraderEntity.getTemplateId());
            List<FollowVarietyEntity> list = followVarietyEntityList.stream().filter(o ->ObjectUtil.isNotEmpty(o.getBrokerName())&& o.getBrokerName().equals(followPlatform.getBrokerName()) && o.getStdSymbol().equals(symbol)).toList();
            QuoteEventArgs eventArgs;
            for (FollowVarietyEntity o : list) {
                if (ObjectUtil.isNotEmpty(o.getBrokerSymbol())) {
                    //获取报价
                    eventArgs= getEventArgs(quoteClient,o.getBrokerSymbol());
                    if (ObjectUtil.isNotEmpty(eventArgs)){
                        return o.getBrokerSymbol();
                    }
                }
            }
        }
        return symbol;
    }

    private QuoteEventArgs getEventArgs(QuoteClient quoteClient,String symbol){
        QuoteEventArgs eventArgs = null;
        try {
            if (ObjectUtil.isEmpty(quoteClient.GetQuote(symbol))){
                //订阅
                quoteClient.Subscribe(symbol);
            }
            while (eventArgs==null && quoteClient.Connected()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                eventArgs=quoteClient.GetQuote(symbol);
            }
            eventArgs = quoteClient.GetQuote(symbol);
            return eventArgs;
        }catch (InvalidSymbolException | TimeoutException | ConnectException e) {
            log.info("获取报价失败,品种不正确,请先配置品种");
            return eventArgs;
        }
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

    @PostMapping("reconnectionServer")
    @Operation(summary = "重连服务器账号")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<Map<String, Boolean>> reconnectionServer(@RequestBody String severName) {
        // 查询serverName中的所有信息
        List<FollowTraderEntity> list = followTraderService.listByServerName(severName);
        log.info("查询到serverName为{}的账号数为{}", severName, list.size());
        Map<String, Boolean> reconnectResults = new HashMap<>();
        List<FollowVpsEntity> vps = followVpsService.list();
        Map<Integer, FollowVpsEntity> map = vps.stream().collect(Collectors.toMap(FollowVpsEntity::getId, Function.identity()));
        for (FollowTraderEntity followTraderEntity : list) {
            FollowVpsEntity followVpsEntity = map.get(followTraderEntity.getServerId());
            if(followVpsEntity.getIsActive()==CloseOrOpenEnum.OPEN.getValue()){
                String traderId = followTraderEntity.getId().toString();
                Boolean reconnect = reconnect(traderId);
                reconnectResults.put(traderId, reconnect);
            }

        }

        return Result.ok(reconnectResults);
    }

    @PostMapping("test")
    @Operation(summary = "手动定时测速任务")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<String> test() throws IOException {
        speedTestTask.weeklySpeedTest();
        return Result.ok("手动测速成功");
    }

    @PostMapping("reconnectionTrader")
    @Operation(summary = "重连账号")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<Boolean> reconnectionTrader(@RequestBody FollowTraderVO vo) {
        QuoteClient quoteClient = null;
        Long traderId = vo.getId();
        String s = AesUtils.decryptStr(vo.getPassword());
        String s1 = AesUtils.decryptStr(vo.getNewPassword());
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);
        quoteClient = followApiService.getQuoteClient(traderId, entity, quoteClient);
        log.info("账号内容：{}",quoteClient);
        try {
            if (ObjectUtil.isNotEmpty(quoteClient)) {
                quoteClient.ChangePassword(s1, false);
            }
        } catch (IOException e) {
            throw new ServerException("MT4修改密码异常,检查参数"+"密码："+s+"是否投资密码"+ false+",异常原因"+e);
        } catch (online.mtapi.mt4.Exception.ServerException e) {
            throw new ServerException("mt4修改密码异常,检查参数"+"密码："+s+"是否投资密码"+ false+",异常原因"+e);
        }

            LambdaUpdateWrapper<FollowTraderUserEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(FollowTraderUserEntity::getAccount, vo.getAccount())
                    .set(FollowTraderUserEntity::getPassword, vo.getNewPassword());
            followTraderUserService.update(wrapper);
            //修改密码
            LambdaUpdateWrapper<FollowTraderEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FollowTraderEntity::getId, traderId)
                    .set(FollowTraderEntity::getPassword,vo.getNewPassword());
            followTraderService.update(updateWrapper);
            reconnect(String.valueOf(traderId));
        return Result.ok();
    }

    @PostMapping("reconnectionUpdateTrader")
    @Operation(summary = "重连账号，不涉及MT4修改密码")
    @PreAuthorize("hasAuthority('mascontrol:speed')")
    public Result<Boolean> reconnectionUpdateTrader(@RequestBody FollowTraderVO vo) {
        Long traderId = vo.getId();
//        vo.setPassword(AesUtils.decryptStr(vo.getPassword()));
        LambdaUpdateWrapper<FollowTraderUserEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(FollowTraderUserEntity::getAccount, vo.getAccount())
                .set(FollowTraderUserEntity::getPassword, vo.getNewPassword());
        followTraderUserService.update(wrapper);
        //修改密码
        LambdaUpdateWrapper<FollowTraderEntity> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(FollowTraderEntity::getId, traderId)
                .set(FollowTraderEntity::getPassword, vo.getNewPassword());
        followTraderService.update(updateWrapper);
        reconnect(String.valueOf(traderId));
        return Result.ok();
    }

    @PostMapping("/synchData/{traderId}")
    @Operation(summary = "同步数据")
    public Result<String> synchData(@PathVariable("traderId") Long traderId) throws IOException {
        if(traderId==null){
            throw new ServerException("账号id不能为空");
        }
        FollowTraderEntity traderEntity = followTraderService.getById(traderId);
        if(traderEntity==null){
            throw new ServerException("账号id不存在");
        }
        List<FollowTraderEntity> newList = new ArrayList<>();
        obtainOrderHistoryTask.update(traderEntity,newList);
        return Result.ok("同步成功");
    }

    @GetMapping("sendAccountList")
    @Operation(summary = "下单账号列表")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<List<FollowSendAccountListVO>> sendAccountList() {
        List<FollowSendAccountListVO> page = followTraderService.accountPage();
        return Result.ok(page);
    }

    @PostMapping("masOrdersend")
    @Operation(summary = "交易下单")
    @OperateLog(type = OperateTypeEnum.INSERT)
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<FollowMasOrderVo> masOrdersend(@RequestBody @Valid MasToSubOrderSendDto vo) {
        FollowTraderEntity followTraderVO = followTraderService.getById(vo.getTraderId());
        QuoteClient quoteClient = null;
        AbstractApiTrader abstractApiTrader = null;
        if (followTraderVO.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) || followTraderVO.getType().equals(TraderTypeEnum.BARGAIN.getType())){
            abstractApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap()
                    .get(vo.getTraderId().toString());

            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                    || !abstractApiTrader.quoteClient.Connected()) {
                leaderApiTradersAdmin.removeTrader(vo.getTraderId().toString());
                ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    LeaderApiTrader leaderApiTrader1 = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    leaderApiTrader1.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (leaderApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(leaderApiTrader)){
                        log.info(followTraderVO.getId().toString()+"重复提交并等待完成");
                        quoteClient = leaderApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }else {
                    return Result.error("账号无法登录");
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }
        }else {
            abstractApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap()
                    .get(vo.getTraderId().toString());
            if (ObjectUtil.isEmpty(abstractApiTrader) || ObjectUtil.isEmpty(abstractApiTrader.quoteClient)
                    || !abstractApiTrader.quoteClient.Connected()) {
                copierApiTradersAdmin.removeTrader(vo.getTraderId().toString());
                ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderVO);
                if (conCodeEnum == ConCodeEnum.SUCCESS) {
                    quoteClient = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(vo.getTraderId().toString()).quoteClient;
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    copierApiTrader.startTrade();
                }else if (conCodeEnum == ConCodeEnum.AGAIN){
                    long maxWaitTimeMillis = 10000; // 最多等待10秒
                    long startTime = System.currentTimeMillis();
                    CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    // 开始等待直到获取到copierApiTrader1
                    while (copierApiTrader == null && (System.currentTimeMillis() - startTime) < maxWaitTimeMillis) {
                        try {
                            // 每次自旋等待500ms后再检查
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // 处理中断
                            Thread.currentThread().interrupt();
                            break;
                        }
                        copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                    }
                    //重复提交
                    if (ObjectUtil.isNotEmpty(copierApiTrader)){
                        log.info(followTraderVO.getId()+"重复提交并等待完成");
                        quoteClient = copierApiTrader.quoteClient;
                    }else {
                        log.info(followTraderVO.getId()+"重复提交并等待失败");
                    }
                }else {
                    return Result.error("账号无法登录");
                }
            } else {
                quoteClient = abstractApiTrader.quoteClient;
            }

        }

        // 查看品种匹配 模板
        List<FollowVarietyEntity> followVarietyEntityList = followVarietyService.getListByTemplated(abstractApiTrader.getTrader().getTemplateId());
        Integer contract = followVarietyEntityList.stream().filter(o -> ObjectUtil.isNotEmpty(o.getStdSymbol()) && o.getStdSymbol().equals(vo.getSymbol())).findFirst()
                .map(FollowVarietyEntity::getStdContract)
                .orElse(0);

        String symbol1 = getSymbol(quoteClient,vo.getTraderId(), vo.getSymbol());
        vo.setSymbol(symbol1);
        log.info("标准合约大小{}", contract);
        try {
            double ask = getQuoteOrRetry(quoteClient, vo.getSymbol());
        } catch (InvalidSymbolException | TimeoutException | ConnectException e) {
            return Result.error(followTraderVO.getAccount() + " 获取报价失败, 品种不正确, 请先配置品种");
        } catch (InterruptedException e) {
            return Result.error(followTraderVO.getAccount() + " 操作被中断");
        }

        FollowMasOrderVo followMasOrderVo=followTraderService.masOrdersend(vo, quoteClient, FollowTraderConvert.INSTANCE.convert(followTraderVO), contract);

        return Result.ok(followMasOrderVo);
    }

}