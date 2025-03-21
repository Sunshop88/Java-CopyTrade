package net.maku.followcom.util;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.maku.framework.common.utils.Result;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 调用 Restful 接口 Util
 *
 * @author sunjianlei
 */
@Slf4j
public class RestUtil {

    private static String domain = null;

    public static String getDomain() {
//        if (domain == null) {
//            domain = SpringContextUtils.getDomain();
//            // issues/2959
//            // 微服务版集成企业微信单点登录
//            // 因为微服务版没有端口号，导致 SpringContextUtils.getDomain() 方法获取的域名的端口号变成了:-1所以出问题了，只需要把这个-1给去掉就可以了。
//            if (domain.endsWith(":-1")) {
//                domain = domain.substring(0, domain.length() - 3);
//            }
//        }
//        return domain;
        // update:2021-12-10 直接改成最外层代理服务器的域名
        return "https://cloudfollow.zlinked.cn";
    }

    public static String path = null;

    public static String getPath() {
        if (path == null) {
            path = SpringContextUtils.getApplicationContext().getEnvironment().getProperty("server.servlet.context-path");
        }
        return oConvertUtils.getString(path);
    }

    public static String getBaseUrl() {
        String basepath = getDomain() + getPath();
        log.info(" RestUtil.getBaseUrl: " + basepath);
        return basepath;
    }

    /**
     * RestAPI 调用器
     */
    private final static RestTemplate RT;

    /**
     * RestAPI 调用器 调微信接口专用
     */
    private final static RestTemplate RT_WX;

    static {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        RT = new RestTemplate(requestFactory);
        // 解决乱码问题
        RT.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        // add:2021-12-10 RT_WX
        RT_WX = new RestTemplate(requestFactory);
        RT_WX.getMessageConverters().add(new WxMappingJackson2HttpMessageConverter());
    }

    public static RestTemplate getRestTemplate() {
        return RT;
    }

    /**
     * 发送 get 请求
     */
    public static JSONObject get(String url) {
        return getNative(url, null, null).getBody();
    }

    /**
     * 发送 get 请求
     */
    public static JSONObject get(String url, JSONObject variables) {
        return getNative(url, variables, null).getBody();
    }

    /**
     * 发送 get 请求
     */
    public static JSONObject get(String url, JSONObject variables, JSONObject params) {
        return getNative(url, variables, params).getBody();
    }

