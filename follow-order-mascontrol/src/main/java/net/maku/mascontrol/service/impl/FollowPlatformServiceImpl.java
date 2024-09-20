package net.maku.mascontrol.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import net.maku.mascontrol.convert.FollowPlatformConvert;
import net.maku.mascontrol.dao.FollowPlatformDao;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.eunm.PlatformType;
import net.maku.mascontrol.query.FollowPlatformQuery;
import net.maku.mascontrol.service.FollowPlatformService;
import net.maku.mascontrol.vo.FollowPlatformExcelVO;
import net.maku.mascontrol.vo.FollowPlatformVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * 平台管理
 *
 * @author 阿沐 babamu@126.com
 * @since 1.0.0 2024-09-11
 */
@Service
@AllArgsConstructor
public class FollowPlatformServiceImpl extends BaseServiceImpl<FollowPlatformDao, FollowPlatformEntity> implements FollowPlatformService {
    private final TransService transService;


    @Override
    public PageResult<FollowPlatformVO> page(FollowPlatformQuery query) {
        IPage<FollowPlatformEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowPlatformConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }

    //查询功能
    private LambdaQueryWrapper<FollowPlatformEntity> getWrapper(FollowPlatformQuery query){
        LambdaQueryWrapper<FollowPlatformEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowPlatformEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue());

        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowPlatformVO vo) {
        // 检查平台类型是否为 "MT4" 或 "MT5"
        if (!"MT4".equals(vo.getPlatformType()) && !"MT5".equals(vo.getPlatformType())) {
            throw new RuntimeException("平台类型必须为 'MT4' 或 'MT5'");
        }

        //查询输入的服务器是否存在
        if(ObjectUtil.isNotEmpty(baseMapper.selectOne(Wrappers.<FollowPlatformEntity>lambdaQuery()
                .eq(FollowPlatformEntity::getServer,vo.getServer())))) {
            throw new RuntimeException("服务器 " + vo.getServer() + " 已存在");
        }
        FollowPlatformEntity entity = FollowPlatformConvert.INSTANCE.convert(vo);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(entity);
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowPlatformVO vo) {
        //查询服务器是否存在如果不存在则返回服务器不存在
        if(ObjectUtil.isEmpty(baseMapper.selectOne(Wrappers.<FollowPlatformEntity>lambdaQuery()
                .eq(FollowPlatformEntity::getId,vo.getId())))) {
            throw new RuntimeException("服务器 " + vo.getServer() + " 不存在");
        }

        FollowPlatformEntity entity = FollowPlatformConvert.INSTANCE.convert(vo);
        //根据服务器修改备注内容
        entity.setUpdateTime(LocalDateTime.now());
        entity.setUpdater(SecurityUser.getUserId());
        baseMapper.updateByRemark(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        list(new LambdaQueryWrapper<FollowPlatformEntity>().in(FollowPlatformEntity::getId, idList)).forEach(entity -> {
            // 删除
            entity.setDeleted(CloseOrOpenEnum.OPEN.getValue());
            updateById(entity);
        });
    }

    @Override
    public void export() {
        List<FollowPlatformExcelVO> excelList = FollowPlatformConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowPlatformExcelVO.class, "平台管理", null, excelList);
    }


    @Override
    public List<FollowPlatformVO> getList() {
        FollowPlatformQuery query = new FollowPlatformQuery();
        List<FollowPlatformEntity> list = baseMapper.selectList(getWrapper(query));

        return FollowPlatformConvert.INSTANCE.convertList(list);
    }

//    @Override
//    public List<String> getBrokeName(List<Long> idList) {
//        if (idList.isEmpty()) {
//            return null;
//        }
//
//        return baseMapper.selectBatchIds(idList).stream().map(FollowPlatformEntity::getBrokerName).toList();
//    }


}