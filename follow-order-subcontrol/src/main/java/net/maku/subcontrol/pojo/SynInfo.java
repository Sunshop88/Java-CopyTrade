package net.maku.subcontrol.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * @author 休克柏
 */
@Data
@AllArgsConstructor
public class SynInfo implements Serializable {

    /**
     * 账号的mt4
     */
    String id;

    /**
     * 是否删除该账号的所有存量数据
     */
    Boolean removeAll;

    /**
     * 是否计算复杂指标
     */
    Boolean detail;

    /**
     * 发送者  TASK-定时同步任务SH信号，CLOSE-平仓SH信号
     */
    String from;


    public SynInfo(String id, Boolean removeAll) {
        this(id, removeAll, "CLOSE");
    }

    public SynInfo(String id, Boolean removeAll, String from) {
        this.id=id;
        this.removeAll=removeAll;
        this.from = from;
    }
}
