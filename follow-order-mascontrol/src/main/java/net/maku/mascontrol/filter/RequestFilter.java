package net.maku.mascontrol.filter;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.entity.FollowVpsUserEntity;
import net.maku.followcom.service.FollowVpsService;
import net.maku.followcom.service.FollowVpsUserService;
import net.maku.followcom.util.FollowConstant;
import net.maku.framework.common.utils.Result;
import net.maku.framework.security.user.SecurityUser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Component
public class RequestFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        //排除路径
        String uri = httpRequest.getRequestURI();
        if (uri.startsWith("/bargain/pushOrder")) {
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
