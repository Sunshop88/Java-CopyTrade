package net.maku.followcom.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.api.module.entity.SysUserEntity;
import net.maku.api.module.system.UserApi;
import net.maku.followcom.convert.FollowOrderInstructConvert;
import net.maku.followcom.dao.FollowOrderInstructDao;
import net.maku.followcom.entity.FollowOrderInstructEntity;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowOrderInstructQuery;
import net.maku.followcom.service.FollowOrderInstructService;
import net.maku.followcom.service.UserService;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final UserService userService;

    @Override
    public PageResult<FollowOrderInstructVO> page(FollowOrderInstructQuery query) {
        IPage<FollowOrderInstructEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

//        return new PageResult<>(FollowOrderInstructConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
        List<FollowOrderInstructEntity> records = page.getRecords();
        List<FollowOrderInstructVO> vos = FollowOrderInstructConvert.INSTANCE.convertList(records);

        // 获取 creatorName
        List<Long> creatorIds = records.stream()
                .map(FollowOrderInstructEntity::getCreator)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> creatorNameMap = userService.getUserName(creatorIds);

        for (FollowOrderInstructVO vo : vos) {
            vo.setCreatorName(creatorNameMap.get(vo.getCreator()));
        }
        //查询用户名
        if (ObjectUtil.isNotEmpty(query.getCreator())){
            vos = vos.stream()
                    //过滤出当前用户
                    .filter(vo -> creatorNameMap.get(vo.getCreator()).equals(query.getCreator()))
                    .collect(Collectors.toList());
        }

        return new PageResult<>(vos, page.getTotal());
    }


    private LambdaQueryWrapper<FollowOrderInstructEntity> getWrapper(FollowOrderInstructQuery query){
        LambdaQueryWrapper<FollowOrderInstructEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ObjectUtil.isNotEmpty(query.getType()), FollowOrderInstructEntity::getType, query.getType());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getInstructionType()), FollowOrderInstructEntity::getInstructionType, query.getInstructionType());
        if (ObjectUtil.isNotEmpty(query.getSymbol())){
            String symbol = query.getSymbol().replaceAll("%", "\\\\%");
            wrapper.like(FollowOrderInstructEntity::getSymbol,symbol);
        }
    //    List<Integer> userIdList = userApi.getUser(query.getCreator());
//        List<Integer> userIdList =null;
//        wrapper.in(ObjectUtil.isNotEmpty(userIdList), FollowOrderInstructEntity::getCreator, userIdList);
//        wrapper.like(ObjectUtil.isNotEmpty(query.getCreator()),FollowOrderInstructEntity);
        //如果没有时间，默认一个月
        if (ObjectUtil.isNotEmpty(query.getStartTime()) && ObjectUtil.isNotEmpty(query.getEndTime())) {
            wrapper.ge(FollowOrderInstructEntity::getCreateTime, query.getStartTime());
            wrapper.le(FollowOrderInstructEntity::getCreateTime, query.getEndTime());
        } else {
            // 默认近一个月的数据
            LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startTime = oneMonthAgo.format(formatter);
            String endTime = now.format(formatter);
            wrapper.ge(FollowOrderInstructEntity::getCreateTime, startTime);
            wrapper.le(FollowOrderInstructEntity::getCreateTime, endTime);
        }
          wrapper.orderByDesc(FollowOrderInstructEntity::getCreateTime);
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