package com.miniapi.router.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.miniapi.router.core.exception.RouterException;
import com.miniapi.router.saas.context.TenantContext;
import com.miniapi.router.saas.dto.request.TenantCreateRequest;
import com.miniapi.router.saas.dto.response.PageResult;
import com.miniapi.router.saas.entity.TenantDO;
import com.miniapi.router.saas.mapper.TenantMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 租户管理服务
 * <p>
 * 提供租户的创建、查询、更新和删除功能。
 * 租户是系统中的顶级隔离单元，每个租户拥有独立的配额、用户、API Key 配置和路由规则。
 * </p>
 */
@Service
public class TenantService {

    private final TenantMapper tenantMapper;  // 租户 Mapper，用于数据访问

    public TenantService(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    /**
     * 创建租户
     * <p>
     * 创建前检查租户编码唯一性，创建后返回租户信息。
     * </p>
     *
     * @param req 租户创建请求
     * @return 创建后的租户信息
     * @throws RouterException 当租户编码已存在时抛出 409
     */
    public Map<String, Object> create(TenantCreateRequest req) {
        // 检查租户编码是否已存在
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantDO::getTenantCode, req.getTenantCode());
        if (tenantMapper.selectCount(wrapper) > 0) {
            throw new RouterException("DUPLICATE_RESOURCE", "租户编码已存在", 409);
        }
        TenantDO tenant = new TenantDO();
        tenant.setTenantCode(req.getTenantCode());
        tenant.setTenantName(req.getTenantName());
        tenant.setPlan(req.getPlan());
        tenant.setQuotaLimit(req.getQuotaLimit());
        tenant.setQuotaUsed(0L);
        tenant.setQuotaResetDay(1);
        tenant.setMaxRps(req.getMaxRps());
        tenant.setStatus(1);
        // 解析过期时间字符串
        if (req.getExpiresAt() != null) {
            tenant.setExpiresAt(LocalDateTime.parse(req.getExpiresAt().replace("Z", ""),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        tenantMapper.insert(tenant);
        return toResponse(tenant);
    }

    /**
     * 分页查询租户列表
     * <p>
     * 支持按关键词搜索（匹配租户名称或编码）以及按状态和套餐过滤。
     * </p>
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param keyword  搜索关键词（可选，匹配租户名称或编码）
     * @param status   状态过滤（可选）
     * @param plan     套餐过滤（可选）
     * @return 分页结果
     */
    public PageResult<Map<String, Object>> list(int page, int pageSize, String keyword, Integer status, String plan) {
        LambdaQueryWrapper<TenantDO> wrapper = new LambdaQueryWrapper<>();
        // 关键词搜索：同时匹配租户名称和编码
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(TenantDO::getTenantName, keyword)
                    .or().like(TenantDO::getTenantCode, keyword));
        }
        if (status != null) wrapper.eq(TenantDO::getStatus, status);
        if (plan != null) wrapper.eq(TenantDO::getPlan, plan);
        wrapper.orderByDesc(TenantDO::getCreatedAt);

        Page<TenantDO> p = new Page<>(page, pageSize);
        Page<TenantDO> result = tenantMapper.selectPage(p, wrapper);
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, pageSize);
    }

    /**
     * 更新租户信息
     * <p>
     * 仅更新请求中非空的字段，支持部分更新。
     * </p>
     *
     * @param id  租户ID
     * @param req 更新请求
     * @return 更新后的租户信息
     * @throws RouterException 当租户不存在时抛出 404
     */
    public Map<String, Object> update(Long id, TenantCreateRequest req) {
        TenantDO tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new RouterException("RESOURCE_NOT_FOUND", "租户不存在", 404);
        }
        // 逐字段条件更新
        if (req.getTenantName() != null) tenant.setTenantName(req.getTenantName());
        if (req.getPlan() != null) tenant.setPlan(req.getPlan());
        if (req.getQuotaLimit() != null) tenant.setQuotaLimit(req.getQuotaLimit());
        if (req.getMaxRps() != null) tenant.setMaxRps(req.getMaxRps());
        if (req.getExpiresAt() != null) {
            tenant.setExpiresAt(LocalDateTime.parse(req.getExpiresAt().replace("Z", ""),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        tenantMapper.updateById(tenant);
        return toResponse(tenant);
    }

    /**
     * 删除租户
     *
     * @param id 租户ID
     */
    public void delete(Long id) {
        tenantMapper.deleteById(id);
    }

    /**
     * 将租户 DO 对象转换为响应 Map
     *
     * @param tenant 租户 DO 对象
     * @return 响应 Map
     */
    private Map<String, Object> toResponse(TenantDO tenant) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", tenant.getId());
        m.put("tenant_code", tenant.getTenantCode());
        m.put("tenant_name", tenant.getTenantName());
        m.put("plan", tenant.getPlan());
        m.put("quota_limit", tenant.getQuotaLimit());
        m.put("quota_used", tenant.getQuotaUsed());
        m.put("max_rps", tenant.getMaxRps());
        m.put("status", tenant.getStatus());
        m.put("expires_at", tenant.getExpiresAt());
        m.put("created_at", tenant.getCreatedAt());
        return m;
    }
}
