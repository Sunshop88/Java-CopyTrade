package net.maku.followcom.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import net.maku.followcom.entity.FollowTraderUserEntity;
import net.maku.followcom.query.FollowTraderUserQuery;
import net.maku.followcom.vo.FollowTraderUserVO;
import net.maku.followcom.vo.HangVpsVO;
import net.maku.followcom.vo.TraderUserStatVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 账号初始表
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowTraderUserService extends BaseService<FollowTraderUserEntity> {

    PageResult<FollowTraderUserVO> page(FollowTraderUserQuery query);

    FollowTraderUserVO get(Long id);


    void save(FollowTraderUserVO vo);

    void update(FollowTraderUserVO vo, HttpServletRequest req);

    void delete(List<Long> idList);

    void export();

    void generateCsv(ByteArrayOutputStream outputStream) throws IOException;

    void addByExcel(MultipartFile file, Long savedId);

    void updateGroup(List<Long> idList, Long group);

    void updatePasswords(List<FollowTraderUserVO> idList, String password, String confirmPassword, HttpServletRequest req) throws Exception;

    void updatePassword(FollowTraderUserVO vo, HttpServletRequest req) throws Exception;

    TraderUserStatVO getStatInfo(FollowTraderUserQuery query);

    TraderUserStatVO searchPage(@Valid FollowTraderUserQuery query);

    void hangVps(HangVpsVO hangVpsVO,HttpServletRequest request);

    void belowVps(List<Long> traderUserIds, HttpServletRequest request);
}