package net.maku.followcom.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.entity.FollowOrderDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.enums.CloseOrOpenEnum;
import net.maku.followcom.enums.MessagesTypeEnum;
import net.maku.followcom.enums.TraderRepairOrderEnum;
import net.maku.followcom.pojo.EaOrderInfo;
import net.maku.followcom.service.FollowOrderDetailService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.service.MessagesService;
import net.maku.followcom.vo.FixTemplateVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.followcom.vo.OrderActiveInfoVO;
import net.maku.followcom.vo.OrderRepairInfoVO;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.cache.RedisUtil;
import net.maku.framework.common.cache.RedissonLockUtil;
import net.maku.framework.common.constant.Constant;

import net.maku.framework.common.utils.ThreadPoolUtils;
import online.mtapi.mt4.Op;
import online.mtapi.mt4.QuoteClient;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Author:  zsd
 * Date:  2025/1/23/周四 9:31
 */
@Service
@Slf4j
@Component
@AllArgsConstructor
public class MessagesServiceImpl implements MessagesService {
    private final RedisCache redisCache;
    private final FollowOrderDetailService followOrderDetailService;
    private final RedissonLockUtil redissonLockUtil;
    private final RedisUtil redisUtil;
    private final FollowVpsService followVpsService;


    private  String template(String secret, Integer timestamp,String vpsName,String sourceRemarks,String source,String follow,String symbol,String type) {
        String json="{\n" +
                "    \"timestamp\": \""+timestamp+"\",\n" +
                "    \"sign\": \""+secret+"\",\n" +
                "    \"msg_type\": \"post\",\n" +
                "    \"content\": {\n" +
                "        \"post\": {\n" +
                "            \"zh_cn\": {\n" +
                "                \"title\": \"实时漏单通知\",\n" +
                "                \"content\": [\n" +
                "                    [{\n" +
                "                        \"tag\": \"text\",\n" +
                "                        \"text\": \" 通知时间：【"+ DateUtil.format(new Date(),"yyyy-MM-dd HH:mm:ss")+"】\n" +
                " VPS名称：【"+vpsName+"】\n" +
                " 策略账号：【"+sourceRemarks+"】 【"+source+"】  \n" +
                " 跟单账号：【"+follow+"】 \n" +
                " 漏单信息：【"+symbol+"】,【"+type+"】 \"\n " +
                "                    }]\n" +
                "                ]\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
        return json;
    }

    private  String  fixedTimeTemplate(String secret, Integer timestamp,Integer num) {
        String json="{\n" +
                "    \"timestamp\": \""+timestamp+"\",\n" +
                "    \"sign\": \""+secret+"\",\n" +
                "    \"msg_type\": \"post\",\n" +
                "    \"content\": {\n" +
                "        \"post\": {\n" +
                "            \"zh_cn\": {\n" +
                "                \"title\": \"定时任务漏单检查\",\n" +
                "                \"content\": [\n" +
                "                    [{\n" +
                "                        \"tag\": \"text\",\n" +
                "                        \"text\": \" 通知时间：【"+DateUtil.format(new Date(),"yyyy-MM-dd HH:mm:ss")+"】\n" +
                " 目前Live存在总漏单数量：【"+num+"】\"\n " +
                "                    }]\n" +
                "                ]\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";
        return json;
    }
    private  String GenSign(String secret, int timestamp) throws NoSuchAlgorithmException, InvalidKeyException {
        //把timestamp+"\n"+密钥当做签名字符串
        String stringToSign = timestamp + "\n" + secret;
        //使用HmacSHA256算法计算签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(new byte[]{});
        return new String(Base64.encodeBase64(signData));
    }
    public void isRepairClose(EaOrderInfo orderInfo, FollowTraderEntity follow,FollowTraderEntity master){
       ThreadPoolUtils.getExecutor().execute(() -> {
          /*  try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

            }*/
            //写入到redis中
            String key = Constant.REPAIR_CLOSE + "：" + follow.getAccount();
            boolean lock = redissonLockUtil.lock(key, 500, -1, TimeUnit.SECONDS);
            try {
            if(lock) {
                Object repairStr = redisCache.hGetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount());
                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap<Integer, OrderRepairInfoVO>();
                if (repairStr != null && repairStr.toString().trim().length() > 0) {
                    repairInfoVOS = JSONObject.parseObject(repairStr.toString(), Map.class);
                }
                Object o2 = redisUtil.get(Constant.TRADER_ACTIVE + follow.getId());
                List<OrderActiveInfoVO> followActiveInfoList = new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o2)) {
                    followActiveInfoList = JSONObject.parseArray(o2.toString(), OrderActiveInfoVO.class);
                }
                Boolean flag = followActiveInfoList.stream().anyMatch(order -> String.valueOf(orderInfo.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));
                //通过备注查询未平仓记录
                if(flag) {
                    List<FollowOrderDetailEntity> detailServiceList = followOrderDetailService.list(new LambdaQueryWrapper<FollowOrderDetailEntity>().eq(FollowOrderDetailEntity::getTraderId, follow.getId()).eq(FollowOrderDetailEntity::getMagical, orderInfo.getTicket()));
                    if (ObjectUtil.isNotEmpty(detailServiceList)) {
                        Map<Integer, OrderRepairInfoVO> closeMap = new HashMap<Integer, OrderRepairInfoVO>();
                        detailServiceList.forEach(detail -> {
                            OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                            orderRepairInfoVO.setMasterOpenTime(orderInfo.getOpenTime());
                            orderRepairInfoVO.setMasterCloseTime(orderInfo.getCloseTime());
                            orderRepairInfoVO.setMasterSymbol(orderInfo.getSymbol());
                            orderRepairInfoVO.setMasterOpenPrice(orderInfo.getOpenPrice());
                            orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.CLOSE.getType());
                            orderRepairInfoVO.setMasterLots(orderInfo.getLots());
                            orderRepairInfoVO.setMasterProfit(orderInfo.getProfit().doubleValue());
                            orderRepairInfoVO.setMasterOpenPrice(orderInfo.getOpenPrice());
                            //orderRepairInfoVO.setMasterProfit(eaOrderInfo.getProfit());
                            orderRepairInfoVO.setMasterType(Op.forValue(orderInfo.getType()).name());
                            orderRepairInfoVO.setMasterTicket(orderInfo.getTicket());
                            orderRepairInfoVO.setSlaveLots(orderInfo.getLots());
                            orderRepairInfoVO.setSlaveType(Op.forValue(orderInfo.getType()).name());
                            orderRepairInfoVO.setSlaveOpenTime(detail.getOpenTime());
                            orderRepairInfoVO.setSlaveOpenPrice(detail.getOpenPrice().doubleValue());
                            orderRepairInfoVO.setSlaveCloseTime(detail.getCloseTime());
                            orderRepairInfoVO.setSlaveSymbol(detail.getSymbol());
                            orderRepairInfoVO.setSlaveAccount(detail.getAccount());
                            orderRepairInfoVO.setSlavePlatform(detail.getPlatform());
                            orderRepairInfoVO.setSlaveTicket(detail.getOrderNo());
                            orderRepairInfoVO.setSlaverProfit(detail.getProfit().doubleValue());
                            orderRepairInfoVO.setMasterId(orderInfo.getMasterId());
                            orderRepairInfoVO.setSlavePlatform(follow.getPlatform());
                            orderRepairInfoVO.setSlaveId(follow.getId());
                            closeMap.put(orderInfo.getTicket(), orderRepairInfoVO);
                            //发送漏单
                            ThreadPoolUtils.getExecutor().execute(() -> {
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                FollowOrderDetailEntity d = followOrderDetailService.getById(detail.getId());
                                if (ObjectUtil.isNotEmpty(d) && d.getCloseStatus() == CloseOrOpenEnum.CLOSE.getValue()) {
                                    FollowVpsEntity vps = followVpsService.getById(follow.getServerId());
                                    Boolean isSend = isSend(vps,follow,master);
                                    if(isSend) {
                                        FixTemplateVO vo = FixTemplateVO.builder().templateType(MessagesTypeEnum.MISSING_ORDERS_NOTICE.getCode()).
                                                vpsName(vps.getName())
                                                .source(master.getAccount())
                                                .sourceRemarks(master.getRemark())
                                                .follow(follow.getAccount())
                                                .symbol(orderInfo.getSymbol())
                                                .type(Constant.NOTICE_MESSAGE_SELL).build();
                                        send(vo);
                                    }
                                }
                            });
                        });
                        repairInfoVOS.putAll(closeMap);
                        redisCache.hSetStr(Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSON.toJSONString(repairInfoVOS));
                        log.info("漏平数据写入,key:{},key:{},订单号:{},val:{}", Constant.REPAIR_CLOSE + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), orderInfo.getTicket(), JSONObject.toJSONString(repairInfoVOS));

                    }
                }
            }
            } catch (Exception e) {
                log.error("漏平数据写入异常{0}",e);
            }finally {
                redissonLockUtil.unlock(key);
            }

        });
    }
    public void isRepairSend(EaOrderInfo orderInfo, FollowTraderEntity follow, FollowTraderEntity master, QuoteClient quoteClient ){
        ThreadPoolUtils.getExecutor().execute(() -> {
            boolean existsInActive =true;
            if(quoteClient!=null){
                existsInActive= Arrays.stream(quoteClient.GetOpenedOrders()).anyMatch(order -> String.valueOf(orderInfo.getTicket()).equalsIgnoreCase(order.MagicNumber+""));
            }else{

                Object o1 = redisCache.get(Constant.TRADER_ACTIVE + follow.getId());
                List<OrderActiveInfoVO> orderActiveInfoList = new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o1)) {
                    orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                }
                 existsInActive = orderActiveInfoList.stream().anyMatch(order -> String.valueOf(orderInfo.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));
            }
          /*  try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                log.error("漏单通知异常{0}",e);
            }*/
            Object o1 = redisCache.get(Constant.TRADER_ACTIVE + follow.getId());
            List<OrderActiveInfoVO> orderActiveInfoList = new ArrayList<>();
            if (ObjectUtil.isNotEmpty(o1)) {
                orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
            }
            existsInActive = orderActiveInfoList.stream().anyMatch(order -> String.valueOf(orderInfo.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));

            //确定为漏单
            if (!existsInActive) {
                log.info("漏单喊单者订单号{}",orderInfo.getTicket());
                String key = Constant.REPAIR_SEND + "：" + follow.getAccount();
                //写入到redis中
                boolean lock = redissonLockUtil.lock(key, 30, -1, TimeUnit.SECONDS);
                try {
                    if(lock) {
                        Object repairStr = redisCache.hGetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                        Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap<Integer, OrderRepairInfoVO>();
                        if (repairStr != null && repairStr.toString().trim().length() > 0) {
                            repairInfoVOS = JSONObject.parseObject(repairStr.toString(), Map.class);
                        }
                        OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                        orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                        orderRepairInfoVO.setMasterLots(orderInfo.getLots());
                        orderRepairInfoVO.setMasterOpenTime(orderInfo.getOpenTime());
                        orderRepairInfoVO.setMasterProfit(orderInfo.getProfit().doubleValue());
                        orderRepairInfoVO.setMasterSymbol(orderInfo.getSymbol());
                        orderRepairInfoVO.setMasterTicket(orderInfo.getTicket());
                        orderRepairInfoVO.setMasterOpenPrice(orderInfo.getOpenPrice());
                        orderRepairInfoVO.setMasterType(Op.forValue(orderInfo.getType()).name());
                        orderRepairInfoVO.setMasterId(orderInfo.getMasterId());
                        orderRepairInfoVO.setSlaveAccount(follow.getAccount());
                        orderRepairInfoVO.setSlaveType(Op.forValue(orderInfo.getType()).name());
                        orderRepairInfoVO.setSlavePlatform(follow.getPlatform());
                        orderRepairInfoVO.setSlaveId(follow.getId());
                        repairInfoVOS.put(orderInfo.getTicket(), orderRepairInfoVO);
                        redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSON.toJSONString(repairInfoVOS));
                      //  redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + "#" + master.getId(), follow.getAccount(), JSON.toJSONString(repairInfoVOS));
                        log.info("漏开数据写入,key:{},key:{},val:{},订单号:{}",Constant.REPAIR_SEND +master.getAccount() + ":" + master.getId(), follow.getAccount().toString(),JSONObject.toJSONString(repairInfoVOS),orderInfo.getTicket() );
                    }

                } catch (Exception e) {
                   log.error("漏单数据写入异常{0}",e);
                }finally {
                    redissonLockUtil.unlock(key);
                }
                //发送漏单消息
                try {
                    ThreadPoolUtils.getExecutor().execute(() -> {
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                        Object o = redisCache.get(Constant.TRADER_ACTIVE + follow.getId());
                        List<OrderActiveInfoVO> orderActive = new ArrayList<>();
                        if (ObjectUtil.isNotEmpty(o1)) {
                            orderActive = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                        }
                        boolean flag=true;
                        if(quoteClient!=null){
                            flag= Arrays.stream(quoteClient.GetOpenedOrders()).anyMatch(order -> String.valueOf(orderInfo.getTicket()).equalsIgnoreCase(order.MagicNumber+""));
                        }else{
                             flag = orderActive.stream().anyMatch(order -> String.valueOf(orderInfo.getTicket()).equalsIgnoreCase(order.getMagicNumber().toString()));

                        }
                       if (!flag) {
                          // +";"+orderInfo.getTicket()+";"+orderActive.size()+";"+quoteClient.GetOpenedOrders().length
                            FollowVpsEntity vps = followVpsService.getById(follow.getServerId());
                           Boolean isSend = isSend(vps,follow,master);
                           if( isSend){
                                FixTemplateVO vo = FixTemplateVO.builder().templateType(MessagesTypeEnum.MISSING_ORDERS_NOTICE.getCode()).
                                        vpsName(vps.getName())
                                        .source(master.getAccount())
                                        .sourceRemarks(master.getRemark())
                                        .follow(follow.getAccount())
                                        .symbol(orderInfo.getSymbol())
                                        .type(Constant.NOTICE_MESSAGE_BUY).build();
                                send(vo);
                            }


                        }
                    });

                } catch (Exception e) {

                }
            }
       });
    }

    public void checkRepairSend(FollowTraderEntity follow, FollowTraderEntity master, QuoteClient quoteClient){
        String openKey = Constant.REPAIR_SEND + "：" + follow.getAccount();
        boolean lock = redissonLockUtil.lock(openKey, 30, -1, TimeUnit.SECONDS);
        try {
            if(lock) {
                //如果主账号这边都平掉了,就删掉这笔订单
                Object o1 = redisUtil.get(Constant.TRADER_ACTIVE + master.getId());
                List<OrderActiveInfoVO> orderActiveInfoList = new ArrayList<>();
                if (ObjectUtil.isNotEmpty(o1)) {
                    orderActiveInfoList = JSONObject.parseArray(o1.toString(), OrderActiveInfoVO.class);
                }
                Map<Integer, OrderRepairInfoVO> repairInfoVOS = new HashMap<Integer, OrderRepairInfoVO>();
                if(orderActiveInfoList!=null && orderActiveInfoList.size()>0) {
                    orderActiveInfoList.stream().forEach(orderInfo -> {
                        AtomicBoolean existsInActive = new AtomicBoolean(true);
                        if (quoteClient != null) {
                            existsInActive.set(Arrays.stream(quoteClient.GetOpenedOrders()).anyMatch(order -> String.valueOf(orderInfo.getOrderNo()).equalsIgnoreCase(order.MagicNumber + "")));
                        } else {
                            Object o2 = redisUtil.get(Constant.TRADER_ACTIVE + follow.getId());
                            List<OrderActiveInfoVO> followActiveInfoList = new ArrayList<>();
                            if (ObjectUtil.isNotEmpty(o2)) {
                                followActiveInfoList = JSONObject.parseArray(o2.toString(), OrderActiveInfoVO.class);
                            }
                            existsInActive.set(followActiveInfoList.stream().anyMatch(order -> String.valueOf(orderInfo.getOrderNo()).equalsIgnoreCase(order.getMagicNumber().toString())));
                        }
                        if (!existsInActive.get()) {
                            OrderRepairInfoVO orderRepairInfoVO = new OrderRepairInfoVO();
                            orderRepairInfoVO.setRepairType(TraderRepairOrderEnum.SEND.getType());
                            orderRepairInfoVO.setMasterLots(orderInfo.getLots());
                            orderRepairInfoVO.setMasterOpenTime(orderInfo.getOpenTime());
                            orderRepairInfoVO.setMasterProfit(orderInfo.getProfit());
                            orderRepairInfoVO.setMasterSymbol(orderInfo.getSymbol());
                            orderRepairInfoVO.setMasterTicket(orderInfo.getOrderNo());
                            orderRepairInfoVO.setMasterOpenPrice(orderInfo.getOpenPrice());
                            orderRepairInfoVO.setMasterType(orderInfo.getType());
                            orderRepairInfoVO.setMasterId(master.getId());
                            orderRepairInfoVO.setSlaveAccount(follow.getAccount());
                            orderRepairInfoVO.setSlaveType(orderInfo.getType());
                            orderRepairInfoVO.setSlavePlatform(follow.getPlatform());
                            orderRepairInfoVO.setSlaveId(follow.getId());
                            repairInfoVOS.put(orderInfo.getOrderNo(), orderRepairInfoVO);
                        }
                        redisUtil.hSetStr(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), JSON.toJSONString(repairInfoVOS));
                        log.info("初始化漏单补偿数据写入,key:{},key:{},订单号:{},val:{},", Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString(), orderInfo.getOrderNo(), JSONObject.toJSONString(repairInfoVOS));
                    });
                }else{
                    redisUtil.hDel(Constant.REPAIR_SEND + master.getAccount() + ":" + master.getId(), follow.getAccount().toString());
                }
            }
        } catch (Exception e) {
            log.error("漏单检查写入异常"+e);
        }finally {
            redissonLockUtil.unlock(openKey);
        }
    }

    /**
     * 发送消息
     *
    * */
    @Override
    public void send(FixTemplateVO vo) {
        Object secretRedis = redisCache.hGet(Constant.SYSTEM_PARAM_LOTS_MAX, Constant.FS_NOTICE_SECRET);
        Object urltRedis = redisCache.hGet(Constant.SYSTEM_PARAM_LOTS_MAX, Constant.FS_NOTICE_URL);

        Integer timestamp = Long.valueOf(System.currentTimeMillis()).intValue();
        String url=urltRedis.toString();

        // 清理URL中的非法字符
        url = url.replaceAll("\"", "");  // 去掉双引号
        JSONObject parameters =null;
        // 验证URL是否合法
        try {
            new URL(url);  // 如果URL不合法，会抛出MalformedURLException
        } catch (MalformedURLException e) {
            log.error("无效的URL: {}", url);
            return;
        }

        String secret = null;
        try {
            secret = GenSign(secretRedis.toString(),timestamp);
        } catch (Exception e) {
            log.error("发送消息失败:{}",e);
        }
        try {
            String json=null;
            if(vo.getTemplateType().equals(MessagesTypeEnum.MISSING_ORDERS_NOTICE.getCode())){
                 json=template(secret,timestamp,vo.getVpsName(),vo.getSourceRemarks(),vo.getSource(),vo.getFollow(),vo.getSymbol(),vo.getType());
            }else{
                 json=fixedTimeTemplate(secret,timestamp,vo.getNum());
            }
             parameters = JSON.parseObject(json);
            HttpResponse response = HttpRequest.post(url)
                    .body(parameters.toJSONString(), "application/json") // 设置表单参数转成json格式
                    .execute();
            String body = response.body();
            JSONObject jsonObject = JSON.parseObject(body);
            Integer code = jsonObject.getInteger("code");
            if(code!=0){
                log.error("发送消息失败:{}",body);
            }
        } catch (Exception e) {
            log.error("发送消息失败:发送url:{},参数：{}",url,parameters);
        }
    }

    /***
     * 判断是否需要发送消息
     * 1、VPS运行状态/VPS漏单监控  任意一个关闭，则整个VPS不监控
     * 2、VPS正常运行/VPS漏单监控开启，则判断账号跟单状态
     * 2/1主账号跟单状态关闭，则此主账号下所有跟单账号漏单都不监控
     * 2/2主账号跟单状态开启，跟单账号跟单状态关闭，则此跟单账号漏单不监控
     * 2/3主账号跟单状态开启，跟单账号跟单状态开启，则正常监控
     * */
    public  Boolean isSend(FollowVpsEntity vps,FollowTraderEntity follow, FollowTraderEntity master){
        if(ObjectUtil.isNotEmpty(vps.getIsMonitorRepair()) && vps.getIsMonitorRepair().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }
        if(ObjectUtil.isNotEmpty(vps.getIsActive()) && vps.getIsActive().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }
        if(ObjectUtil.isNotEmpty(master) &&  master.getFollowStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }
        if(ObjectUtil.isNotEmpty(follow) &&  follow.getFollowStatus().equals(CloseOrOpenEnum.CLOSE.getValue())){
            return false;
        }

        return true;
    }

}
