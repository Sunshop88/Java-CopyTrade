package net.maku.followcom.dao;

import net.maku.followcom.entity.UserEntity;
import net.maku.framework.mybatis.dao.BaseDao;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserDao extends BaseDao<UserEntity> {

}
