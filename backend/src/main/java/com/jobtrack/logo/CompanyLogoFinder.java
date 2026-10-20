package com.jobtrack.logo;

import com.jobtrack.gmail.CompanyMatcher;
import com.jobtrack.gmail.JobEmailFilter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Finds a company's logo: works out its website (from what the user gave, the job link, the email
 * senders, or a public company-name lookup), then takes the best icon the site publishes for
 * itself (the large "apple-touch-icon" most sites have), falling back to Google's favicon service.
 */
@Component
public class CompanyLogoFinder {

    private static final Logger log = LoggerFactory.getLogger(CompanyLogoFinder.class);

    /** Big enough to look sharp in a 40px tile on a high-density screen. */
    static final int GOOD_SIZE = 64;
    private static final int MAX_PAGE_BYTES = 1_500_000;
    private static final int MAX_IMAGE_BYTES = 1_000_000;
    private static final int MIN_IMAGE_BYTES = 100;
    private static final int MAX_REDIRECTS = 4;
    private static final Pattern SIZES = Pattern.compile("(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    /** Hosts that are never the employer's own site: job boards, recruiting software, email providers. */
    static final Set<String> NOT_COMPANY_SITES = Set.copyOf(Stream.concat(JobEmailFilter.HIRING_DOMAINS.stream(),
            Stream.of("linkedin.com", "indeed.com", "indeedemail.com", "glassdoor.com", "kariyer.net",
                    "secretcv.com", "yenibiris.com", "eleman.net", "wellfound.com", "otta.com",
                    "welcometothejungle.com", "ziprecruiter.com", "monster.com", "talent.com", "jooble.org",
                    "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
                    "yahoo.com", "icloud.com", "me.com", "yandex.com", "yandex.ru", "proton.me", "protonmail.com",
                    "mail.ru", "gmx.com", "gmx.de", "aol.com", "sendgrid.net", "mailchimp.com", "mailgun.org",
                    "amazonses.com")).distinct().toList());
    /** Second-level labels under country domains: acme.com.tr and acme.co.uk are registered as a whole. */
    private static final Set<String> SECOND_LEVEL = Set.of("co", "com", "org", "net", "gov", "edu", "ac", "gen", "biz");

    /** What was found: the image, its type, where it came from, and the company's domain. */
    public record FoundLogo(byte[] image, String contentType, String sourceUrl, String domain) {
    }

    record IconCandidate(String url, int size) {
    }

    private record Fetched(byte[] body, String contentType, URI finalUri) {
    }

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER) // redirects are followed by hand, each one checked
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public CompanyLogoFinder(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param companyName the company's name, used for a domain lookup when nothing else helps
     * @param hints       URLs, hosts or email addresses that may point at the company's site, best first
     */
    public Optional<FoundLogo> find(String companyName, List<String> hints) {
        Set<String> domains = new LinkedHashSet<>();
        for (String hint : hints) {
            String domain = domainOf(hint);
            if (domain != null && isCompanySite(domain)) {
                domains.add(domain);
            }
        }
        if (domains.isEmpty()) {
            lookUpDomain(companyName).ifPresent(domains::add);
        }
        for (String domain : domains.stream().limit(3).toList()) {
            Optional<FoundLogo> found = fromDomain(domain);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<FoundLogo> fromDomain(String domain) {
        List<IconCandidate> candidates = new ArrayList<>();
        String siteHost = domain;
        try {
            Fetched page = fetch(URI.create("https://" + domain + "/"), MAX_PAGE_BYTES);
            if (page != null && page.contentType().contains("html")) {
                Document document = Jsoup.parse(new String(page.body(), StandardCharsets.UTF_8),
                        page.finalUri().toString());
                candidates.addAll(iconCandidates(document));
                siteHost = page.finalUri().getHost();
            }
        } catch (RuntimeException e) {
            log.debug("Couldn't read {}: {}", domain, e.getMessage());
        }
        // Most sites serve a large icon here even when the page doesn't mention it.
        candidates.add(new IconCandidate("https://" + siteHost + "/apple-touch-icon.png", 180));
        candidates.add(new IconCandidate("https://www.google.com/s2/favicons?sz=128&domain="
                + URLEncoder.encode(domain, StandardCharsets.UTF_8), 128));
        candidates.add(new IconCandidate("https://" + domain + "/favicon.ico", 16));

        FoundLogo best = null;
        int bestWidth = -1;
        for (IconCandidate candidate : candidates.stream().distinct().toList()) {
            Optional<Fetched> image = downloadImage(candidate.url());
            if (image.isEmpty()) {
                continue;
            }
            int width = widthOf(image.get().body());
            FoundLogo logo = new FoundLogo(image.get().body(), image.get().contentType(), candidate.url(), domain);
            // Unknown width (.ico, .webp): trust the size the page declared.
            if (width >= GOOD_SIZE || width < 0 && candidate.size() >= GOOD_SIZE) {
                return Optional.of(logo);
            }
            if (best == null || width > bestWidth) {
                best = logo;
                bestWidth = width;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Icons a page declares for itself, largest first. SVG is skipped (it can carry scripts). */
    static List<IconCandidate> iconCandidates(Document document) {
        List<IconCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("link[rel][href]")) {
            String rel = link.attr("rel").toLowerCase(Locale.ROOT);
            boolean appleTouch = rel.contains("apple-touch-icon");
            if (!appleTouch && !rel.matches("(.*\\s)?icon(\\s.*)?")) {
                continue;
            }
            String url = link.absUrl("href");
            String type = link.attr("type").toLowerCase(Locale.ROOT);
            if (url.isEmpty() || type.contains("svg") || url.toLowerCase(Locale.ROOT).split("\\?")[0].endsWith(".svg")) {
                continue;
            }
            int size = declaredSize(link.attr("sizes"));
            if (size < 0) {
                size = appleTouch ? 180 : 32; // apple-touch-icons are 180px unless they say otherwise
            }
            candidates.add(new IconCandidate(url, size));
        }
        candidates.sort(Comparator.comparingInt(IconCandidate::size).reversed());
        return candidates.stream().distinct().limit(4).toList();
    }

    private static int declaredSize(String sizes) {
        int best = -1;
        Matcher matcher = SIZES.matcher(sizes == null ? "" : sizes);
        while (matcher.find()) {
            best = Math.max(best, Integer.parseInt(matcher.group(1)));
        }
        return best;
    }

    /** Asks Clearbit's free company-name autocomplete for the domain of an exact name match. */
    private Optional<String> lookUpDomain(String companyName) {
        String wanted = CompanyMatcher.coreName(companyName);
        if (wanted.length() < 2) {
            return Optional.empty();
        }
        try {
            Fetched result = fetch(URI.create("https://autocomplete.clearbit.com/v1/companies/suggest?query="
                    + URLEncoder.encode(companyName.strip(), StandardCharsets.UTF_8)), 200_000);
            if (result == null) {
                return Optional.empty();
            }
            JsonNode suggestions = jsonMapper.readTree(result.body());
            for (JsonNode suggestion : suggestions) {
                String domain = domainOf(suggestion.path("domain").asString(""));
                if (domain != null && isCompanySite(domain)
                        && CompanyMatcher.coreName(suggestion.path("name").asString("")).equals(wanted)) {
                    return Optional.of(domain);
                }
            }
        } catch (RuntimeException e) {
            log.debug("Company lookup for '{}' failed: {}", companyName, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<Fetched> downloadImage(String url) {
        try {
            Fetched image = fetch(URI.create(url), MAX_IMAGE_BYTES);
            if (image == null || image.body().length < MIN_IMAGE_BYTES) {
                return Optional.empty();
            }
            String type = imageType(image.body());
            return type == null ? Optional.empty() : Optional.of(new Fetched(image.body(), type, image.finalUri()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The image type from the file's first bytes (servers often send the wrong Content-Type), or null. */
    static String imageType(byte[] bytes) {
        if (bytes.length < 12) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        if (bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 1 && bytes[3] == 0) {
            return "image/x-icon";
        }
        return null;
    }

    /** Pixel width for PNG/JPEG/GIF, or -1 when Java can't read the format. */
    static int widthOf(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            return image == null ? -1 : image.getWidth();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    /** "https://careers.acme.com/x", "Jobs <jobs@mail.acme.com.tr>" or "acme.io" -> the registered domain. */
    static String domainOf(String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        String text = hint.strip().toLowerCase(Locale.ROOT);
        String host;
        if (!text.contains("://") && text.contains("@")) {
            host = CompanyMatcher.senderDomain(text);
        } else {
            try {
                host = URI.create(text.contains("://") ? text : "https://" + text).getHost();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (host == null || !host.matches("[a-z0-9.-]+") || host.matches("[0-9.]+")) {
            return null;
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return null;
        }
        int keep = labels.length >= 3 && labels[labels.length - 1].length() == 2
                && SECOND_LEVEL.contains(labels[labels.length - 2]) ? 3 : 2;
        return String.join(".", List.of(labels).subList(labels.length - keep, labels.length));
    }

    static boolean isCompanySite(String domain) {
        return NOT_COMPANY_SITES.stream().noneMatch(d -> domain.equals(d) || domain.endsWith("." + d));
    }

    /** GET over https with redirects followed by hand, refusing local/private addresses at every hop. */
    private Fetched fetch(URI uri, int maxBytes) {
        URI current = uri;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!"https".equalsIgnoreCase(current.getScheme()) || current.getHost() == null
                    || !isPublic(current.getHost())) {
                return null;
            }
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,image/*,application/json;q=0.9,*/*;q=0.5")
                    .GET()
                    .build();
            try {
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = response.body()) {
                    int status = response.statusCode();
                    if (status >= 300 && status < 400) {
                        Optional<String> location = response.headers().firstValue("Location");
                        if (location.isEmpty()) {
                            return null;
                        }
                        current = current.resolve(location.get());
                        continue;
                    }
                    if (status >= 400) {
                        return null;
                    }
                    byte[] bytes = body.readNBytes(maxBytes);
                    String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    return new Fetched(bytes, type, current);
                }
            } catch (IOException | IllegalArgumentException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private static boolean isPublic(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
