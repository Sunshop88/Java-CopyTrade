package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2024/12/23/周一 13:33
 * 外部接口 平仓查询
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderHistoryVO  implements Serializable {
    //客户端Id
    @JsonProperty(value = "ClientId")
    private Integer clientId;
    @JsonProperty(value = "Account")
    private List<AccountModelVO> account;
    @JsonProperty(value = "PlaceType")
    private List<String> placeType;
    @JsonProperty(value = "Type")
    private List<String> type;
    //平仓起始时间
    @JsonProperty(value = "CloseFrom")
    private Date closeFrom;
    // 平仓结束时间
    @JsonProperty(value = "CloseTo")
    private Date closeTo;
    //开仓起始时间
    @JsonProperty(value = "OpenFrom")
    private Date openFrom;
    //开仓结束时间
    @JsonProperty(value = "OpenTo")
    private Date openTo;
    //页码 ,
    @JsonProperty(value = "PageNumber")
    @NotEmpty(message ="页码不能为空" )
    private Integer pageNumber;
    //页面大小
    @JsonProperty(value = "PageSize")
    @NotEmpty(message ="页面大小不能为空" )
    private Integer pageSize;
    //是否从服务器下载 没有
    @JsonProperty(value = "IsFromServer")
    private Boolean isFromServer;


}
