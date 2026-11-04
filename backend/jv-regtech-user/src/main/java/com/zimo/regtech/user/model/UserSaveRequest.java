package com.zimo.regtech.user.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserSaveRequest {

    @NotBlank(message = "请输入账号")
    private String username;

    /** 新增必填；编辑时留空表示不修改 */
    private String password;

    private String nickname;
    private String email;
    private String phone;
    private String role = "ANALYST";
    private Integer status = 1;
}
