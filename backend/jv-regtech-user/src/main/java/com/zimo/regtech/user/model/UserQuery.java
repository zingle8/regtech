package com.zimo.regtech.user.model;

import lombok.Data;

@Data
public class UserQuery {

    private String keyword;
    private String role;
    private Integer status;
    private long current = 1;
    private long size = 10;

    /** MyBatis 取 #{offset} / #{limit}（JavaBean getter，必须显式声明） */
    public long getOffset() {
        return Math.max(0, (Math.max(1, current) - 1) * getLimit());
    }

    public long getLimit() {
        return Math.max(1, Math.min(size, 200));
    }
}
