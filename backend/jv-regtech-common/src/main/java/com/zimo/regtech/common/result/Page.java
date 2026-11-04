package com.zimo.regtech.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果：COUNT + LIMIT 两条 SQL 手写实现分页，不依赖任何分页插件。
 */
@Data
public class Page<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private long pages;
    private long current;
    private long size;

    public static <T> Page<T> of(List<T> records, long total, long current, long size) {
        Page<T> page = new Page<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        page.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return page;
    }
}
