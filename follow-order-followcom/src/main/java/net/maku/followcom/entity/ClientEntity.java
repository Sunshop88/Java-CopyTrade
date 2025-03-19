package net.maku.followcom.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 外部VPS
 */

@Data
@TableName("client")
public class ClientEntity implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	private Integer id;

	/**
	* 名称
	*/
	@TableField(value = "name")
	private String name;

	/**
	* ip
	*/
	@TableField(value = "ip")
	private String ip;

}