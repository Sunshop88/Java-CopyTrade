package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
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
import net.maku.followcom.entity.FollowTraderSubscribeEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.TraderStatusEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.enums.VpsSpendEnum;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.service.*;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.constant.Constant;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.RandomStringUtil;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
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
    private final FollowTraderSubscribeService followTraderSubscribeService;
    private final FollowVpsUserService followVpsUserService;

    @Override
    public PageResult<FollowVpsVO> page(FollowVpsQuery query) {
        //过滤vpsPage
        //除了admin都需要判断
        List<VpsUserVO> list = new ArrayList<>();
        //除了admin都需要判断
        if (!ObjectUtil.equals(Objects.requireNonNull(SecurityUser.getUserId()).toString(), "10000")) {
            //查看当前用户拥有的vps
            if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId()))) {
                list = (List<VpsUserVO>) redisUtil.get(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId());
            } else {
                List<FollowVpsUserEntity> vpsUserEntityList = followVpsUserService.list(new LambdaQueryWrapper<FollowVpsUserEntity>().eq(FollowVpsUserEntity::getUserId, SecurityUser.getUserId()));
                List<VpsUserVO> vpsUserVOS = convertoVpsUser(vpsUserEntityList);
                redisUtil.set(Constant.SYSTEM_VPS_USER + SecurityUser.getUserId(), JSONObject.toJSON(vpsUserVOS));
                list = vpsUserVOS;
            }
        }
        LambdaQueryWrapper<FollowVpsEntity> wrapper = getWrapper(query);
        List<Integer> ids =new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            FollowVpsVO followVpsVO = JSON.parseObject(JSON.toJSONString(list.get(i)), FollowVpsVO.class);
            ids.add(followVpsVO.getId() );
        }
        wrapper.in(ObjectUtil.isNotEmpty(list), FollowVpsEntity::getId,ids);
        IPage<FollowVpsEntity> page = baseMapper.selectPage(getPage(query), wrapper);
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

    private List<VpsUserVO> convertoVpsUser(List<FollowVpsUserEntity> list) {
        return list.stream().map(o -> {
            VpsUserVO vpsUserVO = new VpsUserVO();
            vpsUserVO.setId(o.getVpsId());
            vpsUserVO.setName(o.getVpsName());
            return vpsUserVO;
        }).toList();
    }

    private LambdaQueryWrapper<FollowVpsEntity> getWrapper(FollowVpsQuery query) {
        LambdaQueryWrapper<FollowVpsEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowVpsEntity::getDeleted, query.getDeleted());
        wrapper.like(ObjectUtil.isNotEmpty(query.getName()), FollowVpsEntity::getName, query.getName());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getIsActive()), FollowVpsEntity::getIsActive, query.getIsActive());
        wrapper.eq(ObjectUtil.isNotEmpty(query.getIsOpen()), FollowVpsEntity::getIsOpen, query.getIsOpen());
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
        LambdaQueryWrapper<FollowVpsEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and((x) -> {
            x.eq(FollowVpsEntity::getIpAddress, vo.getIpAddress()).or().eq(FollowVpsEntity::getName, vo.getName());
        });
        wrapper.eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE.getType());
        List<FollowVpsEntity> list = this.list(wrapper);
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
            List<FollowVpsEntity> list = this.list(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getName, vo.getName()).eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE.getType()));
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
                .eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE.getType())
                .eq(FollowVpsEntity::getIsActive, VpsSpendEnum.IN_PROGRESS.getType())
                .groupBy(FollowVpsEntity::getName);
        List<FollowVpsEntity> list = baseMapper.selectList(wrapper);
        return FollowVpsConvert.INSTANCE.convertList(list);
    }

    @Override
    public List<FollowVpsEntity> listByVpsName(List<String> vps) {
        return list(new LambdaQueryWrapper<FollowVpsEntity>().in(FollowVpsEntity::getName, vps).eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE.getType()).orderByDesc(FollowVpsEntity::getCreateTime));
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
        //过滤被删除的数据
        List<FollowVpsEntity> list = this.lambdaQuery().eq(FollowVpsEntity::getDeleted, VpsSpendEnum.FAILURE).eq(FollowVpsEntity::getIsOpen,CloseOrOpenEnum.OPEN.getValue()).list();
        Integer openNum = (int) list.stream().filter(o -> o.getIsOpen().equals(CloseOrOpenEnum.OPEN.getValue())).count();
        Integer runningNum = (int) list.stream().filter(o -> o.getIsActive().equals(CloseOrOpenEnum.OPEN.getValue())).count();
        Integer closeNum = (int) list.stream().filter(o -> o.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        Integer errorNum = (int) list.stream().filter(o -> o.getConnectionStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        //有效的vpsId
        List<Integer> vpsIds = list.stream().map(FollowVpsEntity::getId).toList();
        //账号信息
        List<FollowTraderEntity> followTraderEntityList = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().in(FollowTraderEntity::getServerId, vpsIds));
        Integer masterSuccess = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) && o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        Integer masterTotal = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        Integer slaveSuccess = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType()) && o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        Integer slaveTotal = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).count();
        Integer orderCountTotal = 0;
        BigDecimal orderLotsTotal = BigDecimal.ZERO;
        BigDecimal orderEquityTotal = BigDecimal.ZERO;
        BigDecimal orderProfitTotal = BigDecimal.ZERO;
        //过滤出为跟单的
        List<FollowTraderEntity> slaveTraderEntityList = followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())  ).collect(Collectors.toList());
        //followTraderEntityList 替换出slaveTraderEntityList
        for (FollowTraderEntity followTraderEntity : slaveTraderEntityList) {
            if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.TRADER_USER + followTraderEntity.getId())) && followTraderEntity.getStatus()==TraderStatusEnum.NORMAL.getValue()) {
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

    @Override
    public List<List<BigDecimal>> getStatByVpsId(Integer vpsId, Long traderId, FollowTraderService followTraderService) {
        if (vpsId == null) {
            throw new ServerException("vpsId不能为空");
        }
        if (traderId == null) {
            throw new ServerException("策略部不能为空");
        }
        List<List<BigDecimal>> statList = new ArrayList<>();
        List<BigDecimal> ls1 = initList();
        List<BigDecimal> ls2 = initList();
        List<BigDecimal> ls3 = initList();
        List<FollowTraderSubscribeEntity> list = followTraderSubscribeService.list(new LambdaQueryWrapper<FollowTraderSubscribeEntity>().eq(FollowTraderSubscribeEntity::getMasterId, traderId).eq(FollowTraderSubscribeEntity::getFollowStatus,CloseOrOpenEnum.OPEN.getValue()));
        List<Long> slaveIds = list.stream().map(FollowTraderSubscribeEntity::getSlaveId).toList();
        Map<Long, Long> subscribeMap = list.stream().collect(Collectors.toMap(FollowTraderSubscribeEntity::getSlaveId, FollowTraderSubscribeEntity::getMasterId));
        statList.add(ls1);
        statList.add(ls2);
        statList.add(ls3);
        List<FollowTraderEntity> followTraderEntityList = followTraderService.list(new LambdaQueryWrapper<FollowTraderEntity>().eq(FollowTraderEntity::getServerId, vpsId));
        //策略总数量
        Integer masterTotal = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType())).count();
        //处理精度丢失
        ls1.set(0, new BigDecimal(masterTotal.toString()));
        //策略正常数量
        Integer masterSuccess = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) && o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())).count();
        //处理精度丢失
        ls2.set(0, new BigDecimal(masterSuccess.toString()));
        //跟单总数量
        Integer slaveTotal = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType())).count();
        ls1.set(1, new BigDecimal(slaveTotal.toString()));
        //跟单正常数量
        Integer slaveSuccess = (int) followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.SLAVE_REAL.getType()) && o.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue()) && o.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue())).count();
        ls2.set(1, BigDecimal.valueOf(slaveSuccess));
        ls3.set(1, BigDecimal.valueOf(slaveIds.size()));
        Map<Long, FollowTraderEntity> map = followTraderEntityList.stream().filter(o -> o.getType().equals(TraderTypeEnum.MASTER_REAL.getType()) && o.getFollowStatus().equals(CloseOrOpenEnum.OPEN.getValue()))
                .collect(Collectors.toMap(FollowTraderEntity::getId, Function.identity()));

        for (FollowTraderEntity followTraderEntity : followTraderEntityList) {
            //跟单正常连接的，跟单
            if(followTraderEntity.getType().equals(TraderTypeEnum.SLAVE_REAL.getType()) && followTraderEntity.getStatus()== TraderStatusEnum.NORMAL.getValue() && followTraderEntity.getFollowStatus()==CloseOrOpenEnum.OPEN.getValue()) {
                //喊单者跟单开启跟单的
                Long masterId = subscribeMap.get(followTraderEntity.getId());
                FollowTraderEntity master = map.get(masterId);
                if(ObjectUtil.isNotEmpty(master) && master.getFollowStatus()== CloseOrOpenEnum.OPEN.getValue() ) {
                if (ObjectUtil.isNotEmpty(redisUtil.get(Constant.TRADER_USER + followTraderEntity.getId()))) {
                    FollowRedisTraderVO followRedisTraderVO = (FollowRedisTraderVO) redisUtil.get(Constant.TRADER_USER + followTraderEntity.getId());
                    //总持仓
                    ls1.set(2, ls1.get(2).add(new BigDecimal(followRedisTraderVO.getTotal().toString())));
                    //做多
                    ls1.set(4, ls1.get(4).add(new BigDecimal(followRedisTraderVO.getBuyNum() + "")));
                    //做空
                    ls1.set(5, ls1.get(5).add(new BigDecimal(followRedisTraderVO.getSellNum() + "")));
                    //总持仓手数
                    //  BigDecimal orderLotsTotal = orderLotsTotal.add(BigDecimal.valueOf(followRedisTraderVO.getSellNum())).add(BigDecimal.valueOf(followRedisTraderVO.getBuyNum()));
                    ls1.set(3, ls1.get(4).add(ls1.get(5)));
                    //总净值
                    ls1.set(6, ls1.get(6).add(followRedisTraderVO.getEuqit()));
                    //总倍数
                    ls1.set(7, ls1.get(7).add(new BigDecimal(followTraderEntity.getLeverage().toString())));
                    //总盈亏
                    ls1.set(8, ls1.get(8).add(followRedisTraderVO.getProfit() == null ? BigDecimal.ZERO : followRedisTraderVO.getProfit()));
                    //开启中
                    if (followTraderEntity.getStatus().equals(CloseOrOpenEnum.CLOSE.getValue())) {
                        //总持仓
                        ls2.set(2, ls2.get(2).add(new BigDecimal(followRedisTraderVO.getTotal().toString())));
                        //总持仓手数
                        //  BigDecimal orderLotsTotal = orderLotsTotal.add(BigDecimal.valueOf(followRedisTraderVO.getSellNum())).add(BigDecimal.valueOf(followRedisTraderVO.getBuyNum()));
                        //    ls2.set(3, (ls2.get(3) == null) ? BigDecimal.valueOf(followRedisTraderVO.getTotal()) : ls2.get(3).add(BigDecimal.valueOf(followRedisTraderVO.getTotal())));
                        //做多
                        ls2.set(4, ls2.get(4).add(new BigDecimal(followRedisTraderVO.getBuyNum() + "")));
                        //做空
                        ls2.set(5, ls2.get(5).add(new BigDecimal(followRedisTraderVO.getSellNum() + "")));

                        ls2.set(3, ls2.get(4).add(ls2.get(5)));
                        //总净值
                        ls2.set(6, ls2.get(6).add(followRedisTraderVO.getEuqit()));
                        //总倍数
                        ls2.set(7, ls2.get(7).add(new BigDecimal(followTraderEntity.getLeverage().toString())));
                        //总盈亏
                        ls2.set(8, ls2.get(8).add(followRedisTraderVO.getProfit() == null ? BigDecimal.ZERO : followRedisTraderVO.getProfit()));
                    }
                    //当前策略
                    if (slaveIds.contains(followTraderEntity.getId())) {
                        //总持仓
                        ls3.set(2, ls3.get(2).add(new BigDecimal(followRedisTraderVO.getTotal().toString())));
                        //总持仓手数
                        //  BigDecimal orderLotsTotal = orderLotsTotal.add(BigDecimal.valueOf(followRedisTraderVO.getSellNum())).add(BigDecimal.valueOf(followRedisTraderVO.getBuyNum()));
                        //    ls2.set(3, (ls2.get(3) == null) ? BigDecimal.valueOf(followRedisTraderVO.getTotal()) : ls2.get(3).add(BigDecimal.valueOf(followRedisTraderVO.getTotal())));
                        //做多
                        ls3.set(4, ls3.get(4).add(new BigDecimal(followRedisTraderVO.getBuyNum() + "")));
                        //做空
                        ls3.set(5, ls3.get(5).add(new BigDecimal(followRedisTraderVO.getSellNum() + "")));
                        //总持仓手数
                        ls3.set(3, ls3.get(4).add(ls3.get(5)));
                        //总净值
                        ls3.set(6, ls3.get(6).add(followRedisTraderVO.getEuqit()));
                        //总倍数
                        ls3.set(7, ls3.get(7).add(new BigDecimal(followTraderEntity.getLeverage().toString())));
                        //总盈亏
                        ls3.set(8, ls3.get(8).add(followRedisTraderVO.getProfit() == null ? BigDecimal.ZERO : followRedisTraderVO.getProfit()));
                    }
                }
                }
            }
        }
        //处理小数位
        handleScale(statList);
        return statList;
    }

    @Override
    public void updateStatus(FollowVpsEntity vpsEntity) {
        UpdateWrapper<FollowVpsEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", vpsEntity.getId());
        updateWrapper.set("connection_status", vpsEntity.getConnectionStatus());
        baseMapper.update(updateWrapper);
    }

    @Override
    public FollowVpsEntity getVps(String ip) {
        return baseMapper.selectOne(new LambdaQueryWrapper<FollowVpsEntity>().eq(FollowVpsEntity::getIpAddress, ip));
    }

    /**
     * 初始化iniList
     */
    private List<BigDecimal> initList() {
        return Arrays.asList(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 处理小数
     */
    private void handleScale(List<List<BigDecimal>> statList) {
        List<Integer> excludeIndex = Arrays.asList(0, 1, 2, 4);
        statList.forEach(x -> {
            for (int i = 0; i < x.size(); i++) {
                if (!excludeIndex.contains(i)) {
                    x.set(i, x.get(i).setScale(2, BigDecimal.ROUND_HALF_UP));
                }

            }
        });

    }

}