package com.example.claims.api;

import com.example.claims.api.dto.StaffDtos.*;
import com.example.claims.service.StaffService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService service;
    public StaffController(StaffService service) {
        this.service = service;
    }

    @GetMapping
    public List<StaffResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse create(@Valid @RequestBody CreateStaffRequest request) { return service.create(request); }

    @PatchMapping("/{id}/active")
    public StaffResponse setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        return service.setActive(id, request.active());
    }

    /** Manager view: each officer's open claims, claims per status and average time to decision. */
    @GetMapping("/workload")
    public List<WorkloadResponse> workload() { return service.workload(); }
}
