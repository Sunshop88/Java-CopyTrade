package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.dao.FollowTraderDao;
import net.maku.followcom.service.FollowTraderService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowTraderExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderServiceImpl extends BaseServiceImpl<FollowTraderDao, FollowTraderEntity> implements FollowTraderService {
    private final TransService transService;

    @Override
    public PageResult<FollowTraderVO> page(FollowTraderQuery query) {
        IPage<FollowTraderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderEntity> getWrapper(FollowTraderQuery query){
        LambdaQueryWrapper<FollowTraderEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowTraderVO get(Long id) {
        FollowTraderEntity entity = baseMapper.selectById(id);
        FollowTraderVO vo = FollowTraderConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderVO vo) {
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderVO vo) {
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowTraderExcelVO> excelList = FollowTraderConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderExcelVO.class, "mt4账号", null, excelList);
    }

}