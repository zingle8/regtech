package com.zimo.regtech.graph.controller;

import com.zimo.regtech.common.result.R;
import com.zimo.regtech.graph.entity.KgEdge;
import com.zimo.regtech.graph.entity.KgNode;
import com.zimo.regtech.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    /** 图谱视图数据（ECharts） */
    @GetMapping("/graph")
    public R<Map<String, Object>> graph(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(defaultValue = "300") int limit) {
        return R.ok(graphService.graph(keyword, category, limit));
    }

    @GetMapping("/nodes")
    public R<List<KgNode>> nodes(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String category) {
        return R.ok(graphService.nodes(keyword, category));
    }

    @GetMapping("/nodes/{id}")
    public R<KgNode> node(@PathVariable Long id) {
        return R.ok(graphService.node(id));
    }

    @PostMapping("/nodes")
    public R<KgNode> createNode(@RequestBody KgNode node) {
        return R.ok(graphService.createNode(node));
    }

    @PutMapping("/nodes/{id}")
    public R<Void> updateNode(@PathVariable Long id, @RequestBody KgNode node) {
        graphService.updateNode(id, node);
        return R.ok();
    }

    @DeleteMapping("/nodes/{id}")
    public R<Void> deleteNode(@PathVariable Long id) {
        graphService.deleteNode(id);
        return R.ok();
    }

    @PostMapping("/edges")
    public R<KgEdge> createEdge(@RequestBody KgEdge edge) {
        return R.ok(graphService.createEdge(edge));
    }

    @DeleteMapping("/edges/{id}")
    public R<Void> deleteEdge(@PathVariable Long id) {
        graphService.deleteEdge(id);
        return R.ok();
    }

    /** 从法条库自动构图（会清空现有图谱） */
    @PostMapping("/build")
    public R<Map<String, Object>> build(@RequestParam(defaultValue = "200") int limit) {
        return R.ok(graphService.build(limit));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(graphService.stats());
    }
}
