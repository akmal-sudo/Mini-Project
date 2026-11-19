package com.project.web.controller;

import com.project.dbms.entity.EventLog;
import com.project.dbms.repository.EventLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final EventLogRepository logRepository;
    private final List<SseEmitter> sseEmitters = new CopyOnWriteArrayList<>();

    public LogController(EventLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping
    public ResponseEntity<List<EventLog>> getLogs(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String sku,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<EventLog> logs;

        if (level != null && !level.isBlank()) {
            logs = logRepository.findByLevelOrderByCreatedAtDesc(level.toUpperCase());
        } else if (sku != null && !sku.isBlank()) {
            logs = logRepository.findBySkuOrderByCreatedAtDesc(sku);
        } else {
            logs = logRepository.findAll(pageRequest).getContent();
        }

        return ResponseEntity.ok(logs);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs() {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 minutes timeout
        sseEmitters.add(emitter);

        emitter.onCompletion(() -> sseEmitters.remove(emitter));
        emitter.onTimeout(() -> sseEmitters.remove(emitter));
        emitter.onError(e -> sseEmitters.remove(emitter));

        // Send initial heartbeat
        try {
            emitter.send(SseEmitter.event().name("INIT").data("Connected to Real-time Log Stream"));
        } catch (IOException e) {
            sseEmitters.remove(emitter);
        }

        return emitter;
    }

    public void broadcastLogEvent(EventLog logEvent) {
        for (SseEmitter emitter : sseEmitters) {
            try {
                emitter.send(SseEmitter.event().name("LOG").data(logEvent));
            } catch (Exception e) {
                sseEmitters.remove(emitter);
            }
        }
    }
}
