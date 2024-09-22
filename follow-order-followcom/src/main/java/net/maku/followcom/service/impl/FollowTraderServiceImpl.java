package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowBrokeServerEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.query.FollowOrderSpliListQuery;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.vo.FollowOrderSendVO;
import net.maku.followcom.vo.FollowOrderSlipPointVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.followcom.convert.FollowTraderConvert;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.FollowTraderQuery;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.dao.FollowTraderDao;
import net.maku.followcom.service.FollowTraderService;
import com.fhs.trans.service.impl.TransService;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.followcom.vo.FollowTraderExcelVO;
import net.maku.framework.security.user.SecurityUser;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

/**
 * mt4账号
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTraderServiceImpl extends BaseServiceImpl<FollowTraderDao, FollowTraderEntity> implements FollowTraderService {
    private final TransService transService;
    private final FollowBrokeServerService followBrokeServerService;
    private final FollowVpsService followVpsService;

    @Override
    public PageResult<FollowTraderVO> page(FollowTraderQuery query) {
        IPage<FollowTraderEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));

        return new PageResult<>(FollowTraderConvert.INSTANCE.convertList(page.getRecords()), page.getTotal());
    }


    private LambdaQueryWrapper<FollowTraderEntity> getWrapper(FollowTraderQuery query){
        LambdaQueryWrapper<FollowTraderEntity> wrapper = Wrappers.lambdaQuery();
        //查询指定VPS下的账号
        wrapper.eq(FollowTraderEntity::getDeleted,query.getDeleted());
        return wrapper;
    }


    @Override
    public FollowTraderVO get(Long id) {
        FollowTraderEntity entity = baseMapper.selectById(id);
        FollowTraderVO vo = FollowTraderConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTraderVO vo) {
        if (ObjectUtil.isEmpty(vo.getPlatform())){
            throw new ServerException("服务商错误");
        }
        List<FollowBrokeServerEntity> serverEntityList = followBrokeServerService.listByServerName(vo.getPlatform());
        if (ObjectUtil.isEmpty(serverEntityList)){
            throw new ServerException("暂无可用服务器商");
        }
        //查看是否已存在该账号
        FollowTraderEntity followTraderEntity = baseMapper.selectOne(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getAccount, vo.getAccount()).eq(FollowTraderEntity::getPlatform, vo.getPlatform()));
        if (ObjectUtil.isNotEmpty(followTraderEntity)){
            throw new ServerException("该账号已存在");
        }
        //进行登录
        try {
            for (FollowBrokeServerEntity address : serverEntityList) {
                QuoteClient quoteClient = new QuoteClient(Integer.parseInt(vo.getAccount()), vo.getPassword(), address.getServerNode(),Integer.valueOf(address.getServerPort()));
                if (quoteClient.Connected()){
                    //结束循环并保存记录
                    //策略信息入库
                    FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);
                    entity.setBalance(BigDecimal.valueOf(quoteClient.AccountBalance()));
                    entity.setEuqit(BigDecimal.valueOf(quoteClient.AccountEquity()));
                    //todo 默认第一个VPS
                    FollowVpsEntity followVpsEntity = followVpsService.list().get(0);
                    if (ObjectUtil.isEmpty(followVpsEntity)){
                        throw new ServerException("请先添加VPS");
                    }
                    entity.setIpAddr(followVpsEntity.getIpAddress());
                    entity.setDiff(quoteClient.ServerTimeZone()/60);
                    entity.setServerName(followVpsEntity.getName());
                    entity.setServerId(followVpsEntity.getId());
                    entity.setFreeMargin(BigDecimal.valueOf(quoteClient.AccountFreeMargin()));
                    //预付款比例:账户的净值÷已用预付款
                    entity.setMarginProportion(BigDecimal.valueOf(quoteClient.AccountMargin()).divide(BigDecimal.valueOf(quoteClient.AccountEquity()),2, RoundingMode.HALF_UP));
                    entity.setLeverage(quoteClient.AccountLeverage());
                    entity.setIsDemo(quoteClient._IsDemo);
                    entity.setCreator(SecurityUser.getUserId());
                    entity.setCreateTime(DateUtil.toLocalDateTime(new Date()));
                    int insert = baseMapper.insert(entity);
                    if (insert>0){
                        //增加品种规格记录

                    }
                    break;
                }
            }
        }catch (Exception e){
            log.error("登录异常");
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTraderVO vo) {
        FollowTraderEntity entity = FollowTraderConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getId, idList)).forEach(entity -> {
            // 删除
            entity.setDeleted(CloseOrOpenEnum.OPEN.getValue());
            updateById(entity);
        });

    }


    @Override
    public void export() {
    List<FollowTraderExcelVO> excelList = FollowTraderConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTraderExcelVO.class, "mt4账号", null, excelList);
    }

    @Override
    public boolean orderSend(FollowOrderSendVO vo) {
        return false;
    }

    @Override
    public void orderSlipPoint() {

    }

    @Override
    public PageResult<FollowOrderSlipPointVO> pageSlipPoint(FollowOrderSpliListQuery query) {
        return null;
    }

}