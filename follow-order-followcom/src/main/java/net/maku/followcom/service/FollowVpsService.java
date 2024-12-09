package net.maku.followcom.service;

import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.vo.FollowVpsInfoVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.vo.FollowVpsVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * vps列表
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowVpsService extends BaseService<FollowVpsEntity> {

    PageResult<FollowVpsVO> page(FollowVpsQuery query);

    FollowVpsVO get(Long id);

    Boolean save(FollowVpsVO vo);

    void update(FollowVpsVO vo);

    void delete(List<Integer> idList);

    void export();

    List<FollowVpsVO> listByVps();

    List<FollowVpsEntity> listByVpsName(List<String> vps);

    void transferVps(Integer oldId, HttpServletRequest req);

    void startNewVps(Integer newId, HttpServletRequest req);

    FollowVpsEntity select(String vpsName);

    FollowVpsInfoVO getFollowVpsInfo(FollowTraderService followTraderService,Long userId);

    List<List<BigDecimal>> getStatByVpsId(Integer vpsId, Long traderId, FollowTraderService followTraderService);

    void updateStatus(FollowVpsEntity vpsEntity);

    FollowVpsEntity getVps(String ip);

}