package com.miniapi.router.saas.spiimpl;

import com.miniapi.router.core.domain.RequestLogMeta;
import com.miniapi.router.saas.entity.RequestLogMetaDO;
import com.miniapi.router.saas.mapper.RequestLogMetaMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MybatisLogRepositoryTest {

    @Test
    void saveWritesGeneratedIdBackToDomainObject() {
        RequestLogMetaMapper mapper = mock(RequestLogMetaMapper.class);
        when(mapper.insert(any(RequestLogMetaDO.class))).thenAnswer(invocation -> {
            RequestLogMetaDO inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return 1;
        });
        RequestLogMeta meta = new RequestLogMeta();

        new MybatisLogRepository(mapper).save(meta);

        assertThat(meta.getId()).isEqualTo(42L);
    }
}
