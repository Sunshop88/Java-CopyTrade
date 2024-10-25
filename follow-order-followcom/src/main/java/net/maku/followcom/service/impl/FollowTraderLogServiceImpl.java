package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowTraderLogConvert;
import net.maku.followcom.entity.FollowTraderLogEntity;
import net.maku.followcom.query.FollowTraderLogQuery;
import net.maku.followcom.vo.FollowTraderLogVO;
import net.maku.followcom.dao.FollowTraderLogDao;
import net.maku.followcom.service.FollowTraderLogService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowTraderLogExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 交易日志
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderLogServiceImpl extends BaseServiceImpl<FollowTraderLogDao, FollowTraderLogEntity> implements FollowTraderLogService {
    private final TransService transService;

    @Override
    public PageResult<FollowTraderLogVO> page(FollowTraderLogQuery query) {
        IPage<FollowTraderLogEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderLogConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderLogEntity> getWrapper(FollowTraderLogQuery query){
        LambdaQueryWrapper<FollowTraderLogEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowTraderLogVO get(Long id) {
        FollowTraderLogEntity entity = baseMapper.selectById(id);
        FollowTraderLogVO vo = FollowTraderLogConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderLogVO vo) {
        FollowTraderLogEntity entity = FollowTraderLogConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderLogVO vo) {
        FollowTraderLogEntity entity = FollowTraderLogConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowTraderLogExcelVO> excelList = FollowTraderLogConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderLogExcelVO.class, "交易日志", null, excelList);
    }

}