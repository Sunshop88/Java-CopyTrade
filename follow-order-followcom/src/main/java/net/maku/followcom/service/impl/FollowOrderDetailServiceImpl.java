package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.ThreadPoolUtils;
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
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final FollowOrderDetailDao followOrderDetailDao;
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
    public void export(List<FollowOrderDetailVO> followOrderDetailVOList) {
        List<FollowOrderDetailExcelVO> excelList = FollowOrderDetailConvert.INSTANCE.convertExcelList3(followOrderDetailVOList);
        excelList.parallelStream().forEach(o->{
            //设置类型
            if (o.getType()==0){
                o.setTypeName("BUY");
            }else{
                o.setTypeName("SELL");
            }
        });
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowOrderDetailExcelVO.class, "订单列表", null, excelList);
    }

    @Override
    public PageResult<FollowOrderSlipPointVO> listFollowOrderSlipPoint(FollowOrderSpliListQuery query) {
        Page<?> pageRequest = new Page<>(query.getPage(), query.getLimit());
     //   query.setServer(FollowConstant.LOCAL_HOST);
        Page<FollowOrderSlipPointVO> page = followOrderDetailDao.getFollowOrderDetailStats(pageRequest,query);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

}