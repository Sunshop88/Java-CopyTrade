package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowFailureDetailConvert;
import net.maku.followcom.dao.FollowFailureDetailDao;
import net.maku.followcom.entity.FollowFailureDetailEntity;
import net.maku.followcom.query.FollowFailureDetailQuery;
import net.maku.followcom.service.FollowFailureDetailService;
import net.maku.followcom.vo.FollowFailureDetailVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 失败详情表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowFailureDetailServiceImpl extends BaseServiceImpl<FollowFailureDetailDao, FollowFailureDetailEntity> implements FollowFailureDetailService {

    @Override
    public PageResult<FollowFailureDetailVO> page(FollowFailureDetailQuery query) {
        IPage<FollowFailureDetailEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowFailureDetailConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowFailureDetailEntity> getWrapper(FollowFailureDetailQuery query){
        LambdaQueryWrapper<FollowFailureDetailEntity> wrapper = Wrappers.lambdaQuery();
        if (ObjectUtil.isNotEmpty(query.getRecordId())){
            wrapper.eq(FollowFailureDetailEntity::getRecordId,query.getRecordId());
        }
        if (ObjectUtil.isNotEmpty(query.getType())){
            wrapper.eq(FollowFailureDetailEntity::getType,query.getType());
        }
        return wrapper;
    }


    @Override
    public FollowFailureDetailVO get(Long id) {
        FollowFailureDetailEntity entity = baseMapper.selectById(id);
        FollowFailureDetailVO vo = FollowFailureDetailConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowFailureDetailVO vo) {
        FollowFailureDetailEntity entity = FollowFailureDetailConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowFailureDetailVO vo) {
        FollowFailureDetailEntity entity = FollowFailureDetailConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }



}