package com.zimo.regtech.kb.controller;

import com.zimo.regtech.common.result.PageResult;
import com.zimo.regtech.common.result.R;
import com.zimo.regtech.kb.entity.ArticleLog;
import com.zimo.regtech.kb.entity.LegalArticle;
import com.zimo.regtech.kb.model.KbRequests;
import com.zimo.regtech.kb.service.KbService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final KbService kbService;

    @GetMapping
    public R<PageResult<LegalArticle>> page(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String jurisdiction,
                                            @RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageResult.of(kbService.articles(keyword, jurisdiction, current, size)));
    }

    /** 最近的条文变更动态（新增 / 更新 / 删除） */
    @GetMapping("/changes")
    public R<List<ArticleLog>> changes(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(kbService.articleChanges(limit));
    }

    @GetMapping("/{id}")
    public R<LegalArticle> detail(@PathVariable Long id) {
        return R.ok(kbService.article(id));
    }

    @PostMapping
    public R<LegalArticle> create(@Valid @RequestBody KbRequests.ArticleForm form) {
        return R.ok(kbService.createArticle(form));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody KbRequests.ArticleForm form) {
        kbService.updateArticle(id, form);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        kbService.deleteArticle(id);
        return R.ok();
    }
}
