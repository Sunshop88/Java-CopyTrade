package net.maku.mascontrol.controller;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.util.RestUtil;
import net.maku.followcom.vo.FollowInsertVO;
import net.maku.followcom.vo.SourceDelVo;
import net.maku.followcom.vo.SourceInsertVO;
import net.maku.followcom.vo.SourceUpdateVO;
import net.maku.framework.common.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.text.MessageFormat;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "对外api")
@AllArgsConstructor
public class WebApiController {
    private static final Logger log = LoggerFactory.getLogger(WebApiController.class);

    @PostMapping("/source/insert")
    @Operation(summary = "喊单添加")
    public Result<String> insertSource(@RequestBody @Valid SourceInsertVO vo, HttpServletRequest req) {
        return sendRequest(req, FollowConstant.SUBCONTROL_SERVER, FollowConstant.SOURCE_INSERT, vo);

    }

    @PostMapping("/source/update")
    @Operation(summary = "喊单编辑")
    public Result<String> updateSource(@RequestBody @Valid SourceUpdateVO vo, HttpServletRequest req) {
        return sendRequest(req, FollowConstant.SUBCONTROL_SERVER, FollowConstant.SOURCE_UPDATE, vo);

    }

    @PostMapping("/source/delete")
    @Operation(summary = "喊单删除")
    public Result<String> delSource(@RequestBody @Valid SourceDelVo vo, HttpServletRequest req) {
        return sendRequest(req, FollowConstant.SUBCONTROL_SERVER, FollowConstant.SOURCE_DEL, vo);

    }

    @PostMapping("/follow/insert")
    @Operation(summary = "跟单添加")
    public Result<String> insertFollow(@RequestBody @Valid FollowInsertVO vo, HttpServletRequest req) {
        return sendRequest(req, FollowConstant.SUBCONTROL_SERVER, FollowConstant.FOLLOW_INSERT, vo);

    }

    @PostMapping("/follow/update")
    @Operation(summary = "跟单更新")
    public Result<String> updateFollow(@RequestBody @Valid SourceInsertVO vo, HttpServletRequest req) {
        return sendRequest(req, FollowConstant.SUBCONTROL_SERVER, FollowConstant.FOLLOW_UPDATE, vo);

    }

    @PostMapping("/follow/delete")
    @Operation(summary = "跟单删除")
    public Result<String> delFollow(@RequestBody @Valid SourceInsertVO vo, HttpServletRequest req) {
        return sendRequest(req, FollowConstant.SUBCONTROL_SERVER, FollowConstant.FOLLOW_DEL, vo);

    }


    /**
     * 远程调用方法封装
     */
    private static <T> Result<String> sendRequest(HttpServletRequest req, String host, String uri, T t) {
        //远程调用
        String url = MessageFormat.format("http://{0}:{1}{2}", host, FollowConstant.VPS_PORT, uri);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = RestUtil.getHeaderApplicationJsonAndToken(req);
        ObjectMapper objectMapper = new ObjectMapper();
        // 将对象序列化为 JSON
        String jsonBody = null;
        try {
            jsonBody = objectMapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            return Result.error("参数转换异常");

        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<JSONObject> response = restTemplate.exchange(url, HttpMethod.POST, entity, JSONObject.class);
        JSONObject body = response.getBody();
        log.info("远程调用响应:{}", body);
        if (body != null && !body.getString("code").equals("0")) {
            String msg = body.getString("msg");
            log.error("远程调用异常: {}", body.get("msg"));
            Result.error("远程调用异常: " + body.get("msg"));
        }
        return Result.ok();
    }

}
