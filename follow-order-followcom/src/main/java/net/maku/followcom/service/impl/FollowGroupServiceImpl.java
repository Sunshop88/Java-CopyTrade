package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowGroupConvert;
import net.maku.followcom.dao.FollowGroupDao;
import net.maku.followcom.entity.FollowGroupEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowGroupQuery;
import net.maku.followcom.service.FollowGroupService;
import net.maku.followcom.service.FollowTraderUserService;
import net.maku.followcom.vo.FollowGroupExcelVO;
import net.maku.followcom.vo.FollowGroupVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组别
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowGroupServiceImpl extends BaseServiceImpl<FollowGroupDao, FollowGroupEntity> implements FollowGroupService {
    private final TransService transService;

    @Override
    public PageResult<FollowGroupVO> page(FollowGroupQuery query) {
        //需先查询账号数量更新到数据库里

        IPage<FollowGroupEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowGroupConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowGroupEntity> getWrapper(FollowGroupQuery query){
        LambdaQueryWrapper<FollowGroupEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowGroupVO get(Long id) {
        FollowGroupEntity entity = baseMapper.selectById(id);
        FollowGroupVO vo = FollowGroupConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowGroupVO vo) {
        FollowGroupEntity entity = FollowGroupConvert.INSTANCE.convert(vo);
        entity.setNumber(0);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowGroupVO vo) {

        FollowGroupEntity entity = FollowGroupConvert.INSTANCE.convert(vo);
        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowGroupExcelVO> excelList = FollowGroupConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowGroupExcelVO.class, "组别", null, excelList);
    }

}