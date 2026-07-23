package com.example.eventledger.gateway;

import com.example.eventledger.contract.v1.EventRequest;
import com.example.eventledger.contract.v1.EventResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {
  private final EventProcessingService service;
  public EventController(EventProcessingService service) { this.service = service; }

  @PostMapping
  public ResponseEntity<EventResponse> submit(@Valid @RequestBody EventRequest request) {
    EventProcessingService.ProcessingResult result = service.submit(request);
    EventResponse body = response(result.event(), result.replay());
    if (result.replay()) return ResponseEntity.ok().header("Idempotent-Replay", "true").body(body);
    return ResponseEntity.created(URI.create("/events/" + request.eventId())).body(body);
  }
  @GetMapping("/{eventId}")
  public EventResponse get(@PathVariable String eventId) { return response(service.get(eventId), false); }
  @GetMapping
  public List<EventResponse> list(@RequestParam("account") String account, @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
    return service.list(account, PageRequest.of(page, size)).stream().map(event -> response(event, false)).toList();
  }
  static EventResponse response(EventEntity e, boolean replay) {
    return new EventResponse(e.getEventId(), e.getAccountId(), e.getType(), e.getAmount(), e.getCurrency(),
        e.getEventTimestamp(), e.getStatus().name(), e.getReceivedAt(), e.getAppliedAt(), e.getAttemptCount(), replay, e.getFingerprint());
  }
}
