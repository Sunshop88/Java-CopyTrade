package net.maku.subcontrol.filter;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import net.maku.framework.common.utils.Result;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Author:  zsd
 * Date:  2024/12/26/周四 13:41
 */
@Component
public class RequestFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        //排除路径
        String uri = httpRequest.getRequestURI();
        if (uri.startsWith("/api")) {
            String sign = httpRequest.getHeader("x-sign");
            //把返回值输出到客户端
            ServletOutputStream out = servletResponse.getOutputStream();
            if (ObjectUtil.isEmpty(sign) || !sign.equals("417B110F1E71BD2CFE96366E67849B0B")) {
                String jsonString = JSON.toJSONString(Result.error("签名无效,暂无权限访问"));
                out.write(jsonString.getBytes());
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
