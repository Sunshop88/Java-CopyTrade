package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowUploadTraderUserConvert;
import net.maku.followcom.dao.FollowUploadTraderUserDao;
import net.maku.followcom.entity.FollowUploadTraderUserEntity;
import net.maku.followcom.query.FollowUploadTraderUserQuery;
import net.maku.followcom.service.FollowUploadTraderUserService;
import net.maku.followcom.vo.FollowUploadTraderUserVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 上传账号记录表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowUploadTraderUserServiceImpl extends BaseServiceImpl<FollowUploadTraderUserDao, FollowUploadTraderUserEntity> implements FollowUploadTraderUserService {

    @Override
    public PageResult<FollowUploadTraderUserVO> page(FollowUploadTraderUserQuery query) {
        IPage<FollowUploadTraderUserEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowUploadTraderUserConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowUploadTraderUserEntity> getWrapper(FollowUploadTraderUserQuery query){
        LambdaQueryWrapper<FollowUploadTraderUserEntity> wrapper = Wrappers.lambdaQuery();
        if (ObjectUtil.isNotEmpty(query.getType())){
            wrapper.eq(FollowUploadTraderUserEntity::getType,query.getType());
        }
        return wrapper;
    }


    @Override
    public FollowUploadTraderUserVO get(Long id) {
        FollowUploadTraderUserEntity entity = baseMapper.selectById(id);
        FollowUploadTraderUserVO vo = FollowUploadTraderUserConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowUploadTraderUserVO vo) {
        FollowUploadTraderUserEntity entity = FollowUploadTraderUserConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowUploadTraderUserVO vo) {
        FollowUploadTraderUserEntity entity = FollowUploadTraderUserConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }



}