package net.maku.followcom.service;

import net.maku.followcom.entity.FollowEntity;
import net.maku.followcom.vo.FollowInsertVO;
import net.maku.followcom.vo.FollowUpdateVO;
import net.maku.framework.mybatis.service.BaseService;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 17:25
 * 跟单从表数据
 */
public interface FollowService extends BaseService<FollowEntity> {


    Integer add(FollowInsertVO followInsertVO);


    void edit(FollowUpdateVO followUpdateVO);
    void edit(FollowEntity followEntity);


    void del(Long id);

    FollowEntity getEntityById(Long id);


}