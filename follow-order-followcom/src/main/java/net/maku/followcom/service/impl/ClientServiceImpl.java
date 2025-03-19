package net.maku.followcom.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.maku.followcom.convert.FollowVpsConvert;
import net.maku.followcom.dao.ClientDao;
import net.maku.followcom.entity.ClientEntity;
import net.maku.followcom.entity.FollowVpsEntity;
import net.maku.followcom.service.ClientService;
import net.maku.followcom.util.FollowConstant;
import net.maku.followcom.vo.FollowVpsVO;
import net.maku.framework.common.exception.ServerException;
import net.maku.framework.mybatis.service.impl.BaseServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 外部vps
 */
@Service
@AllArgsConstructor
@Slf4j
@DS("slave")
public class ClientServiceImpl extends BaseServiceImpl<ClientDao, ClientEntity> implements ClientService {

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public void delete(List<Integer> idList) {
        removeByIds(idList);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public Boolean insert(FollowVpsVO vo) {
        List<ClientEntity> list = this.list(new LambdaQueryWrapper<ClientEntity>().eq(ClientEntity::getIp, vo.getIpAddress()).or().eq(ClientEntity::getName, vo.getName()));
        if (ObjectUtil.isNotEmpty(list)) {
            throw new ServerException("重复名称或ip地址,请重新输入");
        }
        ClientEntity clientEntity = new ClientEntity();
        clientEntity.setName(vo.getName());
        clientEntity.setIp(vo.getIpAddress() + ":" + FollowConstant.VPS_PORT);
        save(clientEntity);
        return true;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    @Override
    public Boolean update(FollowVpsVO vo) {
        ClientEntity clientEntity = this.getById(vo.getId());
        if (ObjectUtil.notEqual(vo.getName(), clientEntity.getName())) {
            List<ClientEntity> list = this.list(new LambdaQueryWrapper<ClientEntity>().eq(ClientEntity::getName, vo.getName()));
            if (ObjectUtil.isNotEmpty(list)) {
                throw new ServerException("重复名称,请重新输入");
            }
        }
        UpdateWrapper<ClientEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id",vo.getId());
        if(ObjectUtil.isNotEmpty(vo.getName())){
            updateWrapper.set("name",vo.getName());
        }
        if(ObjectUtil.isNotEmpty(vo.getIpAddress())){
            updateWrapper.set("ip",vo.getIpAddress() + ":" + FollowConstant.VPS_PORT);
        }
        update(updateWrapper);
        return true;
    }
}