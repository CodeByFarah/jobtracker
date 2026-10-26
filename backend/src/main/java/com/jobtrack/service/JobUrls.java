package com.jobtrack.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Compares job posting links: the same posting, shared from different places, is one job. */
public final class JobUrls {

    private JobUrls() {
    }

    /**
     * A comparable form of a job link: lower-case scheme and host without "www.", no #fragment, no
     * trailing slash, and no tracking parameters (utm_*, trk, ref, source…), which change with
     * where the link was shared from. Null for blank input.
     */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String text = url.strip();
        try {
            URI uri = new URI(text);
            if (uri.getHost() == null) {
                return text.toLowerCase(Locale.ROOT);
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
            String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                    .filter(param -> !param.isBlank() && !isTracking(param.split("=", 2)[0]))
                    .sorted()
                    .collect(Collectors.joining("&"));
            return host + path + (query.isEmpty() ? "" : "?" + query);
        } catch (URISyntaxException e) {
            return text.toLowerCase(Locale.ROOT);
        }
    }

    private static boolean isTracking(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return key.startsWith("utm_") || key.equals("trk") || key.equals("trackingid") || key.equals("refid")
                || key.equals("ref") || key.equals("src") || key.equals("source") || key.equals("gh_src")
                || key.equals("lever-source") || key.equals("lever-origin") || key.equals("ebp")
                || key.equals("currentjobid") || key.equals("from") || key.equals("fbclid") || key.equals("gclid");
    }
}
