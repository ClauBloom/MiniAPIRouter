package com.miniapi.router.saas.dto.response;

import lombok.Data;
import java.util.List;

/**
 * 分页查询结果封装类。
 * 
 * <p>用于封装分页查询的返回结果，包含数据列表、总记录数、当前页码和每页条数。
 * 
 * @param <T> 列表元素的数据类型
 */
@Data
public class PageResult<T> {
    private List<T> list;     // 当前页的数据列表
    private long total;       // 符合查询条件的总记录数
    private int page;         // 当前页码
    private int pageSize;     // 每页条数

    /**
     * 构造分页结果。
     *
     * @param list     当前页数据列表
     * @param total    总记录数
     * @param page     当前页码
     * @param pageSize 每页条数
     */
    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
