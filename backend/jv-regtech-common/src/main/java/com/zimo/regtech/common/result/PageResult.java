package com.zimo.regtech.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页出参（与前端约定的表格结构）。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long pages;
    private long current;
    private long size;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        return of(Page.of(records, total, current, size));
    }
}
