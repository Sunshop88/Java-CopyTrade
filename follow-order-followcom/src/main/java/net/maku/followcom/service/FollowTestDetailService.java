package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTestDetailEntity;
import net.maku.followcom.entity.FollowTraderEntity;
import net.maku.followcom.query.FollowTestDetailQuery;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;

import java.util.List;

/**
 * 测速详情
 *
 * @author 阿沐 babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTestDetailService extends BaseService<FollowTestDetailEntity> {

//   List <List<Object>>  page(FollowTestDetailQuery query);

    FollowTestDetailVO get(Long id);


    void save(FollowTestDetailVO vo);

    void update(FollowTestDetailVO vo);

    void delete(List<Long> idList);


    void export();


    List<FollowTestDetailVO> listServerAndVps();

    void updates(FollowTestDetailVO convert);

    PageResult<String[]> page(FollowTestDetailQuery query);

    void deleteByTestId(Integer id);

    PageResult<String[]> pageServer(FollowTestServerQuery query);

    PageResult<String[]> pageServerNode(FollowTestServerQuery query);

    List<FollowTestDetailVO> selectServer(FollowTestServerQuery query);
}