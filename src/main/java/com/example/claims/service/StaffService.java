package com.example.claims.service;

import com.example.claims.api.dto.StaffDtos.*;
import com.example.claims.domain.ClaimStatus;
import com.example.claims.domain.ClaimsStaff;
import com.example.claims.repository.ClaimRepository;
import com.example.claims.repository.ClaimsStaffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class StaffService {
    private final ClaimsStaffRepository staff;
    private final ClaimRepository claims;

    public StaffService(ClaimsStaffRepository staff, ClaimRepository claims) {
        this.staff = staff; this.claims = claims;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list() { return staff.findAll().stream().map(StaffResponse::from).toList(); }

    @Transactional
    public StaffResponse create(CreateStaffRequest req) {
        String email = req.email().trim().toLowerCase();
        if (staff.existsByEmailIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Staff email already exists");
        return StaffResponse.from(staff.save(new ClaimsStaff(req.fullName(), email)));
    }

    @Transactional
    public StaffResponse setActive(Long id, boolean active) {
        ClaimsStaff s = staff.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claims staff not found"));
        s.setActive(active);
        return StaffResponse.from(staff.save(s));
    }

    /** One row per officer: open claims, claims per status, and average minutes from submission to decision. */
    @Transactional(readOnly = true)
    public List<WorkloadResponse> workload() {
        Map<Long, Map<String, Long>> byStaff = new HashMap<>();
        for (Object[] r : claims.workloadByStaffAndStatus()) {
            byStaff.computeIfAbsent((Long) r[0], k -> emptyStatusMap()).put(((ClaimStatus) r[1]).name(), (Long) r[2]);
        }
        Map<Long, Double> avg = new HashMap<>();
        for (Object[] r : claims.averageResolutionMinutesByStaff()) avg.put((Long) r[0], (Double) r[1]);

        List<WorkloadResponse> out = new ArrayList<>();
        for (ClaimsStaff s : staff.findAll()) {
            Map<String, Long> counts = byStaff.getOrDefault(s.getId(), emptyStatusMap());
            long open = counts.get(ClaimStatus.SUBMITTED.name()) + counts.get(ClaimStatus.UNDER_REVIEW.name())
                      + counts.get(ClaimStatus.MORE_INFO_REQUIRED.name());
            out.add(new WorkloadResponse(s.getId(), s.getFullName(), s.isActive(), open, counts, avg.get(s.getId())));
        }
        return out;
    }

    private static Map<String, Long> emptyStatusMap() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (ClaimStatus st : ClaimStatus.values()) m.put(st.name(), 0L);
        return m;
    }
}
