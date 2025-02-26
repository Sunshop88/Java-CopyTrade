package net.maku.mascontrol.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.service.BargainService;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.common.utils.Result;
import net.maku.mascontrol.vo.FollowOrderHistoryQuery;
import net.maku.mascontrol.vo.FollowOrderHistoryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.text.MessageFormat;
import java.util.Map;


/**
 * Author:  zsd
 * Date:  2025/2/25/周二 15:11
 */
@RestController
@RequestMapping("/bargain")
@Tag(name = "交易")
@AllArgsConstructor
public class BargainController {
    private static final Logger log = LoggerFactory.getLogger(BargainController.class);
    private final FollowVpsService followVpsService;
    private BargainService bargainService;

    @GetMapping("histotyOrderList")
    @Operation(summary = "历史订单")
    @PreAuthorize("hasAuthority('mascontrol:trader')")
    public Result<PageResult<FollowOrderHistoryVO>> histotyOrderList(@ParameterObject FollowOrderHistoryQuery followOrderHistoryQuery,HttpServletRequest request) {
        Integer vpsId = followOrderHistoryQuery.getVpsId();
        FollowVpsEntity vps = followVpsService.getById(vpsId);
        if (vps==null) {
            throw new ServerException("vps不存在");
        }
        Result result = sendRequest(request, vps.getIpAddress(), HttpMethod.GET, FollowConstant.HISTOTY_ORDER_LIST, followOrderHistoryQuery);

        return result;
    }
    /**
     * 远程调用方法封装
     */
    private static <T> Result sendRequest(HttpServletRequest req, String host,HttpMethod method, String uri, T t) {
        //远程调用
        String url = MessageFormat.format("http://{0}:{1}{2}", "127.0.0.1", FollowConstant.VPS_PORT, uri);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
        headers.add("x-sign","417B110F1E71BD2CFE96366E67849B0B");
        ObjectMapper objectMapper = new ObjectMapper();
        // 将对象序列化为 JSON
        String jsonBody = null;
        try {
            jsonBody = objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            return Result.error("参数转换异常");

        }
        ResponseEntity<byte[]> response =null;
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        if(HttpMethod.GET.equals(method)) {
            Map<String, Object> map = BeanUtil.beanToMap(t);
            StringBuilder sb=new StringBuilder();
            if(ObjectUtil.isNotEmpty(map)) {
                map.forEach((k,v)->{
                    if (v!=null ){
                        sb.append(k).append("=").append(v).append("&");
                    }

                });
            }
            if(!sb.isEmpty()){
                url=url+"?"+sb.toString();
            }
            response=  restTemplate.exchange(url, method, entity, byte[].class,map);
        }else{
            response = restTemplate.exchange(url, method, entity, byte[].class);
        }

        byte[] data = response.getBody();
        JSONObject body = JSON.parseObject(new String(data));
        log.info("远程调用响应:{}", body);
        if (body != null && !body.getString("code").equals("0")) {
            String msg = body.getString("msg");
            log.error("远程调用异常: {}", body.get("msg"));
            return    Result.error("远程调用异常: " + body.get("msg"));
        }
        return Result.ok(body.get("data"));
    }

}
