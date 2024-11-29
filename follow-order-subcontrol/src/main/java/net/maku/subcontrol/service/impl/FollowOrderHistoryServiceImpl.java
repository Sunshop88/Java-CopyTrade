package net.maku.subcontrol.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import net.maku.subcontrol.query.FollowOrderHistoryQuery;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.subcontrol.convert.FollowOrderHistoryConvert;
import net.maku.subcontrol.dao.FollowOrderHistoryDao;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import net.maku.subcontrol.service.FollowOrderHistoryService;
import net.maku.subcontrol.vo.FollowOrderHistoryExcelVO;
import net.maku.subcontrol.vo.FollowOrderHistoryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderHistoryServiceImpl extends BaseServiceImpl<FollowOrderHistoryDao, FollowOrderHistoryEntity> implements FollowOrderHistoryService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderHistoryVO> page(FollowOrderHistoryQuery query) {
        IPage<FollowOrderHistoryEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderHistoryConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderHistoryEntity> getWrapper(FollowOrderHistoryQuery query){
        LambdaQueryWrapper<FollowOrderHistoryEntity> wrapper = Wrappers.lambdaQuery();
        if (ObjectUtil.isNotEmpty(query.getTraderId())){
            wrapper.eq(FollowOrderHistoryEntity::getTraderId,query.getTraderId());
        }
        wrapper.gt(ObjectUtil.isNotEmpty(query.getStartTime()),FollowOrderHistoryEntity::getCloseTime,query.getStartTime());
        wrapper.lt(ObjectUtil.isNotEmpty(query.getEndTime()),FollowOrderHistoryEntity::getCloseTime,query.getEndTime());
        return wrapper;
    }


    @Override
    public FollowOrderHistoryVO get(Long id) {
        FollowOrderHistoryEntity entity = baseMapper.selectById(id);
        FollowOrderHistoryVO vo = FollowOrderHistoryConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderHistoryVO vo) {
        FollowOrderHistoryEntity entity = FollowOrderHistoryConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderHistoryVO vo) {
        FollowOrderHistoryEntity entity = FollowOrderHistoryConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderHistoryExcelVO> excelList = FollowOrderHistoryConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderHistoryExcelVO.class, "所有MT4账号的历史订单", null, excelList);
    }

}