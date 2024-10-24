package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowOrderSendConvert;
import net.maku.followcom.entity.FollowOrderSendEntity;
import net.maku.followcom.query.FollowOrderSendQuery;
import net.maku.followcom.vo.FollowOrderSendVO;
import net.maku.followcom.dao.FollowOrderSendDao;
import net.maku.followcom.service.FollowOrderSendService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowOrderSendExcelVO;
import net.maku.framework.common.excel.ExcelFinishCallBack;
import org.springframework.web.multipart.MultipartFile;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 下单记录
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowOrderSendServiceImpl extends BaseServiceImpl<FollowOrderSendDao, FollowOrderSendEntity> implements FollowOrderSendService {
    private final TransService transService;

    @Override
    public PageResult<FollowOrderSendVO> page(FollowOrderSendQuery query) {
        IPage<FollowOrderSendEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowOrderSendVO> followOrderSendVOS = FollowOrderSendConvert.INSTANCE.convertList(page.getRecords());
        followOrderSendVOS.forEach(o->o.setPlatform(o.getServer()));
        return new PageResult<>(followOrderSendVOS, page.getTotal());

    }


    private LambdaQueryWrapper<FollowOrderSendEntity> getWrapper(FollowOrderSendQuery query){
        LambdaQueryWrapper<FollowOrderSendEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowOrderSendEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue());
        if (ObjectUtil.isNotEmpty(query.getTraderId())){
            wrapper.eq(FollowOrderSendEntity::getTraderId,query.getTraderId());
        }
        if (ObjectUtil.isNotEmpty(query.getAccount())) {
            wrapper.eq(FollowOrderSendEntity::getAccount,query.getAccount());
        }
        if (ObjectUtil.isNotEmpty(query.getSymbol())) {
            wrapper.eq(FollowOrderSendEntity::getSymbol,query.getSymbol());
        }
        if (ObjectUtil.isNotEmpty(query.getStartTime())&&ObjectUtil.isNotEmpty(query.getEndTime())){
            wrapper.ge(FollowOrderSendEntity::getCreateTime, query.getStartTime());  // 大于或等于开始时间
            wrapper.le(FollowOrderSendEntity::getCreateTime, query.getEndTime());    // 小于或等于结束时间
        }
        wrapper.orderByDesc(FollowOrderSendEntity::getCreateTime);
        return wrapper;
    }


    @Override
    public FollowOrderSendVO get(Long id) {
        FollowOrderSendEntity entity = baseMapper.selectById(id);
        FollowOrderSendVO vo = FollowOrderSendConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowOrderSendVO vo) {
        FollowOrderSendEntity entity = FollowOrderSendConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowOrderSendVO vo) {
        FollowOrderSendEntity entity = FollowOrderSendConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowOrderSendExcelVO> excelList = FollowOrderSendConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderSendExcelVO.class, "下单记录", null, excelList);
    }

}