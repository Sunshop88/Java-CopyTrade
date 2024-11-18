package net.maku.followcom.service;

import net.maku.followcom.vo.FollowPlatformVO;
import net.maku.followcom.vo.FollowVpsVO;

import java.util.List;

public interface MasControlService {

    boolean insert(FollowVpsVO vo);

    boolean update(FollowVpsVO vo);

    boolean delete(List<Integer> idList);
    
    boolean insertPlatform(FollowPlatformVO vo);

    boolean deletePlatform(List<Long> idList);

    boolean updatePlatform(FollowPlatformVO vo);
}
