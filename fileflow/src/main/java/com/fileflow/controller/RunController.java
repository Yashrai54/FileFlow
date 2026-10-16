package com.fileflow.controller;

import com.fileflow.dto.RunResponse;
import com.fileflow.service.RunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/jobs/{jobId}/runs")
public class RunController {
    private final RunService runService;

    public RunController(RunService runService) {
        this.runService = runService;
    }

    @PostMapping
    public ResponseEntity<RunResponse> trigger(@PathVariable Long jobId) {
        RunResponse run = runService.trigger(jobId);
        return ResponseEntity
                .created(URI.create("/api/jobs/" + jobId + "/runs/" + run.id()))
                .body(run);
    }

    @GetMapping
    public List<RunResponse> listForJob(@PathVariable Long jobId) {
        return runService.listForJob(jobId);
    }
}
