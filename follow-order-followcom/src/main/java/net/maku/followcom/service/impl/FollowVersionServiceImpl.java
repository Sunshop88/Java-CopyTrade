package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowVersionConvert;
import net.maku.followcom.dao.FollowVersionDao;
import net.maku.followcom.entity.FollowVersionEntity;
import net.maku.followcom.query.FollowVersionQuery;
import net.maku.followcom.service.FollowVersionService;
import net.maku.followcom.vo.FollowVersionVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目版本
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowVersionServiceImpl extends BaseServiceImpl<FollowVersionDao, FollowVersionEntity> implements FollowVersionService {
    private final TransService transService;

    @Override
    public PageResult<FollowVersionVO> page(FollowVersionQuery query) {
        IPage<FollowVersionEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowVersionConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowVersionEntity> getWrapper(FollowVersionQuery query){
        LambdaQueryWrapper<FollowVersionEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowVersionVO get(Long id) {
        FollowVersionEntity entity = baseMapper.selectById(id);
        FollowVersionVO vo = FollowVersionConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowVersionVO vo) {
        FollowVersionEntity entity = FollowVersionConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowVersionVO vo) {
        FollowVersionEntity entity = FollowVersionConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }

}