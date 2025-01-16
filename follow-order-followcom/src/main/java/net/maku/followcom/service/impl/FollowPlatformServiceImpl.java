package net.maku.followcom.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.vo.FollowTraderCountVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
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
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
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
    private final RedisCache redisCache;
    @Override
    public PageResult<FollowPlatformVO> page(FollowPlatformQuery query) {
        IPage<FollowPlatformEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowPlatformVO> followPlatformVOS = FollowPlatformConvert.INSTANCE.convertList(page.getRecords());
        return new PageResult<>(followPlatformVOS, page.getTotal());
    }

    //查询功能
    private LambdaQueryWrapper<FollowPlatformEntity> getWrapper(FollowPlatformQuery query){
        LambdaQueryWrapper<FollowPlatformEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.like(ObjectUtil.isNotEmpty(query.getServer()), FollowPlatformEntity::getServer, query.getServer());
        wrapper.eq(FollowPlatformEntity::getDeleted, CloseOrOpenEnum.CLOSE.getValue());
        if(ObjectUtil.isNotEmpty(query.getBrokerName())){
         //   wrapper.apply("LOWER(broker_name)","like" , "\'%"+query.getBrokerName().toLowerCase()+"%\'");
         wrapper.apply("LOWER(broker_name) like \'%"+query.getBrokerName().toLowerCase()+"%\'");
        }
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
                .set(FollowPlatformEntity::getLogo,vo.getLogo())
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
    public QuoteClient tologin(FollowTraderEntity trader) {
        try {
            String serverNode;
            if (ObjectUtil.isNotEmpty(redisCache.hGet(Constant.VPS_NODE_SPEED+trader.getServerId(),trader.getPlatform()))){
                serverNode=(String)redisCache.hGet(Constant.VPS_NODE_SPEED+trader.getServerId(),trader.getPlatform());
            }else {
                FollowPlatformEntity followPlatformServiceOne = this.getOne(new LambdaQueryWrapper<FollowPlatformEntity>().eq(FollowPlatformEntity::getServer, trader.getPlatform()));
                serverNode=followPlatformServiceOne.getServerNode();
            }
            if (ObjectUtil.isNotEmpty(serverNode)){
                try {
                    //处理节点格式
                    String[] split = serverNode.split(":");
                    QuoteClient quoteClient = new QuoteClient(Integer.parseInt(trader.getAccount()), trader.getPassword(),  split[0], Integer.valueOf(split[1]));
                    quoteClient.Connect();
                    return quoteClient;
                }catch (Exception e1){
                    if (e1.getMessage().contains("Invalid account")){
                        log.info("账号密码错误");
                        throw new ServerException("账号密码错误");
                    }else {
                        //重连失败
                        log.info("重连失败{}",trader.getId());
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

    @Override
    @Cacheable(
            value = "followPlatCache", // 缓存名称
            key = "#id ?: 'defaultKey'",
            unless = "#result == null" // 空结果不缓存
    )
    public FollowPlatformEntity getPlatFormById(String id) {
        return this.getById(id);
    }

    @Override
    @CachePut(
            value = "followPlatCache", // 缓存名称
            key = "#id ?: 'defaultKey'",
            unless = "#result == null" // 空结果不缓存
    )
    public FollowPlatformEntity updatePlatCache(String id) {
        return this.getById(id);
    }

    @Override
    public String getbrokerName(String serverName) {
        LambdaQueryWrapper<FollowPlatformEntity> queryWrapper = Wrappers.lambdaQuery();
                queryWrapper.eq(FollowPlatformEntity::getServer, serverName)
                .orderByDesc(FollowPlatformEntity::getCreateTime) // 按创建时间降序排列
                .last("LIMIT 1");

        FollowPlatformEntity entity = baseMapper.selectOne(queryWrapper);
        return entity != null ? entity.getBrokerName() : null;
    }

    @Override
    public List<FollowTraderCountVO> getBrokerNames() {
        return baseMapper.getBrokerNames();
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