package com.zimo.regtech.overview.controller;

import com.zimo.regtech.common.result.R;
import com.zimo.regtech.overview.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(overviewService.summary());
    }

    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(overviewService.trend(days));
    }
}
