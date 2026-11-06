package com.zimo.regtech.gateway.bridge;

/**
 * 网关错误类型标签，与 api-gateway 平台 ErrType 枚举、控制台「错误类型」筛选项完全一致。
 */
public final class ErrTypes {

    public static final String NONE = "无";
    public static final String AUTH_FAIL = "鉴权失败";
    public static final String ROUTE_FAIL = "路由失败";
    public static final String LIMIT_REJECT = "限流拒绝";
    public static final String PARAM_INVALID = "参数校验失败";
    public static final String UPSTREAM_TIMEOUT = "上游响应超时";
    public static final String UPSTREAM_ERROR = "上游响应异常";

    private ErrTypes() {
    }

    /** 根据响应状态码推断错误类型 */
    public static String resolve(Integer statusCode) {
        if (statusCode == null) {
            return NONE;
        }
        if (statusCode == 401 || statusCode == 403) {
            return AUTH_FAIL;
        }
        if (statusCode == 404) {
            return ROUTE_FAIL;
        }
        if (statusCode == 400 || statusCode == 422) {
            return PARAM_INVALID;
        }
        if (statusCode == 429) {
            return LIMIT_REJECT;
        }
        if (statusCode == 504) {
            return UPSTREAM_TIMEOUT;
        }
        if (statusCode >= 500) {
            return UPSTREAM_ERROR;
        }
        return NONE;
    }
}
