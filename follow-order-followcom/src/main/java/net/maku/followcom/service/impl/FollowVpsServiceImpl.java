package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import kotlin.jvm.internal.Lambda;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowVpsConvert;
import net.maku.followcom.dao.FollowVpsDao;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.vo.FollowVpsExcelVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.RandomStringUtil;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
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
//        followVpsVOS.stream().forEach(o->{
//            Date startDate = DateUtil.offsetDay(Date.from(o.getExpiryDate().atZone(ZoneId.systemDefault()).toInstant()), -10);
//            Date endDate = DateUtil.date();
//            long daysBetween = DateUtil.between(startDate, endDate, DateUnit.DAY);
//            if (endDate.after(startDate)) {
//                daysBetween = -daysBetween;
//            }
//            o.setRemainingDay((int)daysBetween);
//        });

        followVpsVOS.stream().forEach(o -> {
            Date startDate = DateUtil.offsetDay(Date.from(o.getExpiryDate().atZone(ZoneId.systemDefault()).toInstant()), 0);
            Date endDate = DateUtil.date();
            long daysBetween = DateUtil.between(startDate, endDate, DateUnit.DAY);
            if (endDate.after(startDate)) {
                o.setRemainingDay(-1); // 已过期
            } else {
                o.setRemainingDay((int) daysBetween);
            }
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
        vo.setClientId(RandomStringUtil.generateUUIDClientId());
        FollowVpsEntity entity = FollowVpsConvert.INSTANCE.convert(vo);
        List<FollowVpsEntity> list = this.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress, vo.getIpAddress()).or().eq(FollowVpsEntity::getName,vo.getName()));
        if (ObjectUtil.isNotEmpty(list)){
            throw new ServerException("重复名称或ip地址,请重新输入");
        }
        baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowVpsVO vo) {
        FollowVpsEntity entity = FollowVpsConvert.INSTANCE.convert(vo);
        FollowVpsVO followVpsVO = this.get(Long.valueOf(vo.getId()));
        if (ObjectUtil.notEqual(vo.getName(),followVpsVO.getName())){
            List<FollowVpsEntity> list = this.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName,vo.getName()));
            if (ObjectUtil.isNotEmpty(list)){
                throw new ServerException("重复名称,请重新输入");
            }
        }
//        baseMapper.update(entity, new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getId, entity.getId()));
        baseMapper.updateVps(entity);
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

    @Override
    public List<FollowVpsVO> listByVps() {
        LambdaQueryWrapper<FollowVpsEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowVpsEntity::getName)
                .groupBy(FollowVpsEntity::getName);
        List<FollowVpsEntity> list = baseMapper.selectList(wrapper);
        return FollowVpsConvert.INSTANCE.convertList(list);
    }
}