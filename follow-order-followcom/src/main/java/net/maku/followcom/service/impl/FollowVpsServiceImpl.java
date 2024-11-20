package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowVpsConvert;
import net.maku.followcom.dao.FollowVpsDao;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.ClientService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowRedisTraderVO;
import net.maku.followcom.vo.FollowVpsExcelVO;
import net.maku.followcom.vo.FollowVpsInfoVO;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.RandomStringUtil;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
@Slf4j
public class FollowVpsServiceImpl extends BaseServiceImpl<FollowVpsDao, FollowVpsEntity> implements FollowVpsService {
    private final TransService transService;
    private final ClientService clientService;
    private final RedisUtil redisUtil;


    @Override
    public PageResult<FollowVpsVO> page(FollowVpsQuery query) {
        IPage<FollowVpsEntity> page = baseMapper.selectPage(getPage(query), getWrapper(query));
        List<FollowVpsVO> followVpsVOS = FollowVpsConvert.INSTANCE.convertList(page.getRecords());
        followVpsVOS.stream().forEach(o -> {
            Date startDate = DateUtil.offsetDay(Date.from(o.getExpiryDate().atZone(ZoneId.systemDefault()).toInstant()), 0);
            Date endDate = DateUtil.date();
            long daysBetween = DateUtil.between(startDate, endDate, DateUnit.DAY);
            if (endDate.after(startDate)) {
                o.setRemainingDay(-1); // 已过期
            } else {
                o.setRemainingDay((int) daysBetween);
            }
        });
        return new PageResult<>(followVpsVOS, page.getTotal());
    }

    private LambdaQueryWrapper<FollowVpsEntity> getWrapper(FollowVpsQuery query) {
        LambdaQueryWrapper<FollowVpsEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowVpsEntity::getDeleted, query.getDeleted());
        return wrapper;
    }


    @Override
    public FollowVpsVO get(Long id) {
        FollowVpsEntity entity = baseMapper.selectById(id);
        FollowVpsVO vo = FollowVpsConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean save(FollowVpsVO vo) {
        vo.setClientId(RandomStringUtil.generateUUIDClientId());
        FollowVpsEntity entity = FollowVpsConvert.INSTANCE.convert(vo);
        List<FollowVpsEntity> list = this.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress, vo.getIpAddress()).or().eq(FollowVpsEntity::getName, vo.getName()));
        if (ObjectUtil.isNotEmpty(list)) {
            throw new ServerException("重复名称或ip地址,请重新输入");
        }
        baseMapper.insert(entity);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowVpsVO vo) {
        FollowVpsEntity entity = FollowVpsConvert.INSTANCE.convert(vo);
        FollowVpsVO followVpsVO = this.get(Long.valueOf(vo.getId()));
        if (ObjectUtil.notEqual(vo.getName(), followVpsVO.getName())) {
            List<FollowVpsEntity> list = this.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName, vo.getName()));
            if (ObjectUtil.isNotEmpty(list)) {
                throw new ServerException("重复名称,请重新输入");
            }
        }
