package com.zimo.regtech.user.mapper;

import com.zimo.regtech.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    User selectByUsername(@Param("username") String username);

    long countByUsername(@Param("username") String username, @Param("excludeId") Long excludeId);

    long countAll();

    long countByCondition(@Param("keyword") String keyword,
                          @Param("role") String role,
                          @Param("status") Integer status);

    List<User> selectPage(@Param("keyword") String keyword,
                          @Param("role") String role,
                          @Param("status") Integer status,
                          @Param("offset") long offset,
                          @Param("size") long size);

    int insert(User user);

    int update(User user);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateLoginSuccess(@Param("id") Long id, @Param("time") LocalDateTime time);

    int bumpTokenVersion(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
