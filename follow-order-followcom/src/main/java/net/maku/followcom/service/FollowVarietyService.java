package net.maku.followcom.service;

import jakarta.servlet.http.HttpServletResponse;
import net.maku.followcom.entity.FollowVarietyEntity;
import net.maku.followcom.query.FollowVarietyQuery;
import net.maku.followcom.vo.FollowVarietyVO;
import net.maku.framework.common.utils.PageResult;
import net.maku.framework.mybatis.service.BaseService;
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

    PageResult<FollowVarietyVO> pageSmybol(FollowVarietyQuery query);

    PageResult<FollowVarietyVO> pageSmybolList(FollowVarietyQuery query);

    void exportCsv(ByteArrayOutputStream outputStream,Integer template) throws IOException;

    void generateCsv(ByteArrayOutputStream outputStream)throws IOException;

    void addByExcel(MultipartFile file,String templateName) throws Exception;

    void importByExcel(MultipartFile file, Integer template,String templateName) throws Exception;

    List<FollowVarietyEntity> getListByTemplated(Integer templateId);

    List<FollowVarietyVO> getListByTemplate();

    void updateTemplateName(Integer template, String templateName);

    List<FollowVarietyVO> listSymbol();

    boolean deleteTemplate(List<Integer> idList);

    int getLatestTemplateId();

    boolean checkTemplate(List<Integer> idList);

    Boolean updateCache(Integer template);
}