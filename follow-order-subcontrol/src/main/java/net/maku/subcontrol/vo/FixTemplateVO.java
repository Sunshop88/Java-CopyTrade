package net.maku.subcontrol.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Author:  zsd
 * Date:  2025/1/23/周四 9:37
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixTemplateVO {

   private Integer templateType;
   private String vpsName;
    private  String source;
    private  String sourceRemarks;
    private  String follow;
    private  String symbol;
    private  String type;
    //漏单总数
    private   Integer num;
}
