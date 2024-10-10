package net.maku.mascontrol.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.api.module.system.UserApi;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import net.maku.mascontrol.convert.FollowPlatformConvert;
import net.maku.mascontrol.dao.FollowPlatformDao;
import net.maku.mascontrol.entity.FollowPlatformEntity;
import net.maku.mascontrol.query.FollowPlatformQuery;
import net.maku.mascontrol.service.FollowPlatformService;
import net.maku.mascontrol.vo.FollowPlatformExcelVO;
import net.maku.mascontrol.vo.FollowPlatformVO;
//import net.maku.system.service.SysUserService;
import online.mtapi.mt4.QuoteClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

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
    private final UserApi userApi;

    @Override
    public PageResult<FollowPlatformVO> page(FollowPlatformQuery query) {
        IPage<FollowPlatformEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowPlatformVO> followPlatformVOS = FollowPlatformConvert.INSTANCE.convertList(page.getRecords());
        followPlatformVOS.forEach(o->{
            o.setCreator(userApi.getUserById(o.getCreator()).getUsername());
        });

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
            entity.setCreator(SecurityUser.getUserId());
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
        List<FollowPlatformEntity> list = baseMapper.selectList(getWrapper(query)).stream().collect(Collectors.toMap(
                FollowPlatformEntity::getServer, // 以 server 字段为 key
                entity -> entity,                // 以 entity 自身为 value
                (existing, replacement) -> existing // 如果遇到重复 key，则保留原有值
        )).values().stream().collect(Collectors.toList());

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
                }catch (Exception e){
                    if (e.getMessage().contains("Invalid account")){
                        log.info("账号密码错误");
                        throw new ServerException("账号密码错误");
                    }
                }
            }else {
                List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(platform);
                for (FollowBrokeServerEntity address : serverEntityList) {
                    QuoteClient quoteClient = new QuoteClient(Integer.parseInt(account), password, address.getServerNode(), Integer.valueOf(address.getServerPort()));
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
        }catch (Exception e){
            log.error("登录有误",e);
        }
        return null;
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