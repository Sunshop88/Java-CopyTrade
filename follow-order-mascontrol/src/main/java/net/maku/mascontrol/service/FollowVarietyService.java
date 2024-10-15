package net.maku.mascontrol.service;

import jakarta.servlet.http.HttpServletResponse;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
import net.maku.mascontrol.vo.FollowVarietyExcelVO;
import net.maku.mascontrol.vo.FollowVarietyVO;
import net.maku.mascontrol.query.FollowVarietyQuery;
import net.maku.mascontrol.entity.FollowVarietyEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 品种匹配
 *
 * @author LLL babamu@126.com
 * <a href="https://maku.net">MAKU</a>
 */
public interface FollowVarietyService extends BaseService<FollowVarietyEntity> {

    PageResult<FollowVarietyVO> page(FollowVarietyQuery query);

    FollowVarietyVO get(Long id);


    void save(FollowVarietyVO vo);

    void update(FollowVarietyVO vo);

    void delete(List<Long> idList);



    void export();

    void download(HttpServletResponse response)throws Exception;
    

    List<String> listSymbol();

    List<FollowVarietyExcelVO> importByExcel(MultipartFile file)throws Exception;

    List<FollowVarietyVO> getlist(String stdSymbol);

    PageResult<FollowVarietyVO> pageSmybol(FollowVarietyQuery query);

    PageResult<FollowVarietyVO> pageSmybolList(FollowVarietyQuery query);


    void exportCsv(ByteArrayOutputStream outputStream) throws IOException;



    byte[] generateCsv() throws IOException;
}