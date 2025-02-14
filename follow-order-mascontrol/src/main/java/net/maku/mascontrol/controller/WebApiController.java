package net.maku.mascontrol.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.*;
import net.maku.framework.common.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "对外api")
@AllArgsConstructor
public class WebApiController {
    private static final Logger log = LoggerFactory.getLogger(WebApiController.class);
    private final FollowVpsService followVpsService;

    /**
     * 喊单添加
     * @param vo 喊单者
     * @param req 请求
     * */
    @PostMapping("/source/insert")
    @Operation(summary = "喊单添加")
    public Result<String> insertSource(@RequestBody @Valid SourceInsertVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getServerId());
        return sendRequest(req, host, FollowConstant.SOURCE_INSERT, vo);

    }

    @PostMapping("/source/update")
    @Operation(summary = "喊单编辑")
    public Result<String> updateSource(@RequestBody @Valid SourceUpdateVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getServerId());
        return sendRequest(req, host, FollowConstant.SOURCE_UPDATE, vo);

    }

    @PostMapping("/source/delete")
    @Operation(summary = "喊单删除")
    public Result<String> delSource(@RequestBody @Valid SourceDelVo vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getServerId());
        return sendRequest(req, host, FollowConstant.SOURCE_DEL, vo);

    }

    @PostMapping("/follow/insert")
    @Operation(summary = "跟单添加")
    public Result<String> insertFollow(@RequestBody @Valid FollowInsertVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.FOLLOW_INSERT, vo);

    }

    @PostMapping("/follow/update")
    @Operation(summary = "跟单更新")
    public Result<String> updateFollow(@RequestBody @Valid FollowUpdateVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.FOLLOW_UPDATE, vo);

    }

    @PostMapping("/follow/delete")
    @Operation(summary = "跟单删除")
    public Result<String> delFollow(@RequestBody @Valid SourceDelVo vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getServerId());
        return sendRequest(req, host, FollowConstant.FOLLOW_DEL, vo);
    }

    @PostMapping("/orderhistory")
    @Operation(summary = "查询平仓订单")
    public Result<String> orderhistory(@RequestBody @Valid OrderHistoryVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.ORDERHISTORY, vo);
    }

    @PostMapping("/openedorders")
    @Operation(summary = "查询持仓订单")
    public Result<String> openedOrders(@RequestBody @Valid OpenOrderVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.OPENEDORDERS, vo);
    }


    @PostMapping("/ordersend")
    @Operation(summary = "开仓")
    public Result<String> ordersend(@RequestBody @Valid OrderSendVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.ORDERSEND, vo);
    }

    @PostMapping("/orderclose")
    @Operation(summary = "平仓")
    public Result<String> orderclose(@RequestBody @Valid OrderCloseVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());

        return sendRequest(req, host, FollowConstant.ORDERCLOSE, vo);
    }

    @PostMapping("/ordercloseall")
    @Operation(summary = "平仓-全平")
    public Result<String> orderCloseAll(@RequestBody @Valid OrderCloseAllVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.ORDERCLOSEALL, vo);
    }

    @PostMapping("/orderCloseProfit")
    @Operation(summary = "平仓盈利")
    public Result<String> orderCloseProfit(@RequestBody @Valid  OrderCloseAllVO vo, HttpServletRequest req) {
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.ORDERCLOSEPROFIT, vo);
    }

    @PostMapping("/orderCloseLoss")
    @Operation(summary = "平仓亏损")
    public Result<String> orderCloseLoss(@RequestBody @Valid  OrderCloseAllVO vo, HttpServletRequest req) {
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.ORDERCLOSELOSS, vo);
    }

    @PostMapping("/changepassword")
    @Operation(summary = "修改密码")
    public Result<String> changePassword(@RequestBody @Valid ChangePasswordVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.CHANGEPASSWORD, vo);
    }

    @PostMapping("/repairorder")
    @Operation(summary = "补单")
    public Result<String> repairOrder(@RequestBody @Valid RepairOrderVO vo, HttpServletRequest req) {
        //根据vpsId查询ip
        String host = getServerIp(vo.getClientId());
        return sendRequest(req, host, FollowConstant.REPAIRORDER, vo);
    }

    @GetMapping("/symbolParams")
    @Operation(summary = "品种规格")
    public Result<String> symbolParams(@RequestParam("clientId") Integer clientId, @RequestParam("accountId") Long accountId, @RequestParam("accountType") Integer accountType, HttpServletRequest req) {
        String host = getServerIp(clientId);
        HashMap<String, Object> map = new HashMap<>();
        map.put("accountId",accountId);
        map.put("accountType",accountType);
        return sendRequestByGetArray(req, host, FollowConstant.SYMBOLPARAMS, map);
    }


    /**
     * 根据vpsId查询vpsip
     * **/
    private String getServerIp(Integer serverId){
        FollowVpsEntity vps = followVpsService.getById(serverId);
        return  vps.getIpAddress();
       // return  "39.101.133.150";
    }
    /**
     * 远程调用方法封装 POST
     */
    private static <T> Result<String> sendRequest(HttpServletRequest req, String host, String uri, T t) {
        // 远程调用
        String url = MessageFormat.format("http://{0}:{1}{2}", host, FollowConstant.VPS_PORT, uri);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
        headers.add("x-sign", "417B110F1E71BD2CFE96366E67849B0B");
        ObjectMapper objectMapper = new ObjectMapper();
        // 将对象序列化为 JSON
        String jsonBody = null;
        try {
            jsonBody = objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            return Result.error("参数转换异常");

        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.POST, entity, byte[].class);
        byte[] data = response.getBody();
        JSONObject body = JSON.parseObject(new String(data));
        log.info("远程调用响应:{}", body);
        if (body != null && !body.getString("code").equals("0")) {
            String msg = body.getString("msg");
            log.error("远程调用异常: {}", body.get("msg"));
            return Result.error("远程调用异常: " + body.get("msg"));
        }
        return Result.ok(body.getString("data"));
    }

    /**
     * 远程调用方法封装 GET
     */
    private static <T> Result<String> sendRequestByGet(HttpServletRequest req, String host, String uri, Map<String,Object> t) {
        //远程调用
        String url = MessageFormat.format("http://{0}:{1}{2}", host, FollowConstant.VPS_PORT, uri);
        HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
        headers.add("x-sign","417B110F1E71BD2CFE96366E67849B0B");
        JSONObject jsonObject = new JSONObject();
        jsonObject.fluentPutAll(t);
        JSONObject body = RestUtil.request(url, HttpMethod.GET, headers, jsonObject, null, JSONObject.class).getBody();
        log.info("远程调用响应:{}", body);
        if (body != null && !body.getString("code").equals("0")) {
            String msg = body.getString("msg");
            log.error("远程调用异常: {}", body.get("msg"));
            return    Result.error("远程调用异常: " + body.get("msg"));
        }
        return Result.ok(body.getString("data"));
    }

    private static <T> Result<String> sendRequestByGetArray(HttpServletRequest req, String host, String uri, Map<String,Object> t) {
        // 远程调用
        String url = MessageFormat.format("http://{0}:{1}{2}", host, FollowConstant.VPS_PORT, uri);
        HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
        headers.add("x-sign", "417B110F1E71BD2CFE96366E67849B0B");
        JSONObject jsonObject = new JSONObject();
        jsonObject.fluentPutAll(t);
        JSONObject body = RestUtil.request(url, HttpMethod.GET, headers, jsonObject, null, JSONObject.class).getBody();
        log.info("远程调用响应:{}", body);
        if (body != null && !body.getString("code").equals("0")) {
            String msg = body.getString("msg");
            log.error("远程调用异常: {}", body.get("msg"));
            return Result.error(msg);
        }

        // 获取 data 字段并解析为 JSON 数组
        String dataStr = body.getString("data");
        List<ExternalSysmbolSpecificationVO> dataArray = JSON.parseArray(dataStr, ExternalSysmbolSpecificationVO.class);
        // 将 JSON 数组转换为字符串返回
        return Result.ok(JSON.toJSONString(dataArray));
    }


}
