package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;

/**
 * 外部服务器
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */

@Data
@TableName("server")
public class ServerEntity {
	/**
	* ID
	*/
	@TableId(type = IdType.AUTO)
	@TableField(value = "id")
	private Integer id;

	/**
	* 外部平台ID
	*/
	@TableField(value = "platformId")
	private Integer platformId;

	/**
	* 服务器节点端口
	*/
	@TableField(value = "port")
	private Integer port;

	/**
	* 服务器节点ip
	*/
	@TableField(value = "host")
	private String host;

}