package com.example.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.claims.repository.ClaimantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.UUID;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end through HTTP, validation, service, JPA and the in-memory database. */
@SpringBootTest
@AutoConfigureMockMvc
class ClaimsApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClaimantRepository claimants;

    private JsonNode postJson(String url, String body, int expected) throws Exception {
        MvcResult r = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expected)).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }
    private JsonNode putJson(String url, String body, int expected) throws Exception {
        MvcResult r = mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is(expected)).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }
    private JsonNode getJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString());
    }
    private long newOfficer() throws Exception {
        String n = UUID.randomUUID().toString().substring(0, 8);
        return postJson("/api/staff", "{\"fullName\":\"Officer " + n + "\",\"email\":\"" + n + "@example.com\"}", 201).get("id").asLong();
    }
    private JsonNode submit(String email, String amount, String market) throws Exception {
        return postJson("/api/claims", """
            {"claimantName":"Test","claimantEmail":"%s","claimantPhone":"+91-1","market":"%s",
             "claimType":"Motor","claimedAmount":%s,"description":"d"}""".formatted(email, market, amount), 201);
    }

    @Test
    void fullLifecycleWithMoreInfoLoopHistoryAndReporting() throws Exception {
        long officer = newOfficer();
        String email = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode claim = submit(email, "1000.00", "Singapore");
        long id = claim.get("id").asLong();
        assertEquals("SUBMITTED", claim.get("status").asText());

        // claimant tracks by claim number; staff see it in the unassigned queue
        assertEquals(id, getJson("/api/claims/by-number/" + claim.get("claimNumber").asText().toLowerCase()).get("id").asLong());
        boolean queued = false;
        for (JsonNode c : getJson("/api/claims?unassigned=true&size=100").get("content")) queued |= c.get("id").asLong() == id;
        assertTrue(queued);

        // pick up
        assertEquals("UNDER_REVIEW", putJson("/api/claims/" + id + "/assignment", "{\"staffId\":" + officer + "}", 200).get("status").asText());

        // officer asks for more information; approving now is blocked
        postJson("/api/claims/" + id + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"REQUEST_MORE_INFO\",\"notes\":\"need photos\"}", 200);
        postJson("/api/claims/" + id + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"APPROVE\",\"approvedAmount\":500,\"notes\":\"n\"}", 409);

        // wrong claimant refused, right claimant sends info
        postJson("/api/claims/" + id + "/information", "{\"claimantEmail\":\"other@example.com\",\"message\":\"x\"}", 403);
        assertEquals("UNDER_REVIEW", postJson("/api/claims/" + id + "/information",
            "{\"claimantEmail\":\"" + email + "\",\"message\":\"photos attached\"}", 200).get("status").asText());

        // approve above claimed -> 400; valid approve -> 200; settle -> 200
        postJson("/api/claims/" + id + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"APPROVE\",\"approvedAmount\":1000.01,\"notes\":\"n\"}", 400);
        JsonNode approved = postJson("/api/claims/" + id + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"APPROVE\",\"approvedAmount\":800.00,\"notes\":\"ok\"}", 200);
        assertEquals("APPROVED", approved.get("status").asText());
        assertEquals("SETTLED", postJson("/api/claims/" + id + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"SETTLE\",\"notes\":\"paid\"}", 200).get("status").asText());

        // full audit trail: submitted, assigned, info requested, info provided, approved, settled
        JsonNode history = getJson("/api/claims/" + id + "/history");
        assertEquals(6, history.size());
        assertEquals("SUBMITTED", history.get(0).get("toStatus").asText());
        assertEquals("SETTLED", history.get(5).get("toStatus").asText());

        // manager views
        JsonNode dash = getJson("/api/claims/dashboard");
        assertTrue(dash.get("totalClaims").asLong() >= 1);
        assertTrue(dash.get("claimsByStatus").has("SETTLED"));
        boolean found = false;
        for (JsonNode w : getJson("/api/staff/workload")) {
            if (w.get("staffId").asLong() == officer) {
                found = true;
                assertEquals(0, w.get("openClaims").asLong());
                assertEquals(1, w.get("claimsByStatus").get("SETTLED").asLong());
                assertFalse(w.get("avgMinutesToDecision").isNull());
            }
        }
        assertTrue(found);
    }

    @Test
    void exposureUsesApprovedAmountOnceApprovedAndExcludesClosedClaims() throws Exception {
        double before = getJson("/api/claims/dashboard").get("outstandingLiabilityExposure").asDouble();
        long officer = newOfficer();
        long open = submit("a" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "1000.00", "India").get("id").asLong();
        long approved = submit("b" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "2000.00", "India").get("id").asLong();
        long rejected = submit("c" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "5000.00", "India").get("id").asLong();
        for (long c : new long[]{approved, rejected}) putJson("/api/claims/" + c + "/assignment", "{\"staffId\":" + officer + "}", 200);
        postJson("/api/claims/" + approved + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"APPROVE\",\"approvedAmount\":1500.00,\"notes\":\"ok\"}", 200);
        postJson("/api/claims/" + rejected + "/decision",
            "{\"staffId\":" + officer + ",\"decision\":\"REJECT\",\"notes\":\"no\"}", 200);
        // open 1000 (claimed) + approved 1500 (approved amount, not 2000) + rejected 0
        double after = getJson("/api/claims/dashboard").get("outstandingLiabilityExposure").asDouble();
        assertEquals(2500.00, after - before, 0.001);
        assertTrue(open > 0);
    }

    @Test
    void validationAndErrorShape() throws Exception {
        postJson("/api/claims", "{\"claimantName\":\"\"}", 400);
        postJson("/api/claims", """
            {"claimantName":"T","claimantEmail":"t@example.com","claimantPhone":"1","market":"India",
             "claimType":"Motor","claimedAmount":99999999999999999,"description":"d"}""",
            400);   // exceeds 12.2 digits
        postJson("/api/claims", "{not json", 400);
        mvc.perform(get("/api/claims/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Claim not found"));
        mvc.perform(get("/api/claims?status=BOGUS")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/claims?size=1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/no-such-endpoint")).andExpect(status().isNotFound());
    }

    @Test
    void staffEmailUniqueAndDeactivation() throws Exception {
        String n = UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"fullName\":\"S\",\"email\":\"" + n + "@Example.com\"}";
        long id = postJson("/api/staff", body, 201).get("id").asLong();
        postJson("/api/staff", body.replace("Example", "example"), 409);
        mvc.perform(patch("/api/staff/" + id + "/active").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        long claim = submit(n + "c@example.com", "10.00", "India").get("id").asLong();
        putJson("/api/claims/" + claim + "/assignment", "{\"staffId\":" + id + "}", 400);   // inactive officer
    }

    @Test
    void reusesExistingClaimantWhenEmailCaseDiffers() throws Exception {
        String email = "legacy" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        jdbc.update("INSERT INTO claimants (full_name, email, phone) VALUES (?, ?, ?)",
            "Existing claimant", "  " + email.toUpperCase(Locale.ROOT) + "  ", "1");
        long countBeforeSubmit = claimants.count();

        submit(email, "50.00", "India");

        assertEquals(countBeforeSubmit, claimants.count());
    }

    @Test
    void repeatedIdenticalSaveIsRejectedButDifferentClaimDetailsAreAllowed() throws Exception {
        String email = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        long countBeforeSubmit = claimants.count();
        String request = """
            {"claimantName":"Test","claimantEmail":"%s","claimantPhone":"+91-1","market":"India",
             "claimType":"Motor","claimedAmount":50.00,"description":"d"}""";

        JsonNode first = postJson("/api/claims/save", request.formatted(email), 201);
        JsonNode duplicate = postJson("/api/claims/save",
            request.formatted("  " + email.toUpperCase(Locale.ROOT) + "  "), 409);
        JsonNode differentClaim = postJson("/api/claims/save",
            request.replace("50.00", "60.00").formatted(email), 201);

        assertEquals("A claim with the same claimant email and details already exists.",
            duplicate.get("error").asText());
        assertEquals(first.get("claimantId").asLong(), differentClaim.get("claimantId").asLong());
        assertNotEquals(first.get("id").asLong(), differentClaim.get("id").asLong());
        assertEquals(countBeforeSubmit + 1, claimants.count());
    }

    @Test
    void listsClaimsByClaimantId() throws Exception {
        String email = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode first = submit(email, "50.00", "India");
        JsonNode second = submit(email, "75.00", "USA");
        long claimantId = first.get("claimantId").asLong();

        JsonNode result = getJson("/api/claimants/" + claimantId + "/claims");

        assertEquals(2, result.size());
        assertEquals(claimantId, result.get(0).get("claimantId").asLong());
        assertEquals(claimantId, result.get(1).get("claimantId").asLong());
        assertNotEquals(first.get("id").asLong(), second.get("id").asLong());
        mvc.perform(get("/api/claimants/999999/claims"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Claimant not found"));
    }

    @Test
    void normalizedClaimantEmailHasDatabaseUniqueConstraint() throws Exception {
        String email = UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        submit(email, "50.00", "India");

        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("INSERT INTO claimants (full_name, email, phone) VALUES (?, ?, ?)",
                "Duplicate", email, "1"));
    }

    @Test
    void paginationAndCombinedFilters() throws Exception {
        long officer = newOfficer();
        for (int i = 0; i < 3; i++) {
            long c = submit("p" + i + UUID.randomUUID().toString().substring(0, 6) + "@example.com", "10.00", "Hong Kong").get("id").asLong();
            putJson("/api/claims/" + c + "/assignment", "{\"staffId\":" + officer + "}", 200);
        }
        JsonNode page = getJson("/api/claims?staffId=" + officer + "&status=UNDER_REVIEW&size=2&page=0");
        assertEquals(2, page.get("content").size());
        assertEquals(3, page.get("totalElements").asLong());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(1, getJson("/api/claims?staffId=" + officer + "&status=UNDER_REVIEW&size=2&page=1").get("content").size());
        assertEquals(0, getJson("/api/claims?staffId=" + officer + "&status=APPROVED").get("content").size());
    }
}
