package net.maku.followcom.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Data;
import java.io.Serializable;
import net.maku.framework.common.utils.DateUtils;

/**
 * 外部服务器
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Data
@Schema(description = "外部服务器")
public class ServerVO implements Serializable {
	private static final long serialVersionUID = 1L;

	@Schema(description = "ID")
	private Integer id;

	@Schema(description = "外部平台ID")
	private Integer platformId;

	@Schema(description = "服务器节点端口")
	private Integer port;

	@Schema(description = "服务器节点ip")
	private String host;

}