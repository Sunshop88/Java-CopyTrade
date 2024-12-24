package net.maku.followcom.service;

import jakarta.validation.constraints.NotNull;
import net.maku.followcom.entity.SourceEntity;
import net.maku.followcom.vo.SourceInsertVO;
import net.maku.followcom.vo.SourceUpdateVO;
import net.maku.framework.mybatis.service.BaseService;

/**
 * Author:  zsd
 * Date:  2024/11/14/周四 17:25
 * 喊单从数据
 */
public interface SourceService extends BaseService<SourceEntity> {


    void add(SourceInsertVO vo);

    void edit(SourceUpdateVO vo);
    void edit(SourceEntity source);

    void del(Long id);

    SourceEntity getEntityById(@NotNull(message = "Id不能为空") Long id);


}
