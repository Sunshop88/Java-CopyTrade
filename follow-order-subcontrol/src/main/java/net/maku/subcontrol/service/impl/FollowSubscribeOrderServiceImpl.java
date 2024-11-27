package net.maku.subcontrol.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
}