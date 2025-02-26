package net.maku.followcom.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.*;
import net.maku.followcom.vo.FollowTraderAnalysisEntityVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账号数据分析表
 *
 * @author zsd
 */

@Data
@TableName("follow_trader_analysis")
public class FollowTraderAnalysisEntity {
	/**
	* mt4账号
	*/
	@TableField(value = "account")
	private String account;

	/**
	* vpsId
	*/
	@TableField(value = "vps_id")
	private Long vpsId;

	/**
	* 品种
	*/
	@TableField(value = "symbol")
	private String symbol;

	/**
	* vps名称
	*/
	@TableField(value = "vps_name")
	private String vpsName;

	/**
	* 平台服务器
	*/
	@TableField(value = "platform")
	private String platform;

	/**
	* 平台服务器id
	*/
	@TableField(value = "platform_id")
	private Integer platformId;

	/**
	* 信号源账号
	*/
	@TableField(value = "source_account")
	private String sourceAccount;

	/**
	* 信号源服务器
	*/
	@TableField(value = "source_platform")
	private String sourcePlatform;

	/**
	* 净头寸
	*/
	@TableField(value = "position")
	private BigDecimal position;

	/**
	* 总持仓手数
	*/
	@TableField(value = "lots")
	private BigDecimal lots;

	/**
	* 总订单数
	*/
	@TableField(value = "num")
	private BigDecimal num;

	/**
	* 总盈利
	*/
	@TableField(value = "profit")
	private BigDecimal profit;

	/**
	* 多仓订单量
	*/
	@TableField(value = "buy_num")
	private BigDecimal buyNum;

	/**
	* 多仓手数
	*/
	@TableField(value = "buy_lots")
	private BigDecimal buyLots;

	/**
	* 多仓盈利
	*/
	@TableField(value = "buy_profit")
	private BigDecimal buyProfit;

	/**
	* 空仓订单量
	*/
	@TableField(value = "sell_num")
	private BigDecimal sellNum;

	/**
	* 空仓手数
	*/
	@TableField(value = "sell_lots")
	private BigDecimal sellLots;

	/**
	* 空仓盈利
	*/
	@TableField(value = "sell_profit")
	private BigDecimal sellProfit;
	/**
	 * 账号类型
	 */
	@TableField(value = "type")
	private Integer type;

	/**
	 * 可用预付款比例
	 * */
	@TableField(value = "free_margin")
	private BigDecimal freeMargin;

	private

	@TableField(exist = false)
	List<FollowTraderAnalysisEntityVO>  symbolAnalysisDetails;
}