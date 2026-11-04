package com.zimo.regtech.kb.controller;

import com.zimo.regtech.common.result.PageResult;
import com.zimo.regtech.common.result.R;
import com.zimo.regtech.kb.entity.DiffRecord;
import com.zimo.regtech.kb.model.KbRequests;
import com.zimo.regtech.kb.service.DiffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 法规差异对比：单对精读 + 多地域 / 多场景交叉对比。
 */
@RestController
@RequestMapping("/diff")
@RequiredArgsConstructor
public class DiffController {

    private final DiffService diffService;

    /** 对比两条法规并留痕 */
    @PostMapping("/compare")
    public R<Map<String, Object>> compare(@RequestBody KbRequests.DiffForm form) {
        return R.ok(diffService.compare(form.getLeftId(), form.getRightId()));
    }

    /** 可选法域（含条文数量） */
    @GetMapping("/jurisdictions")
    public R<List<Map<String, Object>>> jurisdictions() {
        return R.ok(diffService.jurisdictions());
    }

    /** 多地域 / 多场景交叉对比：{jurisdictions, scenarios, topK, explain} */
    @PostMapping("/matrix")
    public R<Map<String, Object>> matrix(@RequestBody KbRequests.MatrixForm form) {
        return R.ok(diffService.matrix(form));
    }

    @GetMapping("/records")
    public R<PageResult<DiffRecord>> records(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageResult.of(diffService.page(current, size)));
    }

    @GetMapping("/records/{id}")
    public R<DiffRecord> detail(@PathVariable Long id) {
        return R.ok(diffService.get(id));
    }

    @DeleteMapping("/records/{id}")
    public R<Void> delete(@PathVariable Long id) {
        diffService.delete(id);
        return R.ok();
    }
}
