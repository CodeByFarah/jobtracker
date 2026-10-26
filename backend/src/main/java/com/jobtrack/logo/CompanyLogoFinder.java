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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
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
            Stream.of("myworkdayjobs.com", "myworkdaysite.com", "ashbyhq.com", "lever.co", "breezy.hr",
                    "jobs.personio.de", "jobs.personio.com", "linkedin.com", "indeed.com", "indeedemail.com",
                    "glassdoor.com", "kariyer.net",
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
            String wanted = CompanyMatcher.coreName(companyName);
            lookUpDomain(companyName, wanted::equals).ifPresent(domains::add);
        }
        if (domains.isEmpty()) {
            // Recruiting-site links name the company in short (ocbc.wd102.myworkdayjobs.com,
            // boards.greenhouse.io/stripe): look that up, accepting the same name or the start of it.
            String wanted = CompanyMatcher.coreName(companyName);
            for (String slug : hints.stream().map(CompanyLogoFinder::recruitingSiteSlug).filter(s -> s != null).distinct().toList()) {
                Optional<String> domain = lookUpDomain(slug, found -> found.equals(slug.replace('-', ' '))
                        || wanted.equals(found) || wanted.startsWith(found + " "))
                        .or(() -> guessDomain(slug, wanted));
                if (domain.isPresent()) {
                    domains.add(domain.get());
                    break;
                }
            }
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

    /**
     * Asks Clearbit's free company-name autocomplete for a domain, taking the first suggestion whose
     * (simplified) name {@code accepts}.
     */
    private Optional<String> lookUpDomain(String query, Predicate<String> accepts) {
        if (query == null || CompanyMatcher.coreName(query).length() < 2) {
            return Optional.empty();
        }
        try {
            Fetched result = fetch(URI.create("https://autocomplete.clearbit.com/v1/companies/suggest?query="
                    + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8)), 200_000);
            if (result == null) {
                return Optional.empty();
            }
            JsonNode suggestions = jsonMapper.readTree(result.body());
            for (JsonNode suggestion : suggestions) {
                String domain = domainOf(suggestion.path("domain").asString(""));
                String name = CompanyMatcher.coreName(suggestion.path("name").asString(""));
                if (domain != null && isCompanySite(domain) && name.length() >= 2 && accepts.test(name)) {
                    return Optional.of(domain);
                }
            }
        } catch (RuntimeException e) {
            log.debug("Company lookup for '{}' failed: {}", query, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * {@code <slug>.com}, when there are two pieces of evidence it is the company's: the site is up,
     * and either its title names the slug or the company's own name starts with it ("OCBC e2 Power"
     * and ocbc.wd102.myworkdayjobs.com). A look-alike or parked domain isn't taken on the link alone.
     */
    private Optional<String> guessDomain(String slug, String companyCoreName) {
        if (slug.length() < 3 || slug.contains("-")) {
            return Optional.empty();
        }
        String domain = slug + ".com";
        try {
            Fetched page = fetch(URI.create("https://" + domain + "/"), MAX_PAGE_BYTES);
            if (page == null || !page.contentType().contains("html")) {
                return Optional.empty();
            }
            if (companyCoreName.equals(slug) || companyCoreName.startsWith(slug + " ")) {
                return Optional.of(domain);
            }
            Document document = Jsoup.parse(new String(page.body(), StandardCharsets.UTF_8), page.finalUri().toString());
            String names = " " + CompanyMatcher.normalize(document.title() + " "
                    + document.select("meta[property=og:site_name]").attr("content")) + " ";
            return names.contains(" " + slug + " ") ? Optional.of(domain) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The company's short name in a recruiting-site link, or null: the tenant in
     * {@code ocbc.wd102.myworkdayjobs.com} or {@code acme.bamboohr.com}, or the first path part in
     * {@code boards.greenhouse.io/stripe} or {@code jobs.lever.co/acme}.
     */
    static String recruitingSiteSlug(String hint) {
        if (hint == null || !hint.contains("://")) {
            return null;
        }
        try {
            URI uri = URI.create(hint.strip());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String[] path = (uri.getPath() == null ? "" : uri.getPath()).replaceFirst("^/+", "").split("/");
            String slug = null;
            if (host.matches(".+\\.(myworkdayjobs|myworkdaysite|bamboohr|recruitee|teamtailor|applytojob)\\.com")
                    || host.matches(".+\\.breezy\\.hr") || host.matches(".+\\.jobs\\.personio\\.(de|com)")) {
                slug = host.substring(0, host.indexOf('.'));
            } else if (host.matches("(boards|job-boards)(\\.eu)?\\.greenhouse\\.io|jobs(\\.eu)?\\.lever\\.co"
                    + "|jobs\\.ashbyhq\\.com|jobs\\.smartrecruiters\\.com|apply\\.workable\\.com")) {
                slug = path.length > 0 ? path[0] : null;
            }
            if (slug == null) {
                return null;
            }
            slug = slug.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
            return slug.length() >= 2 && !slug.equals("embed") && !slug.equals("careers") ? slug : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Google's favicon service answers with the same grey globe for every site it has no icon for
     * (some sites, e.g. ones that block automated visitors). That isn't the company's logo, so it's
     * refused and the company keeps its letter tile. SHA-256 of each size/format Google serves.
     */
    static final Set<String> PLACEHOLDER_IMAGES = Set.of(
            "b6b2c1e6b1a515177baf79871c21db0f13372de333bc2763f1b45c09aabd1780",
            "493506d686597b9e91c1cb2736600ac22190762e0ced2fba848b8dd623cc4f15",
            "59bfe9bc385ad69f50793ce4a53397316d7a875a7148a63c16df9b674c6cda64");

    static boolean isPlaceholder(byte[] image) {
        try {
            return PLACEHOLDER_IMAGES.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image)));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private Optional<Fetched> downloadImage(String url) {
        try {
            Fetched image = fetch(URI.create(url), MAX_IMAGE_BYTES);
            if (image == null || image.body().length < MIN_IMAGE_BYTES || isPlaceholder(image.body())) {
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
