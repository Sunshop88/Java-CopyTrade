package net.maku.mascontrol.entity;

import lombok.Data;

import java.util.List;

@Data
public class MeasureRequestEntity {
    private List<String> servers;
    private List<String> vps;
}
