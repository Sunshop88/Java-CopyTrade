package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fhs.trans.service.impl.TransService;
import lombok.AllArgsConstructor;
import net.maku.followcom.convert.FollowTestDetailConvert;
import net.maku.followcom.dao.FollowTestDetailDao;
import net.maku.followcom.entity.FollowTestDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.enums.ConCodeEnum;
import net.maku.followcom.enums.TraderTypeEnum;
import net.maku.followcom.query.FollowTestDetailQuery;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.service.FollowPlatformService;
import net.maku.followcom.service.FollowTestDetailService;
import net.maku.followcom.service.FollowTraderService;
import net.maku.followcom.vo.FollowTestDetailExcelVO;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.followcom.vo.FollowTraderCountVO;
import net.maku.followcom.vo.FollowTraderVO;
import net.maku.framework.common.utils.ExcelUtils;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import online.mtapi.mt4.QuoteClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Service
@AllArgsConstructor
public class FollowTestDetailServiceImpl extends BaseServiceImpl<FollowTestDetailDao, FollowTestDetailEntity> implements FollowTestDetailService {
    private final TransService transService;
    private final FollowPlatformService followPlatformService;
    private final FollowTraderService followTraderService;
    private final FollowVpsServiceImpl followVpsServiceImpl;

    public PageResult<String[]> page(FollowTestDetailQuery query) {
        List<FollowTestDetailEntity> allRecords = baseMapper.selectList(getWrapper(query));
        List<FollowTestDetailVO> detailVOList = FollowTestDetailConvert.INSTANCE.convertList(allRecords);
        // 用于最终结果的列表
        List<String[]> result = new ArrayList<>();

        Set<String> uniqueVpsNames = new LinkedHashSet<>();
        for (FollowTestDetailVO detail : detailVOList) {
            uniqueVpsNames.add(detail.getVpsName());
        }
        List<String> header = new ArrayList<>();
        header.add("服务器名称");
        header.add("平台类型");
        header.add("服务器节点");
        header.addAll(uniqueVpsNames);
        // 将表头转换为数组并作为固定的第一行加入结果中
        result.add(header.toArray(new String[0]));

        // 暂存每个 key 对应的速度数据
        Map<String, Map<String, Double>> speedMap = new HashMap<>();
        for (FollowTestDetailVO detail : detailVOList) {
            String key = detail.getServerName() + "_" + detail.getPlatformType() + "_" + detail.getServerNode();
            String vpsName = detail.getVpsName();
            double speed = detail.getSpeed();
            speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, speed);
        }

        List<String[]> dataRows = new ArrayList<>();
        List<Map.Entry<String, Map<String, Double>>> sortedEntries = new ArrayList<>(speedMap.entrySet());
        sortedEntries.sort(Comparator.comparing(e -> e.getKey().split("_")[0])); // 按服务器名称排序

        for (Map.Entry<String, Map<String, Double>> entry : sortedEntries) {
            String key = entry.getKey();
            String[] keyParts = key.split("_");
            String serverName = keyParts[0];
            String platformType = keyParts[1];
            String serverNode = keyParts[2];

            Map<String, Double> vpsSpeeds = entry.getValue();
            String[] dataRow = new String[3 + uniqueVpsNames.size()];
            dataRow[0] = serverName;
            dataRow[1] = platformType;
            dataRow[2] = serverNode;

            int index = 3;
            for (String vpsName : uniqueVpsNames) {
                Double speed = vpsSpeeds.get(vpsName);
                dataRow[index++] = (speed != null) ? speed.toString() : "null";
            }

            dataRows.add(dataRow);
        }

        // 计算分页的开始和结束索引
        int page = query.getPage();
        int limit = query.getLimit();
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, dataRows.size());
        List<String[]> paginatedDataRows = dataRows.subList(start, end);
        result.addAll(paginatedDataRows);

        PageResult<String[]> pageResult = new PageResult<>(result, dataRows.size());
        return pageResult;
    }

    @Override
    public void deleteByTestId(Integer id) {
        //根据testId删除其数据
        baseMapper.delete(Wrappers.<FollowTestDetailEntity>lambdaQuery().eq(FollowTestDetailEntity::getTestId, id));
    }

    private LambdaQueryWrapper<FollowTestDetailEntity> getWrapper(FollowTestDetailQuery query) {
        LambdaQueryWrapper<FollowTestDetailEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(FollowTestDetailEntity::getTestId, query.getTestId());
        return wrapper;
    }

    /**
     * 服务器管理列表
     * @param query
     * @return
     */
//    public PageResult<String[]> pageServer(FollowTestServerQuery query) {
//        List<FollowTestDetailVO> detailVOList = baseMapper.selectServer(query);
//        System.out.println(detailVOList.size());
//        // 用于最终结果的列表
//        List<String[]> result = new ArrayList<>();
//
//        Set<String> uniqueVpsNames = new LinkedHashSet<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            String vpsName = detail.getVpsName();
//            if (vpsName != null) {
//                uniqueVpsNames.add(vpsName);
//            }
//        }
//
//        // 表头处理
//        List<String> header = new ArrayList<>();
//        header.add("券商名称");
//        header.add("服务器名称");
//        header.add("账号数量");
//        header.add("非默认节点账号数量");
//        header.add("平台类型");
//        header.add("更新时间");
//        header.add("服务器节点");
//        header.addAll(uniqueVpsNames);
//        result.add(header.toArray(new String[0]));  // 将表头加入结果中
//
//        // 暂存每个 key 对应的速度数据
//        Map<String, Map<String, Double>> speedMap = new HashMap<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            String key = detail.getServerName() + "_" + detail.getPlatformType() + "_" + detail.getServerNode();
//            String vpsName = detail.getVpsName();
//            Integer speed = detail.getSpeed();
//
//            if (speed != null) {
//                double speedValue = speed.doubleValue();
//                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, speedValue);
//            } else {
//                // 处理 speed 为 null 的情况
//                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, 0.0); // 使用默认值 0.0
//            }
//        }
//
//        // Step 1: 按照 brokerName 分组
//        Map<String, List<FollowTestDetailVO>> brokerGroups = new HashMap<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            String brokerName = followPlatformService.getbrokerName(detail.getServerName());
//            brokerGroups.computeIfAbsent(brokerName, k -> new ArrayList<>()).add(detail);
//        }
//
//        // Step 2: 对每个 brokerGroup 按照 serverName 排序
//        List<String[]> dataRows = new ArrayList<>();
//        for (String brokerName : brokerGroups.keySet()) {
//            // 获取当前 brokerName 下的所有 serverName 对应的数据
//            List<FollowTestDetailVO> serverList = brokerGroups.get(brokerName);
//
//            // 对该 brokerName 下的数据按 serverName 排序
//            serverList.sort(Comparator.comparing(FollowTestDetailVO::getServerName));
//
//            // Step 3: 生成每行数据
//            for (FollowTestDetailVO detail : serverList) {
//                String serverName = detail.getServerName();
//                String platformType = detail.getPlatformType();
//                String serverNode = detail.getServerNode();
//
//                // 构建数据行
//                String[] dataRow = new String[7 + uniqueVpsNames.size()];
//                dataRow[0] = brokerName; // 券商名称
//                dataRow[1] = serverName; // 服务器名称
//                dataRow[2] = followTraderService.getAccountCount(serverName); // 账号数量
//
//                // 获取非默认节点账号数量
//                String defaultServerNode = serverList.stream()
//                        .filter(d -> serverName.equals(d.getServerName())
//                                && d.getIsDefaultServer() != null
//                                && d.getIsDefaultServer() == 0)
//                        .sorted(Comparator.comparing(FollowTestDetailVO::getCreateTime).reversed())
//                        .map(FollowTestDetailVO::getServerNode)
//                        .findFirst()
//                        .orElse("null"); // 默认值为 null
//                dataRow[3] = followTraderService.getDefaultAccountCount(serverName, defaultServerNode);
//
//                dataRow[4] = platformType; // 平台类型
//                dataRow[5] = String.valueOf(serverList.stream()
//                        .filter(d -> serverName.equals(d.getServerName())
//                                && d.getIsDefaultServer() != null
//                                && d.getIsDefaultServer() == 0)
//                        .sorted(Comparator.comparing(FollowTestDetailVO::getCreateTime).reversed())
//                        .map(FollowTestDetailVO::getServerUpdateTime)
//                        .findFirst()
//                        .orElse(null)); // 更新时间
//
//                dataRow[6] = serverNode; // 服务器节点
//
//                // 填充 VPS 名称的速度数据
//                int index = 7;
//                for (String vpsName : uniqueVpsNames) {
//                    Double speed = speedMap.get(serverName + "_" + platformType + "_" + serverNode).get(vpsName);
//                    dataRow[index++] = (speed != null) ? speed.toString() : "null";
//                }
//
//                dataRows.add(dataRow); // 将生成的行加入结果
//            }
//        }
//
//        // 计算分页的开始和结束索引
//        int page = query.getPage();
//        int limit = query.getLimit();
//        int start = (page - 1) * limit;
//        int end = Math.min(start + limit, dataRows.size());
//        List<String[]> paginatedDataRows = dataRows.subList(start, end);
//
//        result.addAll(paginatedDataRows);
//
//        // 返回分页结果
//        PageResult<String[]> pageResult = new PageResult<>(result, dataRows.size());
//        return pageResult;
//    }

    public PageResult<String[]> pageServer(FollowTestServerQuery query) {
        List<FollowTestDetailVO> detailVOList = baseMapper.selectServer(query);
        // 用于最终结果的列表
        List<String[]> result = new ArrayList<>();

        Set<String> uniqueVpsNames = new LinkedHashSet<>();
        for (FollowTestDetailVO detail : detailVOList) {
            String vpsName = detail.getVpsName();
            if (vpsName != null) {
                uniqueVpsNames.add(vpsName);
            }
        }
        List<String> header = new ArrayList<>();
        header.add("券商名称");
        header.add("服务器名称");
        header.add("平台类型");
        header.add("账号数量");
        header.add("非默认节点账号数量");
        header.add("服务器节点");
        header.add("更新时间");
        header.addAll(uniqueVpsNames);
        // 将表头转换为数组并作为固定的第一行加入结果中
        result.add(header.toArray(new String[0]));

        // 暂存每个 key 对应的速度数据
        Map<String, Map<String, Double>> speedMap = new HashMap<>();
        for (FollowTestDetailVO detail : detailVOList) {
            String key = detail.getServerName() + "_" + detail.getPlatformType() + "_" + detail.getServerNode();
            String vpsName = detail.getVpsName();
            Integer speed = detail.getSpeed();

            if (speed != null) {
                double speedValue = speed.doubleValue();
                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, speedValue);
            } else {
                // 处理 speed 为 null 的情况，例如记录日志或使用默认值
                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, 0.0); // 使用默认值 0.0
            }
        }

        List<String[]> dataRows = new ArrayList<>();
        List<Map.Entry<String, Map<String, Double>>> sortedEntries = new ArrayList<>(speedMap.entrySet());
        sortedEntries.sort(Comparator.comparing(e -> e.getKey().split("_")[0])); // 按服务器名称排序

        // 将每个字段对应的数据全量获取，通过map赋值
        // 券商名称map
        List<FollowTraderCountVO> brokerNames = followPlatformService.getBrokerNames();
        Map<String, String> brokerNameMap = new HashMap<>();
        if (ObjectUtil.isNotEmpty(brokerNames)){
            brokerNameMap = brokerNames.stream().collect(Collectors.toMap(FollowTraderCountVO::getServerName, FollowTraderCountVO::getBrokerName));
        }
        // 账号数量map
        List<FollowTraderCountVO> accountCounts = followTraderService.getAccountCounts();
        Map<String, String> accountCountMap = new HashMap<>();
        if (ObjectUtil.isNotEmpty(accountCounts)){
            accountCountMap = accountCounts.stream().collect(Collectors.toMap(FollowTraderCountVO::getServerName, FollowTraderCountVO::getAccountCount));
        }
        // 统计每个服务的节点数量
        List<FollowTraderCountVO> serverNodeCounts = followTraderService.getServerNodeCounts();
        Map<String, Integer> serverNodeCountMap = new HashMap<>();
        if (ObjectUtil.isNotEmpty(serverNodeCounts)){
            serverNodeCountMap = serverNodeCounts.stream().collect(Collectors.toMap(FollowTraderCountVO::getServerName, FollowTraderCountVO::getNodeCount));
        }
        // 统计每个服务对应的节点数量
        List<FollowTraderCountVO> defaultAccountCounts = followTraderService.getDefaultAccountCounts();
        Map<String, Integer> defaultAccountCountMap = new HashMap<>();
        if (ObjectUtil.isNotEmpty(defaultAccountCounts)){
            defaultAccountCountMap = defaultAccountCounts.stream().collect(Collectors.toMap(f -> f.getServerName() + f.getDefaultServerNode(), FollowTraderCountVO::getNodeCount));
        }

        for (Map.Entry<String, Map<String, Double>> entry : sortedEntries) {
            String key = entry.getKey();
            String[] keyParts = key.split("_");
            String serverName = keyParts[0];
            String platformType = keyParts[1];
            String serverNode = keyParts[2];

            Map<String, Double> vpsSpeeds = entry.getValue();
            String[] dataRow = new String[7 + uniqueVpsNames.size()];
            //券商名称
//            dataRow[0] = followPlatformService.getbrokerName(serverName);
            dataRow[0] = brokerNameMap.get(serverName);;
            //服务器名称
            dataRow[1] = serverName;
            //平台类型
            dataRow[2] = platformType;
            //获取账号数量
//            dataRow[2] = followTraderService.getAccountCount(serverName);
            dataRow[3] = accountCountMap.get(serverName) != null ? accountCountMap.get(serverName): "0";
            //非默认节点账号数量
            //查询该severName默认节点
            String defaultServerNode = detailVOList.stream()
                    .filter(detailVO -> serverName.equals(detailVO.getServerName())
                            && detailVO.getIsDefaultServer() != null
                            && detailVO.getIsDefaultServer() == 0)
                    .sorted(Comparator.comparing(FollowTestDetailVO::getCreateTime).reversed())
                    .map(FollowTestDetailVO::getServerNode)
                    .findFirst() // 获取最新的一条数据
                    .orElse("null"); // 如果没有符合条件的记录，返回 null

//            dataRow[3] = followTraderService.getDefaultAccountCount(serverName,defaultServerNode);
            Integer serverNodeCount = serverNodeCountMap.get(serverName) != null ? serverNodeCountMap.get(serverName) : 0;
            Integer defaultAccountCount = defaultAccountCountMap.get(serverName.concat(defaultServerNode)) != null ? defaultAccountCountMap.get(serverName.concat(defaultServerNode)) : 0;
            dataRow[4] = String.valueOf(Math.max(serverNodeCount - defaultAccountCount, 0));
            //服务器节点
            dataRow[5] = serverNode;
            //更新时间
            dataRow[6] = String.valueOf(detailVOList.stream()
                    .filter(detailVO -> serverName.equals(detailVO.getServerName())
                            && detailVO.getIsDefaultServer() != null
                            && detailVO.getIsDefaultServer() == 0)
                    .sorted(Comparator.comparing(FollowTestDetailVO::getCreateTime).reversed())
                    .map(FollowTestDetailVO::getServerUpdateTime)
                    .findFirst() // 获取最新的一条数据
                    .orElse(null));
            //vps名称
            int index = 7;
            for (String vpsName : uniqueVpsNames) {
                Double speed = vpsSpeeds.get(vpsName);
                dataRow[index++] = (speed != null) ? speed.toString() : "null";
            }

            dataRows.add(dataRow);
        }

        // 计算分页的开始和结束索引
        int page = query.getPage();
        int limit = query.getLimit();
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, dataRows.size());
        List<String[]> paginatedDataRows = dataRows.subList(start, end);
        result.addAll(paginatedDataRows);

        Map<String, List<String[]>> groupedByBrokerName = result.stream()
                .filter(row -> row != null && row.length > 0 && row[0] != null)
                .collect(Collectors.groupingBy(row -> row[0]));

        List<String[]> sortedResult = groupedByBrokerName.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing((String[] row) -> row[0])
                        .thenComparing(row -> row[1]))
                .collect(Collectors.toList());

        PageResult<String[]> pageResult = new PageResult<>(result, dataRows.size());
        return pageResult;
    }

    /**
     * 节点列表
     * @param query
     * @return
     */
    public PageResult<String[]> pageServerNode(FollowTestServerQuery query) {
        List<FollowTestDetailVO> detailVOList = baseMapper.selectServerNode(query);

        // 用于最终结果的列表
        List<String[]> result = new ArrayList<>();
        Set<String> uniqueVpsNames = new LinkedHashSet<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            String vpsName = detail.getVpsName();
//            if (vpsName != null) {
//                uniqueVpsNames.add(vpsName);
//            }
//        }
        followVpsServiceImpl.list().stream()
                .filter(vps -> vps.getName() != null
                && vps.getDeleted()==0
                && vps.getConnectionStatus() == 1)
                .forEach(vps -> uniqueVpsNames.add(vps.getName()));
        List<String> header = new ArrayList<>();
        header.add("服务器节点");
        header.add("更新测速时间");
        header.addAll(uniqueVpsNames);
        // 将表头转换为数组并作为固定的第一行加入结果中
        result.add(header.toArray(new String[0]));

        // 暂存每个 key 对应的速度数据
        Map<String, Map<String, Double>> speedMap = new HashMap<>();
        for (FollowTestDetailVO detail : detailVOList) {
            String key = detail.getServerNode();
            String vpsName = detail.getVpsName();
            Integer speed = detail.getSpeed();
            if (speed != null) {
                double speedValue = speed.doubleValue();
                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, speedValue);
            } else {
                // 处理 speed 为 null 的情况，例如记录日志或使用默认值
                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, 0.0); // 使用默认值 0.0
            }
        }

        // 构建唯一服务器节点的数据行
        List<String[]> dataRows = new ArrayList<>();
        for (String serverNode : speedMap.keySet()) {
            String[] dataRow = new String[2 + uniqueVpsNames.size()];
            dataRow[0] = serverNode;

            // 获取最新的更新时间（假设每条记录的时间不同）
            FollowTestDetailVO latestDetail = detailVOList.stream()
                    .filter(detail -> Optional.ofNullable(detail.getServerNode()).orElse("").equals(serverNode))
                    .max(Comparator.comparing(
                            FollowTestDetailVO::getServerUpdateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ))
                    .orElse(null);
//            FollowTestDetailVO latestDetail = detailVOList.stream()
//                    .filter(detail -> detail.getServerNode().equals(serverNode))
//                    .max(Comparator.comparing(
//                            FollowTestDetailVO::getServerUpdateTime,
//                            Comparator.nullsLast(Comparator.naturalOrder())
//                    ))
//                    .orElse(null);

            System.out.println(latestDetail);
            if (latestDetail != null) {
                dataRow[1] = String.valueOf(latestDetail.getServerUpdateTime());
            } else {
                dataRow[1] = "null"; // 或者设置为其他默认值
            }

            // 填充速度数据
            Map<String, Double> vpsSpeeds = speedMap.get(serverNode);
            int index = 2;
            for (String vpsName : uniqueVpsNames) {
                Double speed = vpsSpeeds != null ? vpsSpeeds.get(vpsName) : null;
                dataRow[index++] = (speed != null) ? speed.toString() : "null";
            }
            dataRows.add(dataRow);
        }

        // 计算分页的开始和结束索引
        int page = query.getPage();
        int limit = query.getLimit();
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, dataRows.size());
        List<String[]> paginatedDataRows = dataRows.subList(start, end);
        result.addAll(paginatedDataRows);

        PageResult<String[]> pageResult = new PageResult<>(result, dataRows.size());
        return pageResult;
    }


//    @Override
//    public PageResult<String[]> pageServerNode(FollowTestServerQuery query) {
//        List<FollowTestDetailVO> detailVOList = baseMapper.selectServer(query);
//
//        // 用于最终结果的列表
//        List<String[]> result = new ArrayList<>();
//        Set<String> uniqueVpsNames = new LinkedHashSet<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            uniqueVpsNames.add(detail.getVpsName());
//        }
//        List<String> header = new ArrayList<>();
//        header.add("服务器节点");
//        header.add("更新测速时间");
//        header.addAll(uniqueVpsNames);
//        // 将表头转换为数组并作为固定的第一行加入结果中
//        result.add(header.toArray(new String[0]));
//
//        // 暂存每个 key 对应的速度数据
//        Map<String, Map<String, Double>> speedMap = new HashMap<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            String key = detail.getServerNode();
//            String vpsName = detail.getVpsName();
//            Integer speed = detail.getSpeed();
//            if (speed != null) {
//                double speedValue = speed.doubleValue();
//                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, speedValue);
//            } else {
//                // 处理 speed 为 null 的情况，例如记录日志或使用默认值
//                speedMap.computeIfAbsent(key, k -> new HashMap<>()).put(vpsName, 0.0); // 使用默认值 0.0
//            }
//        }
//
//        List<String[]> dataRows = new ArrayList<>();
//        for (FollowTestDetailVO detail : detailVOList) {
//            String[] dataRow = new String[2 + uniqueVpsNames.size()];
//            String serverNode = detail.getServerNode();
//            dataRow[0] = serverNode;
//            dataRow[1] = String.valueOf(detail.getServerUpdateTime());
//            // 填充速度数据
//            Map<String, Double> vpsSpeeds = speedMap.get(serverNode);
//            int index = 2;
//            for (String vpsName : uniqueVpsNames) {
//                Double speed = vpsSpeeds != null ? vpsSpeeds.get(vpsName) : null;
//                dataRow[index++] = (speed != null) ? speed.toString() : "null";
//            }
//            dataRows.add(dataRow);
//        }
//        // 计算分页的开始和结束索引
//        int page = query.getPage();
//        int limit = query.getLimit();
//        int start = (page - 1) * limit;
//        int end = Math.min(start + limit, dataRows.size());
//        List<String[]> paginatedDataRows = dataRows.subList(start, end);
//        result.addAll(paginatedDataRows);
//
//
//        PageResult<String[]> pageResult = new PageResult<>(result, dataRows.size());
//        return pageResult;
//    }

    @Override
    public List<FollowTestDetailVO> selectServer(FollowTestServerQuery query) {
        return baseMapper.selectServer(query);
    }

    @Override
    public List<FollowTestDetailVO> selectServerNode(FollowTestServerQuery query) {
        return baseMapper.selectServerNode(query);
    }

    @Override
    public List<FollowTestDetailVO> selectServer1(FollowTestServerQuery followTestServerQuery) {
        return baseMapper.selectServer1(followTestServerQuery);
    }


    @Override
    public FollowTestDetailVO get(Long id) {
        FollowTestDetailEntity entity = baseMapper.selectById(id);
        FollowTestDetailVO vo = FollowTestDetailConvert.INSTANCE.convert(entity);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(FollowTestDetailVO vo) {
        FollowTestDetailEntity entity = FollowTestDetailConvert.INSTANCE.convert(vo);

        baseMapper.insert(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(FollowTestDetailVO vo) {
        FollowTestDetailEntity entity = FollowTestDetailConvert.INSTANCE.convert(vo);

        updateById(entity);


    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> idList) {
        removeByIds(idList);

    }


    @Override
    public void export() {
        List<FollowTestDetailExcelVO> excelList = FollowTestDetailConvert.INSTANCE.convertExcelList(list());
        transService.transBatch(excelList);
        ExcelUtils.excelExport(FollowTestDetailExcelVO.class, "测速详情", null, excelList);
    }


    @Override
    public List<FollowTestDetailVO> listServerAndVps() {
        // 查询服务器和vps清单
        LambdaQueryWrapper<FollowTestDetailEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.select(FollowTestDetailEntity::getServerName, FollowTestDetailEntity::getVpsName).orderByDesc(FollowTestDetailEntity::getServerName);
        List<FollowTestDetailEntity> list = baseMapper.selectList(wrapper);
        return FollowTestDetailConvert.INSTANCE.convertList(list);
    }


    @Override
    public void updates(FollowTestDetailVO convert) {
        //设置测试速度
        convert.setSpeed(convert.getSpeed());
        update(convert);
    }



}