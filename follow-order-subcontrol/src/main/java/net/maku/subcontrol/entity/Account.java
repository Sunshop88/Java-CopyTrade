package net.maku.subcontrol.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Account {
    @JsonProperty("Id")
    private long id;

    @JsonProperty("Type")
    private int type;

}
