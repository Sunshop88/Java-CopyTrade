package net.maku.mascontrol.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Enumeration;
import java.util.Objects;
import java.util.stream.Collectors;
@Component
public class PortRoutingInterceptor implements HandlerInterceptor {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();

        // 如果请求以 /subcontrol 开头，执行转发逻辑
        if (requestUri.startsWith("/subcontrol")) {
            String scheme = request.getScheme();
            String serverName = request.getServerName();
            int serverPort = 9001; // 目标端口
            String forwardUrl = String.format("%s://%s:%d%s", scheme, serverName, serverPort, requestUri);

            // 获取原始请求头
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.add(headerName, request.getHeader(headerName));
            }

            // 如果请求有参数，保留参数
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                forwardUrl += "?" + queryString;
            }

            // 处理请求体（例如 JSON 数据）
            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));

            // 将原始请求数据转发到目标服务
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            response.setCharacterEncoding("UTF-8");
            try {
                // 发起请求并捕获响应
                ResponseEntity<String> forwardResponse = restTemplate.exchange(forwardUrl, HttpMethod.valueOf(request.getMethod()), entity, String.class);

                // 将目标服务器的响应返回给客户端
                response.setStatus(forwardResponse.getStatusCodeValue());
                response.getWriter().write(Objects.requireNonNull(forwardResponse.getBody()));
            } catch (Exception ex) {
                // 捕获异常，并返回自定义的错误信息
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.setContentType("application/json");
                String errorMessage = String.format("{\"code\":500, \"msg\":\"%s\", \"data\":null}", ex.getMessage());
                response.getWriter().write(errorMessage);
            }

            return false; // 阻止原始请求的进一步处理
        }

        // 非 /sub 请求则继续正常处理
        return true;
    }
}
