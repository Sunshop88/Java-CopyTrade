package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import net.maku.followcom.convert.FollowPlatformConvert;
import net.maku.followcom.dao.FollowPlatformDao;
import net.maku.followcom.entity.FollowPlatformEntity;
import net.maku.followcom.query.FollowPlatformQuery;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.vo.FollowPlatformExcelVO;
import net.maku.followcom.vo.FollowPlatformVO;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台管理
 *
 * @author 阿沐 babamu@126.com
 * @since 1.0.0 2024-09-11
 */
@Service
@AllArgsConstructor
@Slf4j
public class FollowPlatformServiceImpl extends BaseServiceImpl<FollowPlatformDao, FollowPlatformEntity> implements FollowPlatformService {
    private final TransService transService;
    private final FollowBrokeServerService followBrokeServerService;

    @Override
    public PageResult<FollowPlatformVO> page(FollowPlatformQuery query) {
        IPage<FollowPlatformEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowPlatformVO> followPlatformVOS = FollowPlatformConvert.INSTANCE.convertList(page.getRecords());
        return new PageResult<>(followPlatformVOS, page.getTotal());
    }

    //查询功能
    private LambdaQueryWrapper<FollowPlatformEntity> getWrapper(FollowPlatformQuery query){
        LambdaQueryWrapper<FollowPlatformEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowPlatformEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue());
        wrapper.orderByDesc(FollowPlatformEntity::getBrokerName);
        wrapper.orderByDesc(FollowPlatformEntity::getCreateTime);
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowPlatformVO vo) {
        //查询输入的服务器是否存在
        if(ObjectUtil.isEmpty(baseMapper.selectOne(Wrappers.<FollowPlatformEntity>lambdaQuery()
                .eq(FollowPlatformEntity::getServer,vo.getServer())))) {
            FollowPlatformEntity entity = FollowPlatformConvert.INSTANCE.convert(vo);
            entity.setCreateTime(LocalDateTime.now());
            baseMapper.insert(entity);
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowPlatformVO vo) {
        //查询服务器是否存在如果不存在则返回服务器不存在
        FollowPlatformEntity followPlatformEntity = baseMapper.selectOne(Wrappers.<FollowPlatformEntity>lambdaQuery()
                .eq(FollowPlatformEntity::getId, vo.getId()));
        baseMapper.update((Wrappers.<FollowPlatformEntity>lambdaUpdate().set(FollowPlatformEntity::getBrokerName, vo.getBrokerName())
                .set(FollowPlatformEntity::getRemark, vo.getRemark())
                .set(FollowPlatformEntity::getUpdateTime,LocalDateTime.now())
                .set(FollowPlatformEntity::getUpdater,SecurityUser.getUserId())
                .set(FollowPlatformEntity::getPlatformType, vo.getPlatformType())
                .eq(FollowPlatformEntity::getBrokerName, followPlatformEntity.getBrokerName())));


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);
//        list(new LambdaQueryWrapper<FollowPlatformEntity>().in(FollowPlatformEntity::getId, idList)).forEach(entity -> {
//            // 删除
//            entity.setDeleted(CloseOrOpenEnum.OPEN.getValue());
//            updateById(entity);
//        });
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
        List<FollowPlatformEntity> list = baseMapper.selectList(getWrapper(query)).stream().distinct().toList();

        return FollowPlatformConvert.INSTANCE.convertList(list);
    }

    @Override
    public QuoteClient tologin(String account, String password, String platform) {
        try {
            //优先查看平台默认节点
            FollowPlatformEntity followPlatformServiceOne = this.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, platform));
            if (ObjectUtil.isNotEmpty(followPlatformServiceOne.getServerNode())){
                //处理节点格式
                String[] split = followPlatformServiceOne.getServerNode().split(":");
                QuoteClient quoteClient = new QuoteClient(Integer.parseInt(account), password,  split[0], Integer.valueOf(split[1]));
                try {
                    quoteClient.Connect();
                }catch (Exception e1){
                    if (e1.getMessage().contains("Invalid account")){
                        log.info("账号密码错误");
                        throw new ServerException("账号密码错误");
                    }else {
                        List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(platform);
                        for (FollowBrokeServerEntity address : serverEntityList) {
                             quoteClient = new QuoteClient(Integer.parseInt(account), password, address.getServerNode(), Integer.valueOf(address.getServerPort()));
                            try {
                                quoteClient.Connect();
                            }catch (Exception e){
                                if (e.getMessage().contains("Invalid account")){
                                    log.info("账号密码错误");
                                    throw new ServerException("账号密码错误");
                                }else{
                                    log.info("重试"+e);
                                    continue;
                                }
                            }
                            if (quoteClient.Connected()) {
                                return quoteClient;
                            }
                        }
                    }
                }
            }
        }catch (Exception e){
            log.error("登录有误",e);
        }
        return null;
    }

    @Override
    public List<FollowPlatformVO> listBroke() {
        List<FollowPlatformEntity> list = list(new LambdaQueryWrapper<FollowPlatformEntity>().select(FollowPlatformEntity::getBrokerName).groupBy(FollowPlatformEntity::getBrokerName));
        return FollowPlatformConvert.INSTANCE.convertList(list);
    }

    @Override
    public String listByServerName(String serverName) {
        //根据服务器名称查询出第一个平台名称
        return baseMapper.selectOne(Wrappers.<FollowPlatformEntity>lambdaQuery().eq(FollowPlatformEntity::getServer,serverName)).getBrokerName();
    }

    @Override
    public List<FollowPlatformVO> listHavingServer(String name) {
        List<FollowPlatformEntity> serverList = list(new LambdaQueryWrapper<FollowPlatformEntity>()
                .eq(FollowPlatformEntity::getBrokerName,name));
        return FollowPlatformConvert.INSTANCE.convertList(serverList);
    }

    @Override
    public List<FollowPlatformVO> listByServer() {
        // 查询所有服务器名称
        LambdaQueryWrapper<FollowPlatformEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowPlatformEntity::getServer)
                .groupBy(FollowPlatformEntity::getServer);
        List<FollowPlatformEntity> list = baseMapper.selectList(wrapper);
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