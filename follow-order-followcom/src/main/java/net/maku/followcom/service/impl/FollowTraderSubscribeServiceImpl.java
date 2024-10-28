package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowTraderSubscribeConvert;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.query.FollowTraderSubscribeQuery;
import net.maku.followcom.vo.FollowTraderSubscribeVO;
import net.maku.followcom.dao.FollowTraderSubscribeDao;
import net.maku.followcom.service.FollowTraderSubscribeService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowTraderSubscribeExcelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;

/**
 * 订阅关系表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderSubscribeServiceImpl extends BaseServiceImpl<FollowTraderSubscribeDao, FollowTraderSubscribeEntity> implements FollowTraderSubscribeService {
    private final TransService transService;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public PageResult<FollowTraderSubscribeVO> page(FollowTraderSubscribeQuery query) {
        IPage<FollowTraderSubscribeEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderSubscribeConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderSubscribeEntity> getWrapper(FollowTraderSubscribeQuery query){
        LambdaQueryWrapper<FollowTraderSubscribeEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowTraderSubscribeVO get(Long id) {
        FollowTraderSubscribeEntity entity = baseMapper.selectById(id);
        FollowTraderSubscribeVO vo = FollowTraderSubscribeConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderSubscribeVO vo) {
        FollowTraderSubscribeEntity entity = FollowTraderSubscribeConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderSubscribeVO vo) {
        FollowTraderSubscribeEntity entity = FollowTraderSubscribeConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowTraderSubscribeExcelVO> excelList = FollowTraderSubscribeConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderSubscribeExcelVO.class, "订阅关系表", null, excelList);
    }

    @Override
    public List<String> initSubscriptions(Long id) {
        //清空缓存该跟单者所有的订阅关系的缓存
        redisUtil.del(id.toString());

        //从数据库中获取到该跟单者的所有订阅关系
        List<FollowTraderSubscribeEntity> masterSlaves = this.list(Wrappers.<FollowTraderSubscribeEntity>lambdaQuery().eq(FollowTraderSubscribeEntity::getSlaveId, id)
                .eq(FollowTraderSubscribeEntity::getDeleted, CloseOrOpenEnum.OPEN.getValue()));

        List<String> subscriptions = new LinkedList<>();
        for (FollowTraderSubscribeEntity item : masterSlaves) {
            subscriptions.add(item.getMasterId().toString());
            this.redisUtil.hSet(id.toString(), item.getMasterId().toString(), item);
        }
        return subscriptions;
    }

    @Override
    public FollowTraderSubscribeEntity subscription(Long slaveId, Long masterId) {
        //查看缓存
        Object hGet = this.redisUtil.hGet(slaveId.toString(), masterId.toString());
        if (!ObjectUtils.isEmpty(hGet)) {
            //缓存存在，直接返回
            return (FollowTraderSubscribeEntity) hGet;
        } else {
            FollowTraderSubscribeEntity masterSlave = this.getOne(Wrappers.<FollowTraderSubscribeEntity>lambdaQuery()
                    .eq(FollowTraderSubscribeEntity::getMasterId, masterId)
                    .eq(FollowTraderSubscribeEntity::getSlaveId, slaveId));

            if (!ObjectUtils.isEmpty(masterSlave)) {
                //数据库中存在
                this.redisUtil.hSet(slaveId.toString(), masterId.toString(), masterSlave);
                return masterSlave;
            } else {
                //数据库中不存在
                return null;
            }
        }
    }

}