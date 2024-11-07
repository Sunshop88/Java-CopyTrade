package net.maku.subcontrol.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.vo.FollowSubscribeOrderVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.subcontrol.convert.FollowRepairOrderConvert;
import net.maku.subcontrol.entity.FollowRepairOrderEntity;
import net.maku.subcontrol.enums.TraderRepairOrderEnum;
import net.maku.subcontrol.query.FollowRepairOrderQuery;
import net.maku.subcontrol.service.FollowSubscribeOrderService;
import net.maku.subcontrol.vo.FollowRepairOrderVO;
import net.maku.subcontrol.dao.FollowRepairOrderDao;
import net.maku.subcontrol.service.FollowRepairOrderService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.subcontrol.vo.FollowRepairOrderExcelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 补单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowRepairOrderServiceImpl extends BaseServiceImpl<FollowRepairOrderDao, FollowRepairOrderEntity> implements FollowRepairOrderService {
    private final TransService transService;
    private final RedisCache redisCache;
    private final FollowSubscribeOrderService followSubscribeOrderService;
    @Override
    public PageResult<FollowRepairOrderVO> page(FollowRepairOrderQuery query) {
        IPage<FollowRepairOrderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowRepairOrderVO> followRepairOrderVOS = FollowRepairOrderConvert.INSTANCE.convertList(page.getRecords());
        followRepairOrderVOS.forEach(o->{
            if (o.getType().equals(TraderRepairOrderEnum.CLOSE.getType())){
                //未平仓展示跟单者 订单号 交易品种 手数 开仓时间
                FollowSubscribeOrderVO followSubscribeOrderVO = followSubscribeOrderService.get(o.getSubscribeId());
                o.setSlaveSymbol(followSubscribeOrderVO.getSlaveSymbol());
                o.setSlaveLots(followSubscribeOrderVO.getSlaveLots());
                o.setSlaveOpenTime(followSubscribeOrderVO.getSlaveOpenTime());
            }
        });
        return new PageResult<>(followRepairOrderVOS, page.getTotal());
    }


    private LambdaQueryWrapper<FollowRepairOrderEntity> getWrapper(FollowRepairOrderQuery query){
        LambdaQueryWrapper<FollowRepairOrderEntity> wrapper = Wrappers.lambdaQuery();
        //未完成状态
        wrapper.eq(FollowRepairOrderEntity::getStatus, CloseOrOpenEnum.CLOSE.getValue());
        return wrapper;
    }


    @Override
    public FollowRepairOrderVO get(Long id) {
        FollowRepairOrderEntity entity = baseMapper.selectById(id);
        FollowRepairOrderVO vo = FollowRepairOrderConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowRepairOrderVO vo) {
        FollowRepairOrderEntity entity = FollowRepairOrderConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowRepairOrderVO vo) {
        FollowRepairOrderEntity entity = FollowRepairOrderConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowRepairOrderExcelVO> excelList = FollowRepairOrderConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowRepairOrderExcelVO.class, "补单记录", null, excelList);
    }

}