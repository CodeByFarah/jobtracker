package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A company's logo image, kept apart from {@link Company} so lists never load image bytes. */
@Entity
@Table(name = "company_logos")
public class CompanyLogo {

    @Id
    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "image", nullable = false, columnDefinition = "bytea")
    private byte[] image;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompanyLogo() {
    }

    public CompanyLogo(Long companyId) {
        this.companyId = companyId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getImage() {
        return image;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replace(String contentType, byte[] image, String sourceUrl, Instant updatedAt) {
        this.contentType = contentType;
        this.image = image;
        this.sourceUrl = sourceUrl;
        this.updatedAt = updatedAt;
    }
}
