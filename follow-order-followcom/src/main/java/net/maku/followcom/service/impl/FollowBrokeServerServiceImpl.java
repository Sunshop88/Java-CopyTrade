package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowBrokeServerConvert;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.query.FollowBrokeServerQuery;
import net.maku.followcom.vo.FollowBrokeServerVO;
import net.maku.followcom.dao.FollowBrokeServerDao;
import net.maku.followcom.service.FollowBrokeServerService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowBrokeServerExcelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 导入服务器列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowBrokeServerServiceImpl extends BaseServiceImpl<FollowBrokeServerDao, FollowBrokeServerEntity> implements FollowBrokeServerService {
    private final TransService transService;
    @Override
    public PageResult<FollowBrokeServerVO> page(FollowBrokeServerQuery query) {
        IPage<FollowBrokeServerEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowBrokeServerConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowBrokeServerEntity> getWrapper(FollowBrokeServerQuery query){
        LambdaQueryWrapper<FollowBrokeServerEntity> wrapper = Wrappers.lambdaQuery();

        return wrapper;
    }


    @Override
    public FollowBrokeServerVO get(Long id) {
        FollowBrokeServerEntity entity = baseMapper.selectById(id);
        FollowBrokeServerVO vo = FollowBrokeServerConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowBrokeServerVO vo) {
        FollowBrokeServerEntity entity = FollowBrokeServerConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowBrokeServerVO vo) {
        FollowBrokeServerEntity entity = FollowBrokeServerConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
    List<FollowBrokeServerExcelVO> excelList = FollowBrokeServerConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowBrokeServerExcelVO.class, "导入服务器列表", null, excelList);
    }

    @Override
    public void saveList(List<FollowBrokeServerVO> list) {
        List<FollowBrokeServerEntity> entity = FollowBrokeServerConvert.INSTANCE.convertList2(list);
        baseMapper.insert(entity);
    }

    @Override
    public List<FollowBrokeServerEntity> listByServerName(String name) {
        return list(new LambdaQueryWrapper<FollowBrokeServerEntity>().eq(FollowBrokeServerEntity::getServerName,name).orderByAsc(FollowBrokeServerEntity::getSpeed));
    }

    @Override
    public List<FollowBrokeServerEntity> listByServerName(List<String> name) {
        if (name == null || name.isEmpty()) {
            // 如果 name 列表为空，直接返回空列表
            return Collections.emptyList();
        } else {
            return list(new LambdaQueryWrapper<FollowBrokeServerEntity>()
                    .in(FollowBrokeServerEntity::getServerName, name)
                    .orderByAsc(FollowBrokeServerEntity::getCreateTime));
        }
    }

    @Override
    public List<FollowBrokeServerEntity> listByServerNameGroup(String name) {
        if (ObjectUtil.isNotEmpty(name)){
            return list(new LambdaQueryWrapper<FollowBrokeServerEntity>().select(FollowBrokeServerEntity::getServerName).like(FollowBrokeServerEntity::getServerName,name).groupBy(FollowBrokeServerEntity::getServerName));
        }else {
            return list(new LambdaQueryWrapper<FollowBrokeServerEntity>().select(FollowBrokeServerEntity::getServerName).groupBy(FollowBrokeServerEntity::getServerName));
        }
    }

    @Override
    public FollowBrokeServerEntity getByName(String server) {
        return list(new LambdaQueryWrapper<FollowBrokeServerEntity>()
                .eq(FollowBrokeServerEntity::getServerName, server)
                .orderByDesc(FollowBrokeServerEntity::getCreateTime)) // 按照创建时间降序排列
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public FollowBrokeServerEntity existsByServerNodeAndServerPort(String serverName, String serverNode, String serverPort) {
        return baseMapper.selectOne(new LambdaQueryWrapper<FollowBrokeServerEntity>()
                .eq(FollowBrokeServerEntity::getServerName, serverName)
                .eq(FollowBrokeServerEntity::getServerNode, serverNode)
                .eq(FollowBrokeServerEntity::getServerPort, serverPort)
                .orderByDesc(FollowBrokeServerEntity::getCreateTime)
                .last("LIMIT 1")); // 确保只返回一条记录
    }

    @Override
    public List<FollowBrokeServerVO> listByServer() {
        // 查询所有服务器名称
        LambdaQueryWrapper<FollowBrokeServerEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowBrokeServerEntity::getServerName)
                .groupBy(FollowBrokeServerEntity::getServerName);
        List<FollowBrokeServerEntity> list = baseMapper.selectList(wrapper);
        return FollowBrokeServerConvert.INSTANCE.convertList(list);
    }

}