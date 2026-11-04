package com.zimo.regtech.kb.controller;

import com.zimo.regtech.common.result.PageResult;
import com.zimo.regtech.common.result.R;
import com.zimo.regtech.kb.entity.Dataset;
import com.zimo.regtech.kb.entity.KbDocument;
import com.zimo.regtech.kb.model.KbRequests;
import com.zimo.regtech.kb.service.KbService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库：数据集、文档、检索、RAG、统计。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class KbController {

    private final KbService kbService;

    @GetMapping("/datasets")
    public R<List<Dataset>> datasets(@RequestParam(required = false) String keyword) {
        return R.ok(kbService.datasets(keyword));
    }

    @PostMapping("/datasets")
    public R<Dataset> createDataset(@Valid @RequestBody KbRequests.DatasetForm form) {
        return R.ok(kbService.createDataset(form));
    }

    @PutMapping("/datasets/{id}")
    public R<Void> updateDataset(@PathVariable Long id, @RequestBody KbRequests.DatasetForm form) {
        kbService.updateDataset(id, form);
        return R.ok();
    }

    @DeleteMapping("/datasets/{id}")
    public R<Void> deleteDataset(@PathVariable Long id) {
        kbService.deleteDataset(id);
        return R.ok();
    }

    // ---------------------------------------------------------------- 文档

    @GetMapping("/documents")
    public R<PageResult<KbDocument>> documents(@RequestParam(required = false) Long datasetId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageResult.of(kbService.documents(datasetId, keyword, current, size)));
    }

    @GetMapping("/documents/{id}")
    public R<KbDocument> document(@PathVariable Long id) {
        return R.ok(kbService.document(id));
    }

    @PostMapping("/documents")
    public R<Map<String, Object>> createDocument(@Valid @RequestBody KbRequests.DocumentForm form) {
        return R.ok(kbService.createDocument(form));
    }

    @DeleteMapping("/documents/{id}")
    public R<Void> deleteDocument(@PathVariable Long id) {
        kbService.deleteDocument(id);
        return R.ok();
    }

    // ---------------------------------------------------------------- 检索 / 问答

    @PostMapping("/search")
    public R<Map<String, Object>> search(@RequestBody KbRequests.SearchForm form) {
        return R.ok(kbService.search(form));
    }

    @PostMapping("/articles/search")
    public R<Map<String, Object>> searchArticles(@RequestBody Map<String, Object> body) {
        String query = body.get("query") == null ? "" : String.valueOf(body.get("query"));
        String jurisdiction = body.get("jurisdiction") == null ? null : String.valueOf(body.get("jurisdiction"));
        int topK = body.get("topK") == null ? 10 : Integer.parseInt(String.valueOf(body.get("topK")));
        return R.ok(kbService.searchArticles(query, jurisdiction, topK));
    }

    @PostMapping("/rag/ask")
    public R<Map<String, Object>> rag(@Valid @RequestBody KbRequests.RagForm form) {
        return R.ok(kbService.rag(form));
    }

    /** 为历史数据补齐向量（切换向量模型 / 初始化导入后执行一次） */
    @PostMapping("/reindex")
    public R<Map<String, Object>> reindex(@RequestParam(defaultValue = "2000") int limit) {
        return R.ok(kbService.reindex(limit));
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(kbService.stats());
    }
}
