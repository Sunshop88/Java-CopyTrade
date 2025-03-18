/*
 Navicat Premium Dump SQL

 Source Server         : 39.99.136.49
 Source Server Type    : MySQL
 Source Server Version : 80024 (8.0.24)
 Source Host           : 39.99.136.49:3306
 Source Schema         : follow-order-cp

 Target Server Type    : MySQL
 Target Server Version : 80024 (8.0.24)
 File Encoding         : 65001

 Date: 07/03/2025 09:23:45
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for follow_trader_user
-- ----------------------------
CREATE TABLE `follow_trader_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号',
  `password` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '密码',
  `platform_id` int NULL DEFAULT NULL COMMENT '平台id',
  `platform` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '平台服务器',
  `account_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号类型 MT4或MT5',
  `server_node` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '服务器节点',
  `group_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组别名称',
  `group_id` int NULL DEFAULT NULL COMMENT '组别id',
  `sort` tinyint NULL DEFAULT NULL COMMENT '排序 默认：1',
  `status` tinyint(1) NULL DEFAULT 0 COMMENT '挂靠状态0-未挂靠 1-已挂靠',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `version` int NULL DEFAULT NULL COMMENT '版本号',
  `deleted` tinyint NULL DEFAULT 0 COMMENT '删除标识 0：正常 1：已删除',
  `creator` bigint NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` bigint NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2386 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '账号初始表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;


-- ----------------------------
-- Table structure for follow_failure_detail
-- ----------------------------
CREATE TABLE `follow_failure_detail`  (
      `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
      `platform_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号类型 需为MT4或MT5',
      `server` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '服务器',
      `node` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '节点',
      `account` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '账号',
      `is_password` tinyint(1) NULL DEFAULT NULL COMMENT '是否修改MT4密码',
      `record_id` tinyint(1) NULL DEFAULT NULL COMMENT '记录id',
      `type` tinyint(1) NULL DEFAULT NULL COMMENT '类型 0：新增账号 1：修改密码 2：挂靠VPS',
      `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '失败原因',
      PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '失败详情表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Table structure for follow_upload_trader_user
-- ----------------------------
CREATE TABLE `follow_upload_trader_user`  (
          `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
          `upload_time` datetime NULL DEFAULT NULL COMMENT '上传时间',
          `operator` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '操作人',
          `status` tinyint(1) NULL DEFAULT NULL COMMENT '状态 0：处理中 1：处理完成',
          `upload_total` bigint NULL DEFAULT NULL COMMENT '上传数据数量',
          `success_count` bigint NULL DEFAULT NULL COMMENT '成功数量',
          `failure_count` bigint NULL DEFAULT NULL COMMENT '失败数量',
          `type` tinyint(1) NULL DEFAULT NULL COMMENT '类型 0：新增账号 1：修改密码 2：挂靠VPS',
          `version` int NULL DEFAULT NULL COMMENT '版本号',
          `deleted` tinyint NULL DEFAULT NULL COMMENT '删除标识 0：正常 1：已删除',
          `creator` bigint NULL DEFAULT NULL COMMENT '创建者',
          `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
          `updater` bigint NULL DEFAULT NULL COMMENT '更新者',
          `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
          PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 88 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '批量记录表' ROW_FORMAT = DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
-- ----------------------------
-- 增加漏单监控
-- ----------------------------
ALTER TABLE follow_vps ADD is_monitor_repair tinyint(1) NULL DEFAULT 1 COMMENT '是否监控漏单 0不监控 1监控 ' ;

ALTER TABLE follow_trader ADD cfd varchar(50) NULL  COMMENT '品种 ' ;
ALTER TABLE follow_trader ADD forex varchar(50) NULL   COMMENT '品种 ' ;
ALTER TABLE follow_trader_user MODIFY COLUMN sort INT;

--------总指令表
CREATE TABLE `follow_order_instruct` (
                                         `id` int NOT NULL AUTO_INCREMENT,
                                         `instruction_type` tinyint NOT NULL COMMENT '指令类型0-分配 1-复制',
                                         `symbol` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '品种',
                                         `type` tinyint NOT NULL COMMENT '类型 0-buy 1-sell',
                                         `min_lot_size` decimal(10,2) DEFAULT '0.00' COMMENT '手数范围开始',
                                         `max_lot_size` decimal(10,2) DEFAULT '0.00' COMMENT ' 手数范围结束',
                                         `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
                                         `total_lots` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '下单总手数',
                                         `total_orders` int NOT NULL DEFAULT '0' COMMENT '下单总订单',
                                         `interval_time` int DEFAULT '0' COMMENT '间隔时间',
                                         `true_total_lots` decimal(10,2) DEFAULT '0.00' COMMENT '实际下单总手数',
                                         `true_total_orders` int DEFAULT '0' COMMENT '实际下单总单数',
                                         `traded_lots` decimal(10,2) DEFAULT '0.00' COMMENT '成交手数',
                                         `traded_orders` int DEFAULT '0' COMMENT '成交订单',
                                         `status` tinyint DEFAULT '0' COMMENT '状态0-执行中 1-全部成功 2-存在失败',
                                         `version` int DEFAULT NULL COMMENT '版本号',
                                         `deleted` tinyint DEFAULT '0' COMMENT '删除标识  0：正常   1：已删除',
                                         `creator` bigint DEFAULT NULL COMMENT '创建者',
                                         `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                         `updater` bigint DEFAULT NULL COMMENT '更新者',
                                         `update_time` datetime DEFAULT NULL COMMENT '更新时间',
                                         `end_time` datetime DEFAULT NULL COMMENT '指令结束时间',
                                         `order_no` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '订单号',
                                         `fail_orders` int DEFAULT '0' COMMENT '失败订单数',
                                         `trader_id` int DEFAULT NULL COMMENT '用户id',
                                         PRIMARY KEY (`id`) USING BTREE,
                                         KEY `orderkey` (`order_no`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='下单总指令表';

--------订单详情表
ALTER TABLE `follow-order-cp`.`follow_order_detail`
    AUTO_INCREMENT=3042143,
    ADD COLUMN `open_price_difference` DECIMAL(16,5)   NULL     COMMENT '开仓价格差'  AFTER `comment`,
    ADD COLUMN `close_price_difference` DECIMAL(16,5)   NULL     COMMENT '平仓价格差'  AFTER `open_price_difference`;