package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowOrderActiveConvert;
import net.maku.followcom.entity.FollowOrderActiveEntity;
import net.maku.followcom.query.FollowOrderActiveQuery;
import net.maku.followcom.vo.FollowOrderActiveVO;
import net.maku.followcom.dao.FollowOrderActiveDao;
import net.maku.followcom.service.FollowOrderActiveService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowOrderActiveExcelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账号持仓订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderActiveServiceImpl extends BaseServiceImpl<FollowOrderActiveDao, FollowOrderActiveEntity> implements FollowOrderActiveService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderActiveVO> page(FollowOrderActiveQuery query) {
        IPage<FollowOrderActiveEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderActiveConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderActiveEntity> getWrapper(FollowOrderActiveQuery query){
        LambdaQueryWrapper<FollowOrderActiveEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowOrderActiveVO get(Long id) {
        FollowOrderActiveEntity entity = baseMapper.selectById(id);
        FollowOrderActiveVO vo = FollowOrderActiveConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderActiveVO vo) {
        FollowOrderActiveEntity entity = FollowOrderActiveConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderActiveVO vo) {
        FollowOrderActiveEntity entity = FollowOrderActiveConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderActiveExcelVO> excelList = FollowOrderActiveConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderActiveExcelVO.class, "账号持仓订单", null, excelList);
    }


}