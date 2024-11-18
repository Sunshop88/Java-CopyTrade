package net.maku.subcontrol.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.FollowModeEnum;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
                followTraderService.removeById(followTraderEntity.getId());
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
        FollowTraderVO followTrader = FollowTraderConvert.INSTANCE.convert(vo);
        followTraderService.update(followTrader);
        //保存从表数据
        sourceService.edit(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delSource(SourceDelVo vo) {
        delete(Collections.singletonList(vo.getId()));
        //删除从表数据
        sourceService.del(vo.getId());
        return true;
    }

    private void convert(FollowInsertVO followInsertVO, FollowAddSalveVo followAddSalveVo) {
        //喊单账号
        followAddSalveVo.setTraderId(followInsertVO.getSourceId());
        //账号
        followAddSalveVo.setAccount(followInsertVO.getUser().toString());
        //备注
        followAddSalveVo.setRemark(followInsertVO.getComment());
        //模式-跟单方向 0=正跟 1=反跟 ,
        followAddSalveVo.setFollowDirection(followInsertVO.getDirection());
        //跟随模式 需要转换
        followAddSalveVo.setFollowMode(FollowModeEnum.getVal(followInsertVO.getMode()));
        //跟单参数 q
        followAddSalveVo.setFollowParam(followInsertVO.getModeValue());
        //跟单状态 q
        followAddSalveVo.setFollowStatus(followInsertVO.getStatus());
        //跟单开仓状态不能为空
        followAddSalveVo.setFollowOpen(followInsertVO.getOpenOrderStatus());
        //跟单平仓状态不能为空
        followAddSalveVo.setFollowClose(followInsertVO.getCloseOrderStatus());
        //跟单补单状态不能为空
        followAddSalveVo.setFollowRep(followInsertVO.getRepairStatus());
        //下单方式
        followAddSalveVo.setPlacedType(followInsertVO.getPlacedType());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertFollow(FollowInsertVO vo) {
        //参数转化
        FollowAddSalveVo followAddSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        //  convert(vo, followAddSalveVo);  //根据平台id查询平台
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

 /*   private void convertUpdate(FollowUpdateVO followUpdateVO, FollowUpdateSalveVo followUpdateSalveVo) {
        FollowPlatformEntity platform = followPlatformService.getById(followUpdateVO.getPlatformId());
        if (ObjectUtil.isEmpty(platform)) {
            throw new ServerException("服务商错误");
        }
        //备注
        followUpdateSalveVo.setRemark(followUpdateVO.getComment());
        //模式-跟单方向 0=正跟 1=反跟 ,
        followUpdateSalveVo.setFollowDirection(followUpdateVO.getDirection());
        //跟随模式 需要转换
        followUpdateSalveVo.setFollowMode(FollowModeEnum.getVal(followUpdateVO.getMode()));
        //跟单参数 q
        followUpdateSalveVo.setFollowParam(followUpdateVO.getModeValue());
        //跟单状态 q
        followUpdateSalveVo.setFollowStatus(followUpdateVO.getStatus());
        //跟单开仓状态不能为空
        followUpdateSalveVo.setFollowOpen(followUpdateVO.getOpenOrderStatus());
        //跟单平仓状态不能为空
        followUpdateSalveVo.setFollowClose(followUpdateVO.getCloseOrderStatus());
        //跟单补单状态不能为空
        followUpdateSalveVo.setFollowRep(followUpdateVO.getRepairStatus());
        //下单方式
        followUpdateSalveVo.setPlacedType(followUpdateVO.getPlacedType());
    }*/

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateFollow(FollowUpdateVO vo) {
        FollowUpdateSalveVo followUpdateSalveVo = FollowTraderConvert.INSTANCE.convert(vo);
        // convertUpdate(vo, followUpdateSalveVo);
        // 判断主表如果保存失败，则返回false
        Boolean result = updateSlave(followUpdateSalveVo);
        if (!result) {
            return false;
        }
        //处理副表数据
        followService.edit(vo);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delFollow(SourceDelVo vo) {
        delete(Collections.singletonList(vo.getId()));
        //删除从表数据
        followService.del(vo.getId());
        return true;
    }
}
