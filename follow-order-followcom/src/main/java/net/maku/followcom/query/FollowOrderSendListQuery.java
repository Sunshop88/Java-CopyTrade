package net.maku.followcom.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.maku.framework.common.query.Query;

/**
 * 下单列表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Schema(description = "下单列表")
public class FollowOrderSendListQuery extends Query {

}