package net.maku.mascontrol.filter;

import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Author:  zsd
 * Date:  2024/11/18/周一 15:43
 */
@Component
public class ResponseFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        //排除路径
        String uri = httpRequest.getRequestURI();
        if (uri.startsWith("/api/v1")) {
            // 这里是修改响应内容的部分
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            // 定义一个可修改的响应包装类
            ResponseWrapper responseWrapper = new ResponseWrapper(httpResponse);
            // 继续进行过滤链
            filterChain.doFilter(servletRequest, responseWrapper);

            // 获取响应内容并进行修改
            String oldData = new String(responseWrapper.getContent());
            JSONObject oldJson = JSONObject.parseObject(oldData);
            JSONObject newJson = new JSONObject();
            // 0表示成功，其他值表示失败
            newJson.put("success", oldJson.getInteger("code") == 0 ? true : false);
            newJson.put("message", oldJson.get("msg"));
            newJson.put("data", oldJson.get("data"));
            // 将修改后的内容写入响应
            //把返回值输出到客户端
            ServletOutputStream out = servletResponse.getOutputStream();
            out.write(newJson.toJSONString().getBytes());
            out.flush();
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }

    }
}
