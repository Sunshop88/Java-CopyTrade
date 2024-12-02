package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowOrderCloseConvert;
import net.maku.followcom.entity.FollowOrderCloseEntity;
import net.maku.followcom.query.FollowOrderCloseQuery;
import net.maku.followcom.vo.FollowOrderCloseVO;
import net.maku.followcom.dao.FollowOrderCloseDao;
import net.maku.followcom.service.FollowOrderCloseService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowOrderCloseExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 平仓记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderCloseServiceImpl extends BaseServiceImpl<FollowOrderCloseDao, FollowOrderCloseEntity> implements FollowOrderCloseService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderCloseVO> page(FollowOrderCloseQuery query) {
        IPage<FollowOrderCloseEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowOrderCloseConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderCloseEntity> getWrapper(FollowOrderCloseQuery query){
        LambdaQueryWrapper<FollowOrderCloseEntity> wrapper = Wrappers.lambdaQuery();
        //当前vps
        wrapper.eq(FollowOrderCloseEntity::getIpAddr, FollowConstant.LOCAL_HOST);
        if (ObjectUtil.isNotEmpty(query.getStartTime())&&ObjectUtil.isNotEmpty(query.getEndTime())){
            wrapper.ge(FollowOrderCloseEntity::getCreateTime, query.getStartTime());  // 大于或等于开始时间
            wrapper.le(FollowOrderCloseEntity::getCreateTime, query.getEndTime());    // 小于或等于结束时间
        }
        wrapper.orderByDesc(FollowOrderCloseEntity::getCreateTime);
        return wrapper;
    }


    @Override
    public FollowOrderCloseVO get(Long id) {
        FollowOrderCloseEntity entity = baseMapper.selectById(id);
        FollowOrderCloseVO vo = FollowOrderCloseConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderCloseVO vo) {
        FollowOrderCloseEntity entity = FollowOrderCloseConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderCloseVO vo) {
        FollowOrderCloseEntity entity = FollowOrderCloseConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderCloseExcelVO> excelList = FollowOrderCloseConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderCloseExcelVO.class, "平仓记录", null, excelList);
    }

}