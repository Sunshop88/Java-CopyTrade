//package net.maku.subcontrol.listener.impl;
//
//import lombok.extern.slf4j.Slf4j;
//import net.maku.followcom.entity.FollowTraderEntity;
//import net.maku.followcom.service.FollowTraderService;
//import net.maku.subcontrol.listener.IEquityRiskListener;
//import net.maku.subcontrol.trader.AbstractApiTrader;
//
//import java.math.BigDecimal;
//
///**
// * @author hipil
// */
//@Slf4j
//public class EquityRiskListenerImpl implements IEquityRiskListener {
//    protected FollowTraderService traderService;
//    protected IAotfxMasterSlaveService masterSlaveService;
//    //日志记录
//    protected FollowTraderLogService sensitiveDataLogService;
//
//    public EquityRiskListenerImpl() {
//        this.traderService = SpringContextUtils.getBean(AotfxTraderServiceImpl.class);
//        this.masterSlaveService = SpringContextUtils.getBean(AotfxMasterSlaveServiceImpl.class);
//        this.sensitiveDataLogService = SpringContextUtils.getBean(BchainSensitiveDataLogServiceImpl.class);
//    }
//
//    @Override
//    public void onTriggered(AbstractApiTrader apiTrader, BigDecimal riskEquity) {
//        FollowTraderEntity trader = apiTrader.getTrader();
//        long traderId = trader.getId();
//        String account = trader.getAccount();
////        Integer isProtectEquity = trader4.getIsProtectEquity();
////        boolean ac = Boolean.FALSE;
////        try {
////            if (isProtectEquity==0){
////                log.info("跟单账号：{} 无需触发风控,净值保护关闭中。", account);
////                return;
////            }
////            double curEquity = apiTrader.quoteClient.AccountEquity();
////            if (BigDecimal.valueOf(curEquity).compareTo(riskEquity) > 0) {
////                log.info("跟单账号：{} 无需触发风控,当前净值 {} 大于风控净值 {}。", account, curEquity, riskEquity);
////                return;
////            } else {
////                log.warn("跟单账号：{} 触发风控,当前净值 {} 小于风控净值 {}。", account, curEquity, riskEquity);
////            }
//
//
//            //邮件推送
////            LoginUser sysUser = sysBaseAPI.getUserById(trader4.getUserId());
////            if (!ObjectUtils.isEmpty(sysUser.getEmail())) {
////                Map<String, String> templateParam = new HashMap<>();
////                templateParam.put("account", trader4.getAccount());
////                templateParam.put("equity", String.valueOf(curEquity));
////                templateParam.put("order", content.substring(content.indexOf("#", 1) + 1));
////                sysBaseAPI.sendTemplateEmailMsgBatch(new TemplateEmailMsgDTO(new String[]{sysUser.getEmail()}, new String[]{}, new String[]{}, "EMAIL_MIN_EQUITY_RISK", templateParam));
////            }
////            //站内消息推送
////            MessageDTO messageDTO = new MessageDTO();
////            messageDTO.setFromUser(trader4.getUserId());
////            messageDTO.setToUser("e9ca23d68d884d4ebb19d07889727dae");
////            messageDTO.setTitle("注意！您有账户触发净值风控");
////            messageDTO.setContent("社区后台已检测到您的账户【" + account + "】已触发净值风控，净值【" + curEquity + "】，现已全部平仓并暂停跟单！（当净值达到净值保护金额将进行全部跟随订单平仓，并暂停订阅，如需继续跟单，需要解除订阅关系后重新订阅。）");
////            sysBaseAPI.sendSysAnnouncement(messageDTO);
////            AotfxTrader aotfxTrader = new AotfxTrader();
////            aotfxTrader.setId(traderId);
////            aotfxTrader.setIsProtectEquity(0);
////            traderService.updateById(aotfxTrader);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            if (ac) {
//                apiTrader.getEquitySemaphore().release();
//            }
//        }
//    }
//}
