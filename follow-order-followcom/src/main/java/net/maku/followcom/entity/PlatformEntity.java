package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

/**
 * 外部平台
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("platform")
public class PlatformEntity {
	/**
	* ID
	*/
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Integer id;

	/**
	* 平台名称
	*/
	@TableField(value = "name")
	private String name;

	/**
	* 默认服务器
	*/
	@TableField(value = "defaultServer")
	private String defaultServer;

	/**
	* 平台类型
	*/
	@TableField(value = "type")
	private String type;

}