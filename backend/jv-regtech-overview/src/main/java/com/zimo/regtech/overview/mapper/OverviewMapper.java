package com.zimo.regtech.overview.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface OverviewMapper {

    long countUsers();

    long countActiveUsers();

    long countConversations();

    long countMessages();

    long countTodayMessages();

    List<Map<String, Object>> messageTrend(@Param("days") int days);

    List<Map<String, Object>> roleDistribution();
}
