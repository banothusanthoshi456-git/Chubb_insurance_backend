package com.example.claims.api;

import com.example.claims.api.dto.*;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.service.ClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimService service;
    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @PostMapping({"", "/save"})
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimResponse submit(@Valid @RequestBody SubmitClaimRequest request) {
        return service.submit(request);
    }

    @GetMapping("/{id}")
    public ClaimResponse get(@PathVariable("id") Long claimId) {
        return service.get(claimId);
    }

    /** Claimant tracking by the claim number they were given. */
    @GetMapping("/by-number/{claimNumber}")
    public ClaimResponse getByNumber(@PathVariable String claimNumber) {
        return service.getByNumber(claimNumber);
    }

    @GetMapping("/{id}/history")
    public List<ClaimEventResponse> history(@PathVariable Long id) {
        return service.history(id);
    }

    @GetMapping
    public PagedResponse<ClaimResponse> list(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) Long staffId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, staffId, unassigned, page, size);
    }

    @PutMapping("/{id}/assignment")
    public ClaimResponse assign(@PathVariable Long id, @Valid @RequestBody AssignClaimRequest request) {
        return service.assign(id, request);
    }

    @PostMapping("/{id}/decision")
    public ClaimResponse decide(@PathVariable Long id, @Valid @RequestBody DecisionRequest request) {
        return service.decide(id, request);
    }

    @PostMapping("/{id}/information")
    public ClaimResponse provideInformation(@PathVariable Long id, @Valid @RequestBody ProvideInformationRequest request) {
        return service.provideInformation(id, request);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}
