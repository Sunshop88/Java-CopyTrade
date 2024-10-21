package net.maku.subcontrol.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ServerIpInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的 serverIp 参数
        String serverIp = request.getHeader("serverIp");

        // 你可以在这里进行进一步的处理，比如校验 serverIp 是否存在或者是否合法
        if (serverIp == null || serverIp.isEmpty()) {
            // 如果 serverIp 为空，可以直接返回错误响应
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Missing required header: serverIp");
            return false; // 阻止请求继续处理
        }

        // 如果需要将 serverIp 传递给后续处理逻辑，可以将其保存到 request 属性中
        request.setAttribute("serverIp", serverIp);

        // 返回 true 表示继续执行后续的处理
        return true;
    }
}