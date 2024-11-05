package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowVpsUserConvert;
import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.query.FollowVpsUserQuery;
import net.maku.followcom.vo.FollowVpsUserVO;
import net.maku.followcom.dao.FollowVpsUserDao;
import net.maku.followcom.service.FollowVpsUserService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowVpsUserExcelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户vps可查看列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowVpsUserServiceImpl extends BaseServiceImpl<FollowVpsUserDao, FollowVpsUserEntity> implements FollowVpsUserService {
    private final TransService transService;

    @Override
    public PageResult<FollowVpsUserVO> page(FollowVpsUserQuery query) {
        IPage<FollowVpsUserEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowVpsUserConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowVpsUserEntity> getWrapper(FollowVpsUserQuery query){
        LambdaQueryWrapper<FollowVpsUserEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowVpsUserVO get(Long id) {
        FollowVpsUserEntity entity = baseMapper.selectById(id);
        FollowVpsUserVO vo = FollowVpsUserConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowVpsUserVO vo) {
        FollowVpsUserEntity entity = FollowVpsUserConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowVpsUserVO vo) {
        FollowVpsUserEntity entity = FollowVpsUserConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowVpsUserExcelVO> excelList = FollowVpsUserConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowVpsUserExcelVO.class, "用户vps可查看列表", null, excelList);
    }

}