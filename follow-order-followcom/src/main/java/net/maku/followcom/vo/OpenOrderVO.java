package net.maku.followcom.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Author:  zsd
 * Date:  2025/2/14/周五 15:50
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenOrderVO implements Serializable {
    //客户端Id
    @JsonProperty(value = "ClientId")
    private Integer clientId;
    @JsonProperty(value = "Account")
    private List<AccountModelVO> account;
    @JsonProperty(value = "PlaceType")
    private List<String> placeType;
    @JsonProperty(value = "Type")
    private List<String> type;
}
