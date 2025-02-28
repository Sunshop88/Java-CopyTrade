package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowOrderInstructSubConvert;
import net.maku.followcom.dao.FollowOrderInstructSubDao;
import net.maku.followcom.entity.FollowOrderInstructSubEntity;
import net.maku.followcom.query.FollowOrderInstructSubQuery;
import net.maku.followcom.service.FollowOrderInstructSubService;
import net.maku.followcom.vo.FollowOrderInstructSubExcelVO;
import net.maku.followcom.vo.FollowOrderInstructSubVO;
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
 * 下单子指令
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderInstructSubServiceImpl extends BaseServiceImpl<FollowOrderInstructSubDao, FollowOrderInstructSubEntity> implements FollowOrderInstructSubService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderInstructSubVO> page(FollowOrderInstructSubQuery query) {
        IPage<FollowOrderInstructSubEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderInstructSubConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderInstructSubEntity> getWrapper(FollowOrderInstructSubQuery query){
        LambdaQueryWrapper<FollowOrderInstructSubEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowOrderInstructSubVO get(Long id) {
        FollowOrderInstructSubEntity entity = baseMapper.selectById(id);
        FollowOrderInstructSubVO vo = FollowOrderInstructSubConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderInstructSubVO vo) {
        FollowOrderInstructSubEntity entity = FollowOrderInstructSubConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderInstructSubVO vo) {
        FollowOrderInstructSubEntity entity = FollowOrderInstructSubConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderInstructSubExcelVO> excelList = FollowOrderInstructSubConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderInstructSubExcelVO.class, "下单子指令", null, excelList);
    }

}