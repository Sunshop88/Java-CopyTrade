package net.maku.subcontrol.filter;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.service.FollowVpsUserService;
import net.maku.framework.common.utils.Result;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Author:  zsd
 * Date:  2024/12/26/周四 13:41
 */
@Component
public class RequestFilter implements Filter {
    private final FollowVpsUserService followVpsUserService;
    private final FollowVpsService followVpsService;

    public RequestFilter(FollowVpsUserService followVpsUserService, FollowVpsService followVpsService) {
        this.followVpsUserService = followVpsUserService;
        this.followVpsService = followVpsService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        //排除路径
        String uri = httpRequest.getRequestURI();
        String url = httpRequest.getRequestURL().toString();
        if (uri.startsWith("/api")) {
            String sign = httpRequest.getHeader("x-sign");
            //把返回值输出到客户端
            ServletOutputStream out = servletResponse.getOutputStream();
            if (ObjectUtil.isEmpty(sign) || !sign.equals("417B110F1E71BD2CFE96366E67849B0B")) {
                String jsonString = JSON.toJSONString(Result.error("签名无效,暂无权限访问"));
                out.write(jsonString.getBytes());
                return;
            }
        }else {

//            if (ObjectUtil.isEmpty(SecurityUser.getUserId())) {
//                servletResponse.setContentType("application/json");
//                servletResponse.setCharacterEncoding("UTF-8");
//                ServletOutputStream out = servletResponse.getOutputStream();
//                String jsonString = JSON.toJSONString(Result.error("未授权"));
//                out.write(jsonString.getBytes());
//                out.flush();
//                return;
//            }
            // 获取 userId
            Long userId = SecurityUser.getUserId();
            if (userId != 10000) {
                // 根据 userId 查询 vps
                List<String> vpsList = followVpsUserService.getVpsListByUserId(userId);
                if (vpsList == null || vpsList.isEmpty()) {
                    servletResponse.setContentType("application/json");
                    servletResponse.setCharacterEncoding("UTF-8");
                    ServletOutputStream out = servletResponse.getOutputStream();
                    String jsonString = JSON.toJSONString(Result.error("无权限访问"));
                    out.write(jsonString.getBytes());
                    out.flush();
                    return;
                }

                // 检查 URl 是否以允许的 IP 地址开头
                boolean isAllowed = false;
                for (String vps : vpsList) {
                    // 根据名称查询 IP 地址
                    Optional<FollowVpsEntity> vpsEntityOptional = followVpsService.list().stream()
                            .filter(vpsEntity -> vpsEntity.getName().equals(vps))
                            .findFirst();

                    if (vpsEntityOptional.isPresent()) {
                        String ip = vpsEntityOptional.get().getIpAddress();
                        if (url.startsWith("http://" + ip + ":9001/subcontrol/follow") || url.startsWith("http://" + ip + ":9001/subcontrol/trader")) {
                            isAllowed = true;
                            break;
                        }
                    }
                }

                if (isAllowed) {
                    filterChain.doFilter(servletRequest, servletResponse);
                } else {
                    servletResponse.setContentType("application/json");
                    servletResponse.setCharacterEncoding("UTF-8");
                    ServletOutputStream out = servletResponse.getOutputStream();
                    String jsonString = JSON.toJSONString(Result.error("无权限访问"));
                    out.write(jsonString.getBytes());
                    out.flush();
                    return;
                }
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}
