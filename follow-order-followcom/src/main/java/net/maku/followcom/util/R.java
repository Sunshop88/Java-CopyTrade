package net.maku.followcom.util;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.maku.framework.common.exception.ErrorCode;

/**
 * 响应数据
 *
 * @author zsd
 */
@Data
@Schema(description = "响应")
public class R<T> {
    @Schema(description = "编码 0表示成功，其他值表示失败")
    private boolean success = true;

    @Schema(description = "消息内容")
    private String message = "success";

    @Schema(description = "响应数据")
    private T data;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> result = new R<>();
        result.setData(data);
        return result;
    }

    public static <T> R<T> error() {
        return error(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    public static <T> R<T> error(String msg) {
        return error(false, msg);
    }

    public static <T> R<T> error(ErrorCode errorCode) {
        return error(false, errorCode.getMsg());
    }

    public static <T> R<T> error(boolean code, String msg) {
        R<T> result = new R<>();
        result.setSuccess(code);
        result.setMessage(msg);
        return result;
    }
}
