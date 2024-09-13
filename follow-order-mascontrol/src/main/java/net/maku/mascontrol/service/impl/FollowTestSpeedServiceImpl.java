package net.maku.mascontrol.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.mascontrol.convert.FollowTestSpeedConvert;
import net.maku.mascontrol.entity.FollowTestSpeedEntity;
import net.maku.mascontrol.query.FollowTestSpeedQuery;
import net.maku.mascontrol.vo.FollowTestSpeedVO;
import net.maku.mascontrol.dao.FollowTestSpeedDao;
import net.maku.mascontrol.service.FollowTestSpeedService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.mascontrol.vo.FollowTestSpeedExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 测速记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTestSpeedServiceImpl extends BaseServiceImpl<FollowTestSpeedDao, FollowTestSpeedEntity> implements FollowTestSpeedService {
    private final TransService transService;

    @Override
    public PageResult<FollowTestSpeedVO> page(FollowTestSpeedQuery query) {
        IPage<FollowTestSpeedEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTestSpeedConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTestSpeedEntity> getWrapper(FollowTestSpeedQuery query){
        LambdaQueryWrapper<FollowTestSpeedEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowTestSpeedVO get(Long id) {
        FollowTestSpeedEntity entity = baseMapper.selectById(id);
        FollowTestSpeedVO vo = FollowTestSpeedConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTestSpeedVO vo) {
        FollowTestSpeedEntity entity = FollowTestSpeedConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTestSpeedVO vo) {
        FollowTestSpeedEntity entity = FollowTestSpeedConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowTestSpeedExcelVO> excelList = FollowTestSpeedConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTestSpeedExcelVO.class, "测速记录", null, excelList);
    }

}