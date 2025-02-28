package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowOrderInstructConvert;
import net.maku.followcom.dao.FollowOrderInstructDao;
import net.maku.followcom.entity.FollowOrderInstructEntity;
import net.maku.followcom.query.FollowOrderInstructQuery;
import net.maku.followcom.service.FollowOrderInstructService;
import net.maku.followcom.vo.FollowOrderInstructVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.mascontrol.vo.FollowOrderInstructExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 下单总指令表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderInstructServiceImpl extends BaseServiceImpl<FollowOrderInstructDao, FollowOrderInstructEntity> implements FollowOrderInstructService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderInstructVO> page(FollowOrderInstructQuery query) {
        IPage<FollowOrderInstructEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderInstructConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderInstructEntity> getWrapper(FollowOrderInstructQuery query){
        LambdaQueryWrapper<FollowOrderInstructEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowOrderInstructVO get(Long id) {
        FollowOrderInstructEntity entity = baseMapper.selectById(id);
        FollowOrderInstructVO vo = FollowOrderInstructConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderInstructVO vo) {
        FollowOrderInstructEntity entity = FollowOrderInstructConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderInstructVO vo) {
        FollowOrderInstructEntity entity = FollowOrderInstructConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderInstructExcelVO> excelList = FollowOrderInstructConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderInstructExcelVO.class, "下单总指令表", null, excelList);
    }

}