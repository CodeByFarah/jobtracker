package com.jobtrack.logo;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyLogoFinderTest {

    @Test
    void domainsComeFromLinksHostsAndEmailAddresses() {
        assertThat(CompanyLogoFinder.domainOf("https://careers.acme.com/jobs/123")).isEqualTo("acme.com");
        assertThat(CompanyLogoFinder.domainOf("Acme Talent <jobs@mail.acme.io>")).isEqualTo("acme.io");
        assertThat(CompanyLogoFinder.domainOf("www.trendyol.com")).isEqualTo("trendyol.com");
        assertThat(CompanyLogoFinder.domainOf("https://kariyer.trendyol.com.tr/ilan")).isEqualTo("trendyol.com.tr");
        assertThat(CompanyLogoFinder.domainOf("https://www.example.co.uk")).isEqualTo("example.co.uk");
        assertThat(CompanyLogoFinder.domainOf("http://192.168.1.10/admin")).isNull();
        assertThat(CompanyLogoFinder.domainOf("localhost")).isNull();
        assertThat(CompanyLogoFinder.domainOf("")).isNull();
    }

    @Test
    void recruitingSiteLinksNameTheCompanyInShort() {
        assertThat(CompanyLogoFinder.recruitingSiteSlug("https://ocbc.wd102.myworkdayjobs.com/en-US/External/job/123"))
                .isEqualTo("ocbc");
        assertThat(CompanyLogoFinder.recruitingSiteSlug("https://boards.greenhouse.io/stripe/jobs/42")).isEqualTo("stripe");
        assertThat(CompanyLogoFinder.recruitingSiteSlug("https://jobs.lever.co/acme-labs/abc")).isEqualTo("acme-labs");
        assertThat(CompanyLogoFinder.recruitingSiteSlug("https://acme.bamboohr.com/careers/7")).isEqualTo("acme");
        assertThat(CompanyLogoFinder.recruitingSiteSlug("https://acme.com/careers")).isNull();
        assertThat(CompanyLogoFinder.recruitingSiteSlug("Jobs <jobs@acme.com>")).isNull();
    }

    @Test
    void jobBoardsRecruitingToolsAndMailProvidersAreNotTheCompany() {
        assertThat(CompanyLogoFinder.isCompanySite("greenhouse.io")).isFalse();
        assertThat(CompanyLogoFinder.isCompanySite("myworkday.com")).isFalse();
        assertThat(CompanyLogoFinder.isCompanySite(CompanyLogoFinder.domainOf("https://ocbc.wd102.myworkdayjobs.com/x")))
                .isFalse();
        assertThat(CompanyLogoFinder.isCompanySite("linkedin.com")).isFalse();
        assertThat(CompanyLogoFinder.isCompanySite("gmail.com")).isFalse();
        assertThat(CompanyLogoFinder.isCompanySite("kariyer.net")).isFalse();
        assertThat(CompanyLogoFinder.isCompanySite("stripe.com")).isTrue();
    }

    @Test
    void theLargestDeclaredIconComesFirstAndSvgIsSkipped() {
        var document = Jsoup.parse("""
                <html><head>
                  <link rel="icon" href="/favicon-32.png" sizes="32x32">
                  <link rel="icon" type="image/svg+xml" href="/logo.svg">
                  <link rel="apple-touch-icon" href="/apple-touch-icon.png">
                  <link rel="icon" href="/icon-192.png" sizes="192x192">
                  <link rel="stylesheet" href="/site.css">
                </head></html>""", "https://acme.com/");

        List<CompanyLogoFinder.IconCandidate> icons = CompanyLogoFinder.iconCandidates(document);

        assertThat(icons).extracting(CompanyLogoFinder.IconCandidate::url).containsExactly(
                "https://acme.com/icon-192.png", "https://acme.com/apple-touch-icon.png",
                "https://acme.com/favicon-32.png");
        assertThat(icons.get(1).size()).isEqualTo(180); // apple-touch-icon without sizes
    }

    @Test
    void googlesGenericGlobeIsNotALogo() throws IOException {
        assertThat(CompanyLogoFinder.isPlaceholder(png(96))).isFalse();
        assertThat(CompanyLogoFinder.PLACEHOLDER_IMAGES).hasSize(3);
    }

    @Test
    void imageTypeComesFromTheFileNotTheServer() throws IOException {
        byte[] png = png(96);
        assertThat(CompanyLogoFinder.imageType(png)).isEqualTo("image/png");
        assertThat(CompanyLogoFinder.widthOf(png)).isEqualTo(96);
        assertThat(CompanyLogoFinder.imageType("<html><body>not an image</body></html>".getBytes())).isNull();
        assertThat(CompanyLogoFinder.imageType(new byte[]{0, 0, 1, 0, 1, 0, 32, 32, 0, 0, 1, 0})).isEqualTo("image/x-icon");
    }

    private static byte[] png(int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }
}
