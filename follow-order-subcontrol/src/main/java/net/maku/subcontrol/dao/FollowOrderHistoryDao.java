package net.maku.subcontrol.dao;

import net.maku.framework.mybatis.dao.BaseDao;
import net.maku.subcontrol.entity.FollowOrderHistoryEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 所有MT4账号的历史订单
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
@Mapper
public interface FollowOrderHistoryDao extends BaseDao<FollowOrderHistoryEntity> {

    @Insert({
            " <script>",
            "insert into follow_order_history (",
            "order_no,",
            "type,",
            "open_time,",
            "close_time,",
            "size,",
            "symbol,",
            "open_price,",
            "close_price,",
            "sl,",
            "tp,",
            "commission,",
            "taxes,",
            "swap,",
            "profit,",
            "comment,",
            "magic,",
            "trader_id,",
            "account,",
            "version,",
            "deleted,",
            "creator,",
            "create_time,",
            "updater,",
            "update_time,",
            "placed_type",
            ") values  (",
            "#{historyEntity.orderNo},",
            "#{historyEntity.type},",
            "#{historyEntity.openTime},",
            "#{historyEntity.closeTime},",
            "#{historyEntity.size},",
            "#{historyEntity.symbol},",
            "#{historyEntity.openPrice},",
            "#{historyEntity.closePrice},",
            "#{historyEntity.sl},",
            "#{historyEntity.tp},",
            "#{historyEntity.commission},",
            "#{historyEntity.taxes},",
            "#{historyEntity.swap},",
            "#{historyEntity.profit},",
            "#{historyEntity.comment},",
            "#{historyEntity.magic},",
            "#{historyEntity.traderId},",
            "#{historyEntity.account},",
            "#{historyEntity.version},",
            "#{historyEntity.deleted},",
            "#{historyEntity.creator},",
            "#{historyEntity.createTime},",
            "#{historyEntity.updater},",
            "#{historyEntity.updateTime},",
            "#{historyEntity.placedType}",
            ")",
            "ON DUPLICATE KEY UPDATE update_time = now() , version=version+1",
            "</script>",
    })
  public   void customBatchSaveOrUpdate(@Param("historyEntity") FollowOrderHistoryEntity historyEntity);
}