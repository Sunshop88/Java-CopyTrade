package net.maku.subcontrol.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ServerIpInterceptor implements HandlerInterceptor {
    // 这里设置一个默认的 serverIp，如果不需要传递 serverIp，可以使用这个
    private static final String DEFAULT_SERVER_IP = "192.168.0.1";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的 serverIp 参数
        String serverIp = request.getHeader("serverIp");
        // 你可以在这里进行进一步的处理，比如校验 serverIp 是否存在或者是否合法
        if (serverIp == null || serverIp.isEmpty()) {
            String requestUri = request.getRequestURI();

            // 例如：特定路径 "/api/server" 使用默认的 serverIp
            if (requestUri.startsWith("/doc.html")||requestUri.startsWith("/webjars")||requestUri.startsWith("/v3")) {
                serverIp = DEFAULT_SERVER_IP;
            } else {
                // 如果路径不匹配并且 serverIp 为空，则返回错误
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Missing required header: serverIp");
                return false;
            }
        }
        // 如果需要将 serverIp 传递给后续处理逻辑，可以将其保存到 request 属性中
        request.setAttribute("serverIp", serverIp);

        // 返回 true 表示继续执行后续的处理
        return true;
    }
}