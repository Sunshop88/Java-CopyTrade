package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowOrderDetailConvert;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.query.FollowOrderDetailQuery;
import net.maku.followcom.vo.FollowOrderDetailVO;
import net.maku.followcom.dao.FollowOrderDetailDao;
import net.maku.followcom.service.FollowOrderDetailService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowOrderDetailExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderDetailServiceImpl extends BaseServiceImpl<FollowOrderDetailDao, FollowOrderDetailEntity> implements FollowOrderDetailService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderDetailVO> page(FollowOrderDetailQuery query) {
        IPage<FollowOrderDetailEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderDetailConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderDetailEntity> getWrapper(FollowOrderDetailQuery query){
        LambdaQueryWrapper<FollowOrderDetailEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowOrderDetailVO get(Long id) {
        FollowOrderDetailEntity entity = baseMapper.selectById(id);
        FollowOrderDetailVO vo = FollowOrderDetailConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderDetailVO vo) {
        FollowOrderDetailEntity entity = FollowOrderDetailConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderDetailVO vo) {
        FollowOrderDetailEntity entity = FollowOrderDetailConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderDetailExcelVO> excelList = FollowOrderDetailConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderDetailExcelVO.class, "订单详情", null, excelList);
    }

}