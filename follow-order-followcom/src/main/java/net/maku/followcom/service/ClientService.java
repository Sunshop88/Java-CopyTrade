package net.maku.followcom.service;

import jakarta.servlet.http.HttpServletRequest;
import net.maku.followcom.entity.ClientEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 外部vps
 */
public interface ClientService extends BaseService<ClientEntity> {


}