package com.example.claims.api;

import com.example.claims.api.dto.ClaimResponse;
import com.example.claims.service.ClaimService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/claimants")
public class ClaimantController {
    private final ClaimService service;

    public ClaimantController(ClaimService service) {
        this.service = service;
    }

    @GetMapping("/{id}/claims")
    public List<ClaimResponse> claims(@PathVariable("id") Long claimantId) {
        return service.claimsForClaimant(claimantId);
    }
}
