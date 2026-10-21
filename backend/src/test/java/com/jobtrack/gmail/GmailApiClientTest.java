package com.jobtrack.gmail;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GmailApiClientTest {

    @Test
    void theRealSearchQueryMakesAValidUrlAndArrivesUnchanged() {
        // The exact kind of query the sync sends: braces, quotes, colons and Turkish letters.
        String query = "-in:sent newer_than:180d " + JobEmailFilter.searchClause(List.of("REV Media Group", "Acme & Co"));

        URI uri = GmailApiClient.messagesUri(query, 100, null);

        assertThat(uri.getRawQuery()).doesNotContain("{", "}", "\"", " ");
        assertThat(uri.getQuery()).startsWith("q=" + query).endsWith("&maxResults=100");
    }

    @Test
    void thePageTokenIsEncodedToo() {
        URI uri = GmailApiClient.messagesUri("is:inbox", 50, "abc+/=123");

        assertThat(uri.getRawQuery()).contains("pageToken=abc%2B%2F%3D123");
    }
}
