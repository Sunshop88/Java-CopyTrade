package net.maku.subcontrol.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.enums.MessagesTypeEnum;
import net.maku.framework.common.cache.RedisCache;
import net.maku.framework.common.constant.Constant;
import net.maku.subcontrol.service.MessagesService;
import net.maku.subcontrol.vo.FixTemplateVO;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

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


    private  String template(String secret, Integer timestamp,String vpsName,String sourceRemarks,String source,String follow,String symbol,String type) {
        String json="{\n" +
                "    \"timestamp\": \""+timestamp+"\",\n" +
                "    \"timestamp\": \""+secret+"\",\n" +
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
                "    \"timestamp\": \""+secret+"\",\n" +
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
        String secret = null;
        try {
            secret = GenSign(secretRedis.toString(),timestamp);
        } catch (Exception e) {
            log.error("发送消息失败:{}",e);
        }
        String json=null;
           if(vo.getTemplateType().equals(MessagesTypeEnum.MISSING_ORDERS_NOTICE.getCode())){
                json=template(secret,timestamp,vo.getVpsName(),vo.getSourceRemarks(),vo.getSource(),vo.getFollow(),vo.getSymbol(),vo.getType());
           }else{
                json=fixedTimeTemplate(secret,timestamp,vo.getNum());
           }
        JSONObject parameters = JSON.parseObject(json);
        HttpResponse response = HttpRequest.post(url)
                .body(parameters.toJSONString(), "application/json") // 设置表单参数转成json格式
                .execute();
        String body = response.body();
        JSONObject jsonObject = JSON.parseObject(body);
        Integer code = jsonObject.getInteger("code");
        if(code!=0){
            log.error("发送消息失败:{}",body);
        }
    }


}
