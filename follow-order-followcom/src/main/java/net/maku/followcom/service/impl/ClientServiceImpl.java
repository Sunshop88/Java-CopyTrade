package net.maku.followcom.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.dao.ClientDao;
import net.maku.followcom.entity.ClientEntity;
import net.maku.followcom.service.ClientService;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 外部vps
 */
@Service
@AllArgsConstructor
@Slf4j
@DS("slave")
public class ClientServiceImpl extends BaseServiceImpl<ClientDao, ClientEntity> implements ClientService {

    @Override
    public void delete(List<Integer> idList) {
        removeByIds(idList);
    }
}