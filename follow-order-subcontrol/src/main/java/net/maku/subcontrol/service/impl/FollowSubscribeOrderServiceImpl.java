package net.maku.subcontrol.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.enums.TraderRepairEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowTraderSubscribeService;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.subcontrol.query.FollowSubscribeOrderQuery;
import net.maku.followcom.vo.FollowSubscribeOrderExcelVO;
import net.maku.followcom.vo.FollowSubscribeOrderVO;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.subcontrol.convert.FollowSubscribeOrderConvert;
import net.maku.subcontrol.dao.FollowSubscribeOrderDao;
import net.maku.subcontrol.entity.FollowSubscribeOrderEntity;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.trader.CopierApiTrader;
import net.maku.subcontrol.trader.CopierApiTradersAdmin;
import net.maku.subcontrol.trader.strategy.AbstractOperation;
import net.maku.subcontrol.trader.strategy.OrderCloseCopier;
import net.maku.subcontrol.trader.strategy.OrderSendCopier;
import net.maku.subcontrol.vo.RepairSendVO;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowSubscribeOrderServiceImpl extends BaseServiceImpl<FollowSubscribeOrderDao, FollowSubscribeOrderEntity> implements FollowSubscribeOrderService {
    private final TransService transService;
    private final CopierApiTradersAdmin copierApiTradersAdmin;
    private final RedisUtil redisUtil;
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final RedissonLockUtil redissonLockUtil;
    @Override
    public PageResult<FollowSubscribeOrderVO> page(FollowSubscribeOrderQuery query) {
        IPage<FollowSubscribeOrderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowSubscribeOrderConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowSubscribeOrderEntity> getWrapper(FollowSubscribeOrderQuery query){
        LambdaQueryWrapper<FollowSubscribeOrderEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowSubscribeOrderVO get(Long id) {
        FollowSubscribeOrderEntity entity = baseMapper.selectById(id);
        FollowSubscribeOrderVO vo = FollowSubscribeOrderConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowSubscribeOrderVO vo) {
        FollowSubscribeOrderEntity entity = FollowSubscribeOrderConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowSubscribeOrderVO vo) {
        FollowSubscribeOrderEntity entity = FollowSubscribeOrderConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowSubscribeOrderExcelVO> excelList = FollowSubscribeOrderConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowSubscribeOrderExcelVO.class, "订阅关系表", null, excelList);
    }

    @Override
    public Boolean repairSend(RepairSendVO repairSendVO) {
        //避免重复点击
        if (redissonLockUtil.tryLockForShortTime(repairSendVO.getOrderNo().toString(), 0, 2, TimeUnit.SECONDS)) {
            try {
                CopierApiTrader copierApiTrader = copierApiTradersAdmin.getCopier4ApiTraderConcurrentHashMap().get(repairSendVO.getSlaveId().toString());
                if (ObjectUtil.isEmpty(copierApiTrader)){
                    throw new ServerException("漏单处理异常，账号不正确");
                }
                FollowTraderSubscribeEntity traderSubscribeEntity = followTraderSubscribeService.getOne(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, repairSendVO.getMasterId()).eq(FollowTraderSubscribeEntity::getSlaveId, repairSendVO.getSlaveId()));
                if (repairSendVO.getType().equals(TraderRepairEnum.SEND.getType())){
                    //获取redis内的下单信息
                    List<Object> objects = redisUtil.lGet(Constant.FOLLOW_REPAIR_SEND + traderSubscribeEntity.getId(),0,-1);
                    Optional<Object> first = objects.stream().filter(o -> {
                        EaOrderInfo eaOrderInfo = (EaOrderInfo) o;
                        return eaOrderInfo.getTicket().equals(repairSendVO.getOrderNo());
                    }).toList().stream().findFirst();
                    if (first.isPresent()){
                        ConsumerRecord consumerRecord= new ConsumerRecord<>("send",1,1,"send",first.get());
                        OrderSendCopier orderSendCopier = new OrderSendCopier(copierApiTrader);
                        orderSendCopier.operate(consumerRecord,1);
                        redisUtil.lRemove(Constant.FOLLOW_REPAIR_SEND + traderSubscribeEntity.getId(),1,first.get());
                    }else {
                        throw new ServerException("暂无订单需处理");
                    }
                }else {
                    //获取redis内的平仓信息
                    List<Object> objects = redisUtil.lGet(Constant.FOLLOW_REPAIR_CLOSE + traderSubscribeEntity.getId(),0,-1);
                    Optional<Object> first = objects.stream().filter(o -> ((EaOrderInfo)o).getTicket().equals(repairSendVO.getOrderNo())).toList().stream().findFirst();
                    if (first.isPresent()){
                        ConsumerRecord consumerRecord= new ConsumerRecord<>("close",1,1,"close",first.get());
                        OrderCloseCopier orderCloseCopier = new OrderCloseCopier(copierApiTrader);
                        orderCloseCopier.operate(consumerRecord,1);
                        redisUtil.lRemove(Constant.FOLLOW_REPAIR_CLOSE + traderSubscribeEntity.getId(),1,first.get());
                    }else {
                        throw new ServerException("暂无订单需处理");
                    }
                }
                return true;
            } finally {
                redissonLockUtil.unlock(repairSendVO.getOrderNo().toString());
            }
        } else {
            throw new ServerException("操作过于频繁，请稍后再试");
        }
    }
}