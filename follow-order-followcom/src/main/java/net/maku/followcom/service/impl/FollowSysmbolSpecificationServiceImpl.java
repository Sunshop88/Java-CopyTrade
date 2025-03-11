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
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.FollowSysmbolSpecificationQuery;
import net.maku.followcom.service.FollowSysmbolSpecificationService;
import net.maku.followcom.vo.FollowSysmbolSpecificationExcelVO;
import net.maku.followcom.vo.FollowSysmbolSpecificationVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
//    private final FollowTraderService followTraderService;

    @Override
    public PageResult<FollowSysmbolSpecificationVO> page(FollowSysmbolSpecificationQuery query) {
        IPage<FollowSysmbolSpecificationEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowSysmbolSpecificationEntity> list = page.getRecords();
        List<FollowSysmbolSpecificationVO> voList = FollowSysmbolSpecificationConvert.INSTANCE.convertList(list);
        Map<Long, FollowTraderEntity> traderMap = new HashMap<>();

        for (FollowSysmbolSpecificationVO vo : voList) {

        }

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
    public List<FollowSysmbolSpecificationEntity> getByTraderId(long traderId) {
        return this.list(new LambdaQueryWrapper<FollowSysmbolSpecificationEntity>().eq(FollowSysmbolSpecificationEntity::getTraderId, traderId)).stream().toList();
    }

    @Override
    public PageResult<FollowSysmbolSpecificationVO> pageSpecification(FollowSysmbolSpecificationQuery query) {
//        List<FollowSysmbolSpecificationEntity> entities = list();
//        List<FollowSysmbolSpecificationVO> vos = FollowSysmbolSpecificationConvert.INSTANCE.convertList(entities);
//        vos.forEach(vo -> {
//            FollowTraderVO followTraderVO = map.get(vo.getId());
//            vo.setBrokerName(followTraderVO.getBrokerName());
//            vo.setServerName(followTraderVO.getServerName());
//            vo.setAccount(followTraderVO.getAccount());
//            vo.setAccountType(followTraderVO.getPlatformType());
//        });
//        //搜索
//        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
//            //模糊匹配
//            vos = vos.stream().filter(vo -> vo.getSymbol().contains(query.getSymbol())).toList();
//        }
//        if (ObjectUtil.isNotEmpty(query.getBrokerName())) {
//            //精准匹配
//            vos = vos.stream().filter(vo -> vo.getBrokerName().equals(query.getBrokerName())).toList();
//        }
//        if (ObjectUtil.isNotEmpty(query.getServerName())) {
//            //精准匹配
//            vos = vos.stream().filter(vo -> vo.getServerName().equals(query.getServerName())).toList();
//        }
//        if (ObjectUtil.isNotEmpty(query.getAccount())) {
//            //精准匹配
//            vos = vos.stream().filter(vo -> vo.getAccount().contains(query.getAccount())).toList();
//        }
//        if (ObjectUtil.isNotEmpty(query.getAccountType())) {
//            //精准匹配
//            vos = vos.stream().filter(vo -> vo.getAccountType().equals(query.getAccountType())).toList();
//        }
        List<FollowSysmbolSpecificationVO> vos = baseMapper.selectSpecification(query);
        // 分页参数
        int pageSize = query.getLimit(); // 每页大小
        int currentPage = query.getPage(); // 当前页码

        // 计算分页数据
        int totalSize = vos.size(); // 总记录数
        int fromIndex = (currentPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, totalSize);

        // 提取当前页的数据
        List<FollowSysmbolSpecificationVO> pagedVos = vos.subList(fromIndex, toIndex);

        // 创建分页结果
        PageResult<FollowSysmbolSpecificationVO> pageResult = new PageResult<>(pagedVos, totalSize);
        return pageResult;
    }

}