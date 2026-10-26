package com.jobtrack.logo;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finds real logos on the internet. Off by default because it needs a network connection:
 * <pre>mvnw test -Dtest=CompanyLogoFinderLiveTest -Dlogos.live=true</pre>
 */
@EnabledIfSystemProperty(named = "logos.live", matches = "true")
class CompanyLogoFinderLiveTest {

    private final CompanyLogoFinder finder = new CompanyLogoFinder(JsonMapper.builder().build());

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "Stripe            | https://boards.greenhouse.io/stripe/jobs/1 | stripe.com",
            "Trendyol          |                                             | trendyol.com",
            "Spotify           | Spotify Jobs <no-reply@spotify.com>         | spotify.com",
            "Shopify           |                                             | shopify.com",
            "Monzo             | https://monzo.com/careers                   | monzo.com",
            "OCBC e2 Power Sdn Bhd | https://ocbc.wd102.myworkdayjobs.com/en-US/External/job/1 | ocbc.com"})
    void findsTheLogoOfARealCompany(String name, String hint, String expectedDomain) {
        long started = System.nanoTime();
        Optional<CompanyLogoFinder.FoundLogo> logo = finder.find(name, hint == null ? List.of() : List.of(hint));

        logo.ifPresent(l -> System.out.printf("[logos] %-10s %-14s %6d bytes %-12s %4dpx %4.1fs  %s%n", name,
                l.domain(), l.image().length, l.contentType(), CompanyLogoFinder.widthOf(l.image()),
                (System.nanoTime() - started) / 1e9, l.sourceUrl()));
        assertThat(logo).isPresent();
        assertThat(logo.get().domain()).isEqualTo(expectedDomain);
        assertThat(logo.get().image().length).isGreaterThan(100);
    }
}