//        baseMapper.update(entity, new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getId, entity.getId()));
        UpdateWrapper<FollowVpsEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", entity.getId());
        updateWrapper.set("remark", entity.getRemark());
        if (ObjectUtils.isNotEmpty(entity.getName()) && !entity.getName().isEmpty()) {
            updateWrapper.set("name", entity.getName());
        }
        if (ObjectUtils.isNotEmpty(entity.getIpAddress()) && !entity.getIpAddress().isEmpty()) {
            updateWrapper.set("ip_address", entity.getIpAddress());
        }
        if (ObjectUtils.isNotEmpty(entity.getExpiryDate())) {
            updateWrapper.set("expiry_date", entity.getExpiryDate());
        }
        if (ObjectUtils.isNotEmpty(entity.getIsOpen())) {
            updateWrapper.set("is_open", entity.getIsOpen());
        }
        if (ObjectUtils.isNotEmpty(entity.getIsActive())) {
            updateWrapper.set("is_active", entity.getIsActive());
        }
        baseMapper.update(entity, updateWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Integer> idList) {
        list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getId, idList)).forEach(entity -> {
            // 删除
            entity.setDeleted(CloseOrOpenEnum.OPEN.getValue());
            updateById(entity);
        });
    }


    @Override
    public void export() {
        List<FollowVpsExcelVO> excelList = FollowVpsConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowVpsExcelVO.class, "vps列表", null, excelList);
    }

    @Override
    public List<FollowVpsVO> listByVps() {
        LambdaQueryWrapper<FollowVpsEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowVpsEntity::getName)
                .groupBy(FollowVpsEntity::getName);
        List<FollowVpsEntity> list = baseMapper.selectList(wrapper);
        return FollowVpsConvert.INSTANCE.convertList(list);
    }

    @Override
    public List<FollowVpsEntity> listByVpsName(List<String> vps) {
        return list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getName, vps).orderByDesc(FollowVpsEntity::getCreateTime));
    }

    @Override
    public void transferVps(Integer oldId, HttpServletRequest req) {
        FollowVpsVO followVpsEntityOld = this.get(Long.valueOf(oldId));
        //发送请求到旧VPS，清除缓存
        String url = MessageFormat.format("http://{0}:{1}{2}", followVpsEntityOld.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_TRANSFERVPS);
        JSONObject body = RestUtil.request(url, HttpMethod.GET, RestUtil.getHeaderApplicationJsonAndToken(req), null, null, JSONObject.class).getBody();
        log.info("旧VPS清理缓存请求:" + body);
    }

    @Override
    public void startNewVps(Integer newId, HttpServletRequest req) {
        FollowVpsVO followVpsEntityOld = this.get(Long.valueOf(newId));
        //发送请求到新VPS，启动账号
        String url = MessageFormat.format("http://{0}:{1}{2}", followVpsEntityOld.getIpAddress(), FollowConstant.VPS_PORT, FollowConstant.VPS_STARTNEWVPS);
        JSONObject body = RestUtil.request(url, HttpMethod.GET, RestUtil.getHeaderApplicationJsonAndToken(req), null, null, JSONObject.class).getBody();
        log.info("新VPS启动账号请求:" + body);
    }

    @Override
    public FollowVpsEntity select(String vpsName) {
        return baseMapper.selectOne(Wrappers.<FollowVpsEntity>lambdaQuery().eq(FollowVpsEntity::getName, vpsName));
    }

    @Override
    public FollowVpsInfoVO getFollowVpsInfo(FollowTraderService followTraderService) {
        List<FollowVpsEntity> list = this.list();
        Integer openNum = (int) list.stream().filter(o -> o.getIsOpen().equals(CloseOrOpenEnum.OPEN.getValue())).count();
        Integer runningNum = (int) list.stream().filter(o -> o.getIsActive().equals(CloseOrOpenEnum.OPEN.getValue())).count();
        Integer closeNum = (int) list.stream().filter(o -> o.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        Integer errorNum = (int) list.stream().filter(o -> o.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        //账号信息
        List<FollowTraderEntity> followTraderEntityList = followTraderService.list();
        Integer masterSuccess = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) && o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        Integer masterTotal = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        Integer slaveSuccess = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType()) && o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        Integer slaveTotal = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).count();
        Integer orderCountTotal = 0;
        BigDecimal orderLotsTotal = BigDecimal.ZERO;
        BigDecimal orderEquityTotal = BigDecimal.ZERO;
        BigDecimal orderProfitTotal = BigDecimal.ZERO;
        //过滤出为跟单的
        List<FollowTraderEntity> slaveTraderEntityList = followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).collect(Collectors.toList());
        //followTraderEntityList 替换出slaveTraderEntityList
        for (FollowTraderEntity followTraderEntity : slaveTraderEntityList) {
            if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.TRADER_USER + followTraderEntity.getId()))) {
                FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisUtil.get(Constant.TRADER_USER + followTraderEntity.getId());
                //总持仓
                orderCountTotal += followRedisTraderVO.getTotal();
                //总持仓手数
                orderLotsTotal = orderLotsTotal.add(BigDecimal.valueOf(followRedisTraderVO.getSellNum())).add(BigDecimal.valueOf(followRedisTraderVO.getBuyNum()));
                //总净值
                orderEquityTotal = orderEquityTotal.add(followRedisTraderVO.getEuqit());
                //总盈亏
                orderProfitTotal = orderProfitTotal.add(followRedisTraderVO.getProfit());

            }
        }
        Integer total = list.size();
        FollowVpsInfoVO followVpsInfoVO = new FollowVpsInfoVO();
        followVpsInfoVO.setTotal(total);
        followVpsInfoVO.setOpenNum(openNum);
        followVpsInfoVO.setRunningNum(runningNum);
        followVpsInfoVO.setCloseNum(closeNum);
        followVpsInfoVO.setErrorNum(errorNum);
        followVpsInfoVO.setMasterSuccess(masterSuccess);
        followVpsInfoVO.setMasterTotal(masterTotal);
        followVpsInfoVO.setSlaveSuccess(slaveSuccess);
        followVpsInfoVO.setSlaveTotal(slaveTotal);
        //总持仓
        followVpsInfoVO.setOrderCountTotal(orderCountTotal);
        //总持仓手数
        followVpsInfoVO.setOrderLotsTotal(orderLotsTotal);
        //总净值
        followVpsInfoVO.setOrderEquityTotal(orderEquityTotal);
        //总盈亏
        followVpsInfoVO.setOrderProfitTotal(orderProfitTotal);
        return followVpsInfoVO;
    }
}