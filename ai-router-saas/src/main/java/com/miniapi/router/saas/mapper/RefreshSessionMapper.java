package com.miniapi.router.saas.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.miniapi.router.saas.entity.RefreshSessionDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface RefreshSessionMapper extends BaseMapper<RefreshSessionDO> {

    default RefreshSessionDO selectByTokenHash(String tokenHash) {
        return selectOne(new LambdaQueryWrapper<RefreshSessionDO>()
                .eq(RefreshSessionDO::getTokenHash, tokenHash));
    }

    default int revokeAllActiveForUser(Long userId, LocalDateTime revokedAt) {
        return update(null, new LambdaUpdateWrapper<RefreshSessionDO>()
                .eq(RefreshSessionDO::getUserId, userId)
                .isNull(RefreshSessionDO::getRevokedAt)
                .set(RefreshSessionDO::getRevokedAt, revokedAt));
    }
}
