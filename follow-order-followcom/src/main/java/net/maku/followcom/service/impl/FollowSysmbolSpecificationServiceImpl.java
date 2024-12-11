package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowSysmbolSpecificationConvert;
import net.maku.followcom.dao.FollowSysmbolSpecificationDao;
import net.maku.followcom.entity.FollowSysmbolSpecificationEntity;
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.query.FollowSysmbolSpecificationQuery;
import net.maku.followcom.service.FollowSysmbolSpecificationService;
import net.maku.followcom.vo.FollowSysmbolSpecificationExcelVO;
import net.maku.followcom.vo.FollowSysmbolSpecificationVO;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 品种规格
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowSysmbolSpecificationServiceImpl extends BaseServiceImpl<FollowSysmbolSpecificationDao, FollowSysmbolSpecificationEntity> implements FollowSysmbolSpecificationService {
    private final TransService transService;

    @Override
    public PageResult<FollowSysmbolSpecificationVO> page(FollowSysmbolSpecificationQuery query) {
        IPage<FollowSysmbolSpecificationEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowSysmbolSpecificationConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowSysmbolSpecificationEntity> getWrapper(FollowSysmbolSpecificationQuery query) {
        LambdaQueryWrapper<FollowSysmbolSpecificationEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(query.getTraderId() != null, FollowSysmbolSpecificationEntity::getTraderId, query.getTraderId());
        wrapper.like(ObjectUtil.isNotEmpty(query.getSymbol()), FollowSysmbolSpecificationEntity::getSymbol, query.getSymbol());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getProfitMode()), FollowSysmbolSpecificationEntity::getProfitMode, query.getProfitMode());
        return wrapper;
    }


    @Override
    public FollowSysmbolSpecificationVO get(Long id) {
        FollowSysmbolSpecificationEntity entity = baseMapper.selectById(id);
        FollowSysmbolSpecificationVO vo = FollowSysmbolSpecificationConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowSysmbolSpecificationVO vo) {
        FollowSysmbolSpecificationEntity entity = FollowSysmbolSpecificationConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowSysmbolSpecificationVO vo) {
        FollowSysmbolSpecificationEntity entity = FollowSysmbolSpecificationConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
        List<FollowSysmbolSpecificationExcelVO> excelList = FollowSysmbolSpecificationConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowSysmbolSpecificationExcelVO.class, "品种规格", null, excelList);
    }

    @Override
    @Cacheable(
            value = "followSymbolCache",
            key = "#traderId ?: 'defaultKey'",
            unless = "#result == null"
    )
    public Map<String, FollowSysmbolSpecificationEntity> getByTraderId(long traderId) {
        return this.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId)).stream().collect(Collectors.toMap(FollowSysmbolSpecificationEntity::getSymbol, i -> i));
    }

}