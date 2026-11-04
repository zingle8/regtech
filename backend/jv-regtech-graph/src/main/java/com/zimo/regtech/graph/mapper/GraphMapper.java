package com.zimo.regtech.graph.mapper;

import com.zimo.regtech.graph.entity.KgEdge;
import com.zimo.regtech.graph.entity.KgNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GraphMapper {

    List<KgNode> selectNodes(@Param("keyword") String keyword,
                             @Param("category") String category,
                             @Param("limit") int limit);

    KgNode selectNode(@Param("id") Long id);

    KgNode selectNodeByCode(@Param("code") String code);

    int insertNode(KgNode node);

    int updateNode(KgNode node);

    int deleteNode(@Param("id") Long id);

    List<KgEdge> selectEdges(@Param("limit") int limit);

    int insertEdge(KgEdge edge);

    int deleteEdge(@Param("id") Long id);

    int deleteEdgesByNode(@Param("nodeId") Long nodeId);

    long countNodes();

    long countEdges();

    List<Map<String, Object>> countByCategory();

    List<Map<String, Object>> selectArticlesForGraph(@Param("limit") int limit);

    int clearEdges();

    int clearNodes();
}
