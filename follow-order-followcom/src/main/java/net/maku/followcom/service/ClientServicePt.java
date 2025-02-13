package net.maku.followcom.service;

import net.maku.followcom.entity.ClientEntity;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 外部vps-pt
 */
public interface ClientServicePt extends BaseService<ClientEntity> {


    void delete(List<Integer> idList);


    Boolean update(FollowVpsVO vo);

    Boolean insert(FollowVpsVO vo);
}