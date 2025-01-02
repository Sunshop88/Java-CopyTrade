package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowSpeedSettingConvert;
import net.maku.followcom.dao.FollowSpeedSettingDao;
import net.maku.followcom.entity.FollowSpeedSettingEntity;
import net.maku.followcom.query.FollowSpeedSettingQuery;
import net.maku.followcom.service.FollowSpeedSettingService;
import net.maku.followcom.vo.FollowSpeedSettingVO;
import net.maku.framework.common.utils.PageResult;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 测速配置
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowSpeedSettingServiceImpl extends BaseServiceImpl<FollowSpeedSettingDao, FollowSpeedSettingEntity> implements FollowSpeedSettingService {
    private final TransService transService;

    @Override
    public PageResult<FollowSpeedSettingVO> page(FollowSpeedSettingQuery query) {
        IPage<FollowSpeedSettingEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowSpeedSettingConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowSpeedSettingEntity> getWrapper(FollowSpeedSettingQuery query){
        LambdaQueryWrapper<FollowSpeedSettingEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowSpeedSettingVO get(Long id) {
        FollowSpeedSettingEntity entity = baseMapper.selectById(id);
        FollowSpeedSettingVO vo = FollowSpeedSettingConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowSpeedSettingVO vo) {
        FollowSpeedSettingEntity entity = FollowSpeedSettingConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowSpeedSettingVO vo) {
        FollowSpeedSettingEntity entity = FollowSpeedSettingConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }

}