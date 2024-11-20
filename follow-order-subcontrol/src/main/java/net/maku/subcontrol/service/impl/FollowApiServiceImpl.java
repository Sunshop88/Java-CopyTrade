package net.maku.subcontrol.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.*;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.*;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ThreadPoolUtils;
import net.maku.subcontrol.service.FollowApiService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.LeaderApiTrader;
import net.maku.subcontrol.trader.LeaderApiTradersAdmin;
import online.mtapi.mt4.PlacedType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class FollowApiServiceImpl implements FollowApiService {

    private static final Logger log = LoggerFactory.getLogger(FollowApiServiceImpl.class);
    private final FollowTraderService followTraderService;
    private final FollowPlatformService followPlatformService;
    private final RedisCache redisCache;
    private final LeaderApiTradersAdmin leaderApiTradersAdmin;
    private final SourceService sourceService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowService followService;

    /**
     * 喊单账号保存
     *
     * @return false保存失败true保存成功
     */
    @Override
    public Boolean save(FollowTraderVO vo) {
        //本机处理
        try {
            FollowTraderVO followTraderVO = followTraderService.save(vo);
            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(vo);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = leaderApiTradersAdmin.addTrader(convert);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                followTraderService.removeById(followTraderVO.getId());
                return false;
            }
            ThreadPoolUtils.execute(() -> {
                LeaderApiTrader leaderApiTrader = leaderApiTradersAdmin.getLeader4ApiTraderConcurrentHashMap().get(followTraderVO.getId().toString());
                leaderApiTrader.startTrade();
                followTraderService.saveQuo(leaderApiTrader.quoteClient, convert);
            });
        } catch (Exception e) {
            log.error("保存失败{}", String.valueOf(e));
            throw new ServerException("喊单账号添加异常:" + e);
        }
        return true;
    }

    @Override
    public void delete(List<Long> idList) {
        followTraderService.delete(idList);
        //清空缓存
        idList.stream().forEach(o -> leaderApiTradersAdmin.removeTrader(o.toString()));
    }

    @Override
    public Boolean addSlave(FollowAddSalveVo vo) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getTraderId());
            if (ObjectUtil.isEmpty(followTraderEntity)) {
                throw new ServerException("请输入正确喊单账号");
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
            FollowTraderVO followTraderVO = followTraderService.save(followTraderVo);

            FollowTraderEntity convert = FollowTraderConvert.INSTANCE.convert(followTraderVo);
            convert.setId(followTraderVO.getId());
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(convert);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                followTraderService.removeById(followTraderVO.getId());
                return false;
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
                redisCache.set(Constant.FOLLOW_MASTER_SLAVE + followTraderEntity.getAccount() + ":" + vo.getAccount(), JSONObject.toJSON(map));
            });
        } catch (Exception e) {
            throw new ServerException("保存失败" + e);
        }

        return true;
    }

    @Override
    public Boolean updateSlave(FollowUpdateSalveVo vo) {
        try {
            FollowTraderEntity followTraderEntity = followTraderService.getById(vo.getId());
            BeanUtil.copyProperties(vo, followTraderEntity);
            followTraderService.updateById(followTraderEntity);
            //查看绑定跟单账号
            FollowTraderSubscribeEntity followTraderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>()
                    .eq(FollowTraderSubscribeEntity::getSlaveId, vo.getId()));
            BeanUtil.copyProperties(vo, followTraderSubscribeEntity, "id");
            //更新订阅状态
            followTraderSubscribeService.updateById(followTraderSubscribeEntity);
            redisCache.delete(Constant.FOLLOW_MASTER_SLAVE + followTraderSubscribeEntity.getMasterAccount() + ":" + followTraderEntity.getAccount());
            //删除缓存
            copierApiTradersAdmin.removeTrader(followTraderEntity.getId().toString());
            //启动账户
            ConCodeEnum conCodeEnum = copierApiTradersAdmin.addTrader(followTraderEntity);
            if (!conCodeEnum.equals(ConCodeEnum.SUCCESS)) {
                //     followTraderService.removeById(followTraderEntity.getId());
                return false;
            }
            ThreadPoolUtils.execute(() -> {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(followTraderEntity.getId().toString());
                //设置下单方式
                copierApiTrader.orderClient.PlacedType = PlacedType.forValue(vo.getPlacedType());
                copierApiTrader.startTrade();
            });
        } catch (Exception e) {
            throw new ServerException("修改失败" + e);
        }

        return true;
    }

    /**
     * 喊单账号添加，主从表同时写入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertSource(SourceInsertVO vo) {
        //参数转换，转成主表数据
        FollowTraderVO followTrader = FollowTraderConvert.INSTANCE.convert(vo);
        //根据平台id查询平台
        FollowPlatformEntity platform = followPlatformService.getById(vo.getPlatformId());
        if (ObjectUtil.isEmpty(platform)) {
            throw new ServerException("暂无可用服务器商");
        }
        followTrader.setPlatform(platform.getServer());
        //判断主表如果保存失败，则返回false
        Boolean result = save(followTrader);
        if (!result) {
            return false;
        }
        //保存从表数据
        sourceService.add(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateSource(SourceUpdateVO vo) {
        SourceEntity source = sourceService.getEntityById(vo.getId());
        FollowTraderEntity followTrader = FollowTraderConvert.INSTANCE.convert(vo);
        LambdaQueryWrapper<FollowTraderEntity> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId());
        followTraderService.update(followTrader, lambdaQueryWrapper);
        //重连
        reconnect(vo.getId().toString());
        //保存从表数据
        sourceService.edit(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delSource(SourceDelVo vo) {
        SourceEntity source = sourceService.getEntityById(vo.getId());
        if (source == null) {
            return false;
        }
        List<Long> ids = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, source.getUser()).eq(FollowTraderEntity::getPlatformId, source.getPlatformId()).list().stream().map(FollowTraderEntity::getId).toList();
        delete(ids);
        //删除从表数据
        sourceService.del(vo.getId());
        return true;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertFollow(FollowInsertVO vo) {
        //参数转化
        FollowAddSalveVo followAddSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        //根据平台id查询平台
        FollowPlatformEntity platform = followPlatformService.getById(vo.getPlatformId());
        if (ObjectUtil.isEmpty(platform)) {
            throw new ServerException("暂无可用服务器商");
        }
        followAddSalveVo.setPlatform(platform.getServer());
        //判断主表如果保存失败，则返回false
        Boolean result = addSlave(followAddSalveVo);
        if (!result) {
            return false;
        }
        //处理副表数据
        followService.add(vo);
        return true;
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateFollow(FollowUpdateVO vo) {
        //查询从表
        FollowEntity followEntity = followService.getEntityById(vo.getId());
        //查询主表
        LambdaQueryWrapper<FollowTraderEntity> query = new LambdaQueryWrapper<>();
        query.eq(FollowTraderEntity::getAccount, followEntity.getUser()).eq(FollowTraderEntity::getPlatformId, followEntity.getPlatformId());
        FollowTraderEntity entity = followTraderService.getOne(query);
        FollowUpdateSalveVo followUpdateSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        followUpdateSalveVo.setId(entity.getId());
        String pwd = StringUtils.isNotBlank(vo.getPassword()) ? vo.getPassword() : entity.getPassword();
        followUpdateSalveVo.setPassword(pwd);
        // 判断主表如果保存失败，则返回false
        Boolean result = updateSlave(followUpdateSalveVo);
        if (!result) {
            return false;
        }
        //重连
        reconnect(vo.getId().toString());
        //处理副表数据
        followService.edit(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delFollow(SourceDelVo vo) {
        FollowEntity followEntity = followService.getEntityById(vo.getId());
        if (followEntity == null) {
            return false;
        }
        List<Long> ids = followTraderService.lambdaQuery().eq(FollowTraderEntity::getAccount, followEntity.getUser()).eq(FollowTraderEntity::getPlatformId, followEntity.getPlatformId()).list().stream().map(FollowTraderEntity::getId).toList();
        delete(ids);
        //删除从表数据
        followService.del(vo.getId());
        return true;
    }

    private void reconnect(String traderId) {
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
    }
}
