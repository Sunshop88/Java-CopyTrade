package net.maku.followcom.service;

import net.maku.followcom.entity.FollowTestDetailEntity;
import net.maku.followcom.query.FollowTestDetailQuery;
import net.maku.followcom.query.FollowTestServerQuery;
import net.maku.followcom.query.FollowVpsQuery;
import net.maku.followcom.vo.FollowTestDetailVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    List<FollowTestDetailVO> selectServerNode(FollowTestServerQuery query);

    List<FollowTestDetailVO> selectServer1(FollowTestServerQuery followTestServerQuery);

    void importByExcel(MultipartFile file) throws Exception;

    CompletableFuture<Void> copyDefaultNode(FollowVpsQuery query);

    void uploadDefaultNode(MultipartFile file, List<Integer> vpsId);
}