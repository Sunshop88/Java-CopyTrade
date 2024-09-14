package net.maku.mascontrol.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.mascontrol.convert.FollowVpsConvert;
import net.maku.mascontrol.dao.FollowVpsDao;
import net.maku.mascontrol.entity.FollowVpsEntity;
import net.maku.mascontrol.query.FollowVpsQuery;
import net.maku.mascontrol.service.FollowVpsService;
import net.maku.mascontrol.vo.FollowVpsExcelVO;
import net.maku.mascontrol.vo.FollowVpsVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowVpsServiceImpl extends BaseServiceImpl<FollowVpsDao, FollowVpsEntity> implements FollowVpsService {
    private final TransService transService;

    @Override
    public PageResult<FollowVpsVO> page(FollowVpsQuery query) {
        IPage<FollowVpsEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowVpsVO> followVpsVOS = FollowVpsConvert.INSTANCE.convertList(page.getRecords());
        followVpsVOS.stream().forEach(o->{
            Date date = Date.from(o.getExpiryDate().atZone(ZoneId.systemDefault()).toInstant());
            o.setRemainingDay(Math.toIntExact(DateUtil.betweenDay(date, DateUtil.date(), false)));
        });
        return new PageResult<>(followVpsVOS, page.getTotal());
    }


    private LambdaQueryWrapper<FollowVpsEntity> getWrapper(FollowVpsQuery query){
        LambdaQueryWrapper<FollowVpsEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowVpsEntity::getDeleted,query.getDeleted());
        return wrapper;
    }


    @Override
    public FollowVpsVO get(Long id) {
        FollowVpsEntity entity = baseMapper.selectById(id);
        FollowVpsVO vo = FollowVpsConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowVpsVO vo) {
        FollowVpsEntity entity = FollowVpsConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowVpsVO vo) {
        FollowVpsEntity entity = FollowVpsConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Integer> idList) {
        list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getId, idList)).forEach(entity -> {
            // 删除
            entity.setDeleted(CloseOrOpenEnum.OPEN.getValue());
            updateById(entity);
        });
    }


    @Override
    public void export() {
    List<FollowVpsExcelVO> excelList = FollowVpsConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowVpsExcelVO.class, "vps列表", null, excelList);
    }

}