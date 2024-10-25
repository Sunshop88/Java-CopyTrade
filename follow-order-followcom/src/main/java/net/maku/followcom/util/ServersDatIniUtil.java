package net.maku.followcom.util;
import cn.hutool.core.util.ObjectUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.service.FollowBrokeServerService;
import net.maku.followcom.vo.FollowBrokeServerVO;
import net.maku.framework.security.user.SecurityUser;
import net.maku.framework.security.user.UserDetail;
import online.mtapi.mt4.QuoteClient;
import online.mtapi.mt4.srv.PrimaryServer;
import online.mtapi.mt4.srv.SecondaryServer;
import online.mtapi.mt4.srv.Servers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MT4 MT5配置文件处理工具
 */
@Slf4j
@Component
public class ServersDatIniUtil {

    @Autowired
    private FollowBrokeServerService followBrokeServerService;
    @Data
    @AllArgsConstructor
    public static class Address {
        String host;
        Integer port;

        public String getServer() {
            return host + ":" + port;
        }
    }

    /**
     * @param host 经济商服务器ip
     * @param port 经济商服务器端口
     * @return 是否连通
     */
    public static boolean connected(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return socket.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean ExportServersIni(String url) throws IOException {
        List<Servers> serversList = QuoteClient.LoadServersIni(url);
        UserDetail user = SecurityUser.getUser();
        serversList.parallelStream().forEach(servers->{
            List<FollowBrokeServerVO> followBrokeServerVOList = new LinkedList<>();
            log.info("11111111"+servers.PrimaryServer.name);
            PrimaryServer primaryServer = servers.PrimaryServer;
            FollowBrokeServerVO followBrokeServerVO = new FollowBrokeServerVO();
            followBrokeServerVO.setServerName(primaryServer.name);
            followBrokeServerVO.setServerNode(primaryServer.getHost());
            followBrokeServerVO.setServerPort(String.valueOf(primaryServer.getPort()));
            //设置操作人和时间
            if (ObjectUtil.isNotEmpty(user)){
                followBrokeServerVO.setCreator(user.getId());
            }
            if (connected(servers.PrimaryServer.getHost(), servers.PrimaryServer.getPort())) {
                followBrokeServerVOList.add(followBrokeServerVO);
            }
            for (SecondaryServer secondaryServer : servers.SecondaryServers) {
                if (connected(secondaryServer.getHost(), secondaryServer.getPort())) {
                    FollowBrokeServerVO followBrokeServerVO1 = new FollowBrokeServerVO();
                    followBrokeServerVO1.setServerName(primaryServer.name);
                    followBrokeServerVO1.setServerNode(secondaryServer.getHost());
                    followBrokeServerVO1.setServerPort(String.valueOf(secondaryServer.getPort()));
                    //设置操作人和时间
                    if (ObjectUtil.isNotEmpty(user)){
                        followBrokeServerVO1.setCreator(user.getId());
                    }
                    followBrokeServerVOList.add(followBrokeServerVO1);
                }
            }
            List<FollowBrokeServerVO> collect = new ArrayList<>(followBrokeServerVOList.stream()
                    .collect(Collectors.toMap(
                            FollowBrokeServerVO::getServerNode, // 以 serverNode 作为键
                            server -> server,                   // 值是当前对象
                            (existing, replacement) -> replacement // 如果有重复的键，保留替换的值（你也可以根据需要选择保留 existing）
                    ))
                    .values());// 收集去重后的结果
            log.info("111"+servers.PrimaryServer.name+"size++++++"+collect.size());
            followBrokeServerService.saveList(collect);
        });
        return Boolean.TRUE;
    }
}