    /**
     * 发送 get 请求，返回原生 ResponseEntity 对象
     */
    public static ResponseEntity<JSONObject> getNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.GET, variables, params);
    }

    /**
     * 发送 Post 请求
     */
    public static JSONObject post(String url) {
        return postNative(url, null, null).getBody();
    }

    /**
     * 发送 Post 请求
     */
    public static JSONObject post(String url, JSONObject params) {
        return postNative(url, null, params).getBody();
    }

    /**
     * 发送 Post 请求
     */
    public static JSONObject post(String url, JSONObject variables, JSONObject params) {
        return postNative(url, variables, params).getBody();
    }

    /**
     * 发送 POST 请求，返回原生 ResponseEntity 对象
     */
    public static ResponseEntity<JSONObject> postNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.POST, variables, params);
    }

    /**
     * 发送 put 请求
     */
    public static JSONObject put(String url) {
        return putNative(url, null, null).getBody();
    }

    /**
     * 发送 put 请求
     */
    public static JSONObject put(String url, JSONObject params) {
        return putNative(url, null, params).getBody();
    }

    /**
     * 发送 put 请求
     */
    public static JSONObject put(String url, JSONObject variables, JSONObject params) {
        return putNative(url, variables, params).getBody();
    }

    /**
     * 发送 put 请求，返回原生 ResponseEntity 对象
     */
    public static ResponseEntity<JSONObject> putNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.PUT, variables, params);
    }

    /**
     * 发送 delete 请求
     */
    public static JSONObject delete(String url) {
        return deleteNative(url, null, null).getBody();
    }

    /**
     * 发送 delete 请求
     */
    public static JSONObject delete(String url, JSONObject variables, JSONObject params) {
        return deleteNative(url, variables, params).getBody();
    }

    /**
     * 发送 delete 请求，返回原生 ResponseEntity 对象
     */
    public static ResponseEntity<JSONObject> deleteNative(String url, JSONObject variables, JSONObject params) {
        return request(url, HttpMethod.DELETE, null, variables, params, JSONObject.class);
    }

    /**
     * 发送请求
     */
    public static ResponseEntity<JSONObject> request(String url, HttpMethod method, JSONObject variables, JSONObject params) {
        return request(url, method, getHeaderApplicationJson(), variables, params, JSONObject.class);
    }

    /**
     * 发送请求
     *
     * @param url          请求地址
     * @param method       请求方式
     * @param headers      请求头  可空
     * @param variables    请求url参数 可空
     * @param params       请求body参数 可空
     * @param responseType 返回类型
     * @return ResponseEntity<responseType>
     */
    public static <T> ResponseEntity<T> request(String url, HttpMethod method, HttpHeaders headers, JSONObject variables, Object params, Class<T> responseType) {
        log.info(" RestUtil  --- request ---  url = "+ url);
        if (StringUtils.isEmpty(url)) {
            throw new RuntimeException("url 不能为空");
        }
        if (method == null) {
            throw new RuntimeException("method 不能为空");
        }
        if (headers == null) {
            headers = new HttpHeaders();
        }
        // 请求体
        String body = "";
        if (params != null) {
            if (params instanceof JSONObject) {
                body = ((JSONObject) params).toJSONString();

            } else {
                body = params.toString();
            }
        }
        // 拼接 url 参数
        if (variables != null) {
            url += ("?" + asUrlVariables(variables));
        }
        // 发送请求
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return RT.exchange(url, method, request, responseType);
    }

    /**
     * 发送请求 支持调用微信接口
     *
     * @param url          请求地址
     * @param method       请求方式
     * @param headers      请求头  可空
     * @param variables    请求url参数 可空
     * @param params       请求body参数 可空
     * @param responseType 返回类型
     * @return ResponseEntity<responseType>
     */
    public static <T> ResponseEntity<T> request(String url, HttpMethod method, HttpHeaders headers, JSONObject variables, Object params, Class<T> responseType, boolean isWx) {
        if (!isWx) {
            return request(url, method, headers, variables, params, responseType);
        }
        log.info(" RestUtil  --- request ---  url = " + url);
        if (StringUtils.isEmpty(url)) {
            throw new RuntimeException("url 不能为空");
        }
        if (method == null) {
            throw new RuntimeException("method 不能为空");
        }
        if (headers == null) {
            headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(MediaType.APPLICATION_JSON_UTF8_VALUE));
        }
        // 请求体
        String body = "";
        if (params != null) {
            if (params instanceof JSONObject) {
                body = ((JSONObject) params).toJSONString();

            } else {
                body = params.toString();
            }
        }
        // 拼接 url 参数
        if (variables != null) {
            url += ("?" + asUrlVariables(variables));
        }
        // 发送请求
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return RT_WX.exchange(url, method, request, responseType);
    }


    /**
     * 获取JSON请求头
     */
    public static HttpHeaders getHeaderApplicationJson() {
        return getHeader(MediaType.APPLICATION_JSON_UTF8_VALUE);
    }

    /**
     * 获取请求头
     */
    public static HttpHeaders getHeader(String mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.add("Accept", mediaType);
        return headers;
    }

    /**
     * 将 JSONObject 转为 a=1&b=2&c=3...&n=n 的形式
     */
    public static String asUrlVariables(JSONObject variables) {
        Map<String, Object> source = variables.getInnerMap();
        Iterator<String> it = source.keySet().iterator();
        StringBuilder urlVariables = new StringBuilder();
        while (it.hasNext()) {
            String key = it.next();
            String value = "";
            Object object = source.get(key);
            if (object != null) {
                if (!StringUtils.isEmpty(object.toString())) {
                    value = object.toString();
                }
            }
            urlVariables.append("&").append(key).append("=").append(value);
        }
        // 去掉第一个&
        return urlVariables.substring(1);
    }

    private static class WxMappingJackson2HttpMessageConverter extends MappingJackson2HttpMessageConverter {
        public WxMappingJackson2HttpMessageConverter() {
            List<MediaType> mediaTypes = new ArrayList<>();
            mediaTypes.add(MediaType.TEXT_PLAIN);
            mediaTypes.add(MediaType.TEXT_HTML);
            setSupportedMediaTypes(mediaTypes);
        }
    }

    /**
     * 获取JSON请求头
     */
    public static HttpHeaders getHeaderApplicationJsonAndToken(HttpServletRequest request) {
        HttpHeaders header = getHeader(MediaType.APPLICATION_JSON_UTF8_VALUE);
        header.add("Authorization", request.getHeader("Authorization"));
        return header;
    }

    /**
     * 远程调用方法封装
     */
    public static <T> Result sendRequest(HttpServletRequest req, String host, HttpMethod method, String uri, T t,HttpHeaders header,String port) {
        //远程调用
        String url = MessageFormat.format("http://{0}:{1}{2}", host, port, uri);
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers =null;
        if(ObjectUtil.isNotEmpty(header)) {
             headers =header;
        }else{
             headers = RestUtil.getHeaderApplicationJsonAndToken(req);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
       // headers.add("x-sign","417B110F1E71BD2CFE96366E67849B0B");
        ObjectMapper objectMapper = new ObjectMapper();
        // 将对象序列化为 JSON
        String jsonBody = null;
        try {
            if(t!=null) {
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                //格式化时间格式
                javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                objectMapper.registerModule(javaTimeModule);
                jsonBody = objectMapper.writeValueAsString(t);
            /*    JSONObject jsonObject = JSONObject.parseObject(jsonBody);
                JSONObject newjson = new JSONObject();
                jsonObject.forEach((key, value) -> {
                    if(ObjectUtil.isNotEmpty(value)) {
                        newjson.put(key, value);
                    }
                });
                jsonBody=newjson.toJSONString();*/
            }

        } catch (JsonProcessingException e) {
            return Result.error("参数转换异常");

        }
        ResponseEntity<byte[]> response =null;
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        if(HttpMethod.GET.equals(method)) {
            Map<String, Object> map =null;
            if(t!=null) {
                 map = BeanUtil.beanToMap(t);
                StringBuilder sb=new StringBuilder();
                if(ObjectUtil.isNotEmpty(map)) {
                    map.forEach((k,v)->{
                        if (v!=null ){
                            sb.append(k).append("=").append(v).append("&");
                        }

                    });
                }
                if(!sb.isEmpty()){
                    Random random=new Random();
                    int randomNum  = random.nextInt(10000);
                    url=url+"?randomNum="+randomNum+"&"+sb.toString().substring(0,sb.toString().length()-1);
                }
            }
            try {

                response=  restTemplate.exchange(url, method, entity, byte[].class,map);
            } catch (Exception e) {
                log.error("response远程调用异常: {}", e);
            }
        }else{

            response = restTemplate.exchange(url, method, entity, byte[].class);
        }

        if(response!=null && response.getBody()!=null) {
            byte[] data = response.getBody();
            JSONObject body = JSON.parseObject(new String(data));
            log.info("远程调用响应:{}", body);
            if (body != null && !body.getString("code").equals("0")) {
                String msg = body.getString("msg");
                log.error("远程调用异常: {}", body.get("msg"));
                return Result.error("远程调用异常: " + body.get("msg"));
            }
            return Result.ok(body.get("data")==null?"成功":body.get("data"));
        }
       return Result.error();
    }
}
