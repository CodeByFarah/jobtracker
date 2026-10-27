package com.jobtrack.gmail;

import com.jobtrack.entity.GmailConnection;
import com.jobtrack.entity.EmailCategory;
import com.jobtrack.repository.GmailConnectionRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JobTrack as a multi-user product: one mailbox per account, one account per mailbox, sign-ins
 * that only connect the account that started them, switching mailboxes, checks that stop when a mailbox is
 * disconnected, a sync lease shared by all server instances, and deleting an account.
 */
@TestPropertySource(properties = {"GOOGLE_CLIENT_ID=test-client", "GOOGLE_CLIENT_SECRET=test-secret"})
class MultiUserGmailIntegrationTest extends IntegrationTestSupport {

    private static final String CALLBACK = "/api/gmail/oauth/callback";

    @MockitoBean
    GmailApiClient gmail;
    @MockitoBean
    EmailClassifier classifier;

    @Autowired
    GmailConnectionRepository connectionRepository;
    @Autowired
    JobApplicationRepository applicationRepository;
    @Autowired
    GmailConnectionService connectionService;
    @Autowired
    EmailSyncService syncService;

    @BeforeEach
    void fakeGoogle() {
        when(gmail.authorizationUrl(anyString()))
                .thenAnswer(call -> "https://accounts.google.com/o/oauth2/auth?state=" + call.getArgument(0));
        // The background check started after connecting finds nothing.
        when(gmail.listMessageIds(anyString(), anyString(), anyInt())).thenReturn(List.of());
    }

    @Test
    void aSignInOnlyConnectsTheAccountThatStartedIt() throws Exception {
        String attacker = registerUser();
        String victim = registerUser();
        SignIn attackersLink = startSignIn(attacker, "c1", mailbox());

        // The attacker's Google link, completed by someone else logged in to their own account.
        String result = finish(new SignIn(attackersLink.state(), victim, attackersLink.code()));

        assertThat(result).contains("gmail=error").contains("different JobTrack account");
        assertThat(connectionService.find(userId(attacker))).isEmpty();
        assertThat(connectionService.find(userId(victim))).isEmpty();
        verify(gmail, never()).exchangeCode("c1"); // the Google code was never even used

        // Not logged in at all: rejected too.
        postJson("/api/gmail/complete", null, Map.of("code", "c1", "state", attackersLink.state()))
                .andExpect(status().isUnauthorized());

        assertThat(finish(attackersLink)).contains("gmail=connected");
        assertThat(connectionService.find(userId(attacker))).isPresent();
    }

    @Test
    void aMailboxBelongsToOnlyOneAccount() throws Exception {
        String address = mailbox();
        String first = registerUser();
        String second = registerUser();
        assertThat(finish(startSignIn(first, "a1", address))).contains("gmail=connected");

        String location = finish(startSignIn(second, "a2", address.toUpperCase()));

        assertThat(location).contains("gmail=error").contains("another");
        assertThat(connectionService.find(userId(second))).isEmpty();
        assertThat(connectionService.find(userId(first))).map(GmailConnection::getGmailAddress).contains(address);
        verify(gmail, never()).revoke(anyString()); // the first account keeps its access
    }

    @Test
    void connectingAnotherMailboxReplacesTheOldOne() throws Exception {
        String token = registerUser();
        long userId = userId(token);
        String oldAddress = mailbox();
        String newAddress = mailbox();
        finish(startSignIn(token, "s1", oldAddress));
        Long oldId = connectionService.find(userId).orElseThrow().getId();

        assertThat(finish(startSignIn(token, "s2", newAddress))).contains("gmail=connected");

        GmailConnection now = connectionService.find(userId).orElseThrow();
        assertThat(now.getGmailAddress()).isEqualTo(newAddress);
        assertThat(now.getId()).isNotEqualTo(oldId);
        assertThat(connectionRepository.findById(oldId)).isEmpty();
        verify(gmail).revoke("refresh-s1"); // the old mailbox is no longer readable by JobTrack
        // ...and the old mailbox can now be connected by another account.
        assertThat(finish(startSignIn(registerUser(), "s3", oldAddress))).contains("gmail=connected");
    }

    @Test
    void reconnectingTheSameMailboxKeepsItsHistory() throws Exception {
        String token = registerUser();
        String address = mailbox();
        finish(startSignIn(token, "r1", address));
        Long id = connectionService.find(userId(token)).orElseThrow().getId();

        finish(startSignIn(token, "r2", address));

        assertThat(connectionService.find(userId(token)).orElseThrow().getId()).isEqualTo(id);
        verify(gmail, never()).revoke(anyString());
    }

    @Test
    void disconnectingDuringACheckStopsIt() throws Exception {
        String token = registerUser();
        long userId = userId(token);
        createApplication(token, createCompany(token, "Acme"), "Engineer", "APPLIED");
        finish(startSignIn(token, "d1", mailbox()));
        awaitIdle(userId);

        when(gmail.listMessageIds(eq("access-d1"), anyString(), anyInt())).thenReturn(List.of("m1", "m2"));
        when(gmail.getMessage("access-d1", "m1")).thenReturn(new GmailMessage("m1", "jobs@acme.io", "Acme update",
                Instant.parse("2026-10-02T09:00:00Z"), "Your application"));
        when(gmail.getMessage("access-d1", "m2")).thenReturn(new GmailMessage("m2", "jobs@acme.io", "Acme again",
                Instant.parse("2026-10-01T09:00:00Z"), "Your application"));
        when(classifier.classify(any(), any())).thenAnswer(call -> {
            connectionService.disconnect(userId); // the user disconnects while the AI reads the first email
            return new EmailClassification(null, EmailCategory.OTHER, 0.9, "Update.");
        });

        syncService.syncNow(userId);

        verify(classifier, times(1)).classify(any(), any());
        assertThat(connectionService.find(userId)).isEmpty();
    }

    @Test
    void onlyOneServerChecksAMailboxAtATime() throws Exception {
        String token = registerUser();
        long userId = userId(token);
        finish(startSignIn(token, "l1", mailbox()));
        awaitIdle(userId);
        clearInvocations(gmail); // forget the check that connecting started
        GmailConnection connection = connectionService.find(userId).orElseThrow();
        Instant now = Instant.now();
        // Another instance holds the lease.
        assertThat(connectionRepository.claimSync(connection.getId(), now, now.plusSeconds(600))).isEqualTo(1);

        syncService.syncNow(userId);

        verify(gmail, never()).listMessageIds(eq("access-l1"), anyString(), anyInt());
        assertThat(syncService.state(userId).syncing()).isTrue();
        connectionRepository.finishSync(connection.getId(), null);
        assertThat(syncService.state(userId).syncing()).isFalse();
    }

    @Test
    void deletingAnAccountRemovesAllItsDataAndOnlyItsData() throws Exception {
        String token = registerUser();
        long userId = userId(token);
        createApplication(token, createCompany(token, "Acme"), "Engineer", "APPLIED");
        finish(startSignIn(token, "x1", mailbox()));
        String other = registerUser();
        createApplication(other, createCompany(other, "Globex"), "Designer", "APPLIED");

        postJson("/api/users/me/delete", token, Map.of("password", "wrong-password")).andExpect(status().isBadRequest());
        postJson("/api/users/me/delete", token, Map.of("password", "password123")).andExpect(status().isNoContent());

        assertThat(applicationRepository.countByUserId(userId)).isZero();
        assertThat(connectionService.find(userId)).isEmpty();
        verify(gmail).revoke("refresh-x1");
        getJson("/api/applications", other).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- helpers ---------------------------------------------------------------------------

    private record SignIn(String state, String token, String code) {
    }

    private SignIn startSignIn(String token, String code, String address) throws Exception {
        when(gmail.exchangeCode(code)).thenReturn(new GmailApiClient.Tokens("access-" + code, "refresh-" + code, 3600));
        when(gmail.emailAddress("access-" + code)).thenReturn(address);
        MvcResult result = getJson("/api/gmail/connect-url", token).andExpect(status().isOk()).andReturn();
        String url = jsonMapper.readTree(result.getResponse().getContentAsString()).get("url").asString();
        return new SignIn(url.substring(url.indexOf("state=") + 6), token, code);
    }

    /**
     * Google -> backend callback -> frontend /gmail/done, which posts the code and state as the
     * logged-in user. Returns "gmail=connected" or "gmail=error: <message>".
     */
    private String finish(SignIn signIn) throws Exception {
        String location = mockMvc.perform(get(CALLBACK).param("code", signIn.code()).param("state", signIn.state()))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        Map<String, String> query = UriComponentsBuilder.fromUriString(location).build().getQueryParams().toSingleValueMap();
        assertThat(location).contains("/gmail/done");
        MvcResult completed = postJson("/api/gmail/complete", signIn.token(),
                Map.of("code", decode(query.get("code")), "state", decode(query.get("state")))).andReturn();
        return completed.getResponse().getStatus() == 200
                ? "gmail=connected"
                : "gmail=error: " + jsonMapper.readTree(completed.getResponse().getContentAsString()).get("message").asString();
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private long userId(String token) throws Exception {
        return json(getJson("/api/users/me", token)).get("id").asLong();
    }

    private static String mailbox() {
        return "person-" + UUID.randomUUID() + "@gmail.com";
    }

    /** Waits for the check that connecting starts in the background. */
    private void awaitIdle(long userId) throws InterruptedException {
        for (int i = 0; i < 100 && syncService.state(userId).syncing(); i++) {
            Thread.sleep(50);
        }
        assertThat(syncService.state(userId).syncing()).isFalse();
    }

}
