package com.flink.dwd.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Author:  zsd
 * Date:  2025/1/14/周二 14:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubscribeVo {
  private   Long masterId ;
    private  Integer masterAccount ;
    private  Long slaveId ;
    private   Integer slaveAccount ;
    private  String masterPlatform ;
}
