package net.maku.subcontrol.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.maku.followcom.util.FollowConstant;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ServerIpInterceptor implements HandlerInterceptor {
    // 默认本机ip
    private static final String DEFAULT_SERVER_IP = FollowConstant.LOCAL_HOST;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的 serverIp 参数
        String serverIp = request.getHeader("serverIp");
        if (serverIp == null || serverIp.isEmpty()) {
            serverIp = DEFAULT_SERVER_IP;
        }
        // 如果需要将 serverIp 传递给后续处理逻辑，可以将其保存到 request 属性中
        request.setAttribute("serverIp", serverIp);

        // 返回 true 表示继续执行后续的处理
        return true;
    }
}