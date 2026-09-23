package com.jobtrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A job the user has saved or applied for. Named JobApplication to avoid clashing with the
 * many framework classes called "Application"; the table is simply {@code applications}.
 */
@Entity
@Table(name = "applications")
public class JobApplication extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(name = "job_url")
    private String jobUrl;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "salary_max")
    private Integer salaryMax;

    /** ISO 4217 code, stored upper-case. */
    @Column(name = "salary_currency", length = 3)
    private String salaryCurrency;

    @Column(name = "application_date")
    private LocalDate applicationDate;

    /**
     * Only changed through {@code ApplicationService.changeStatus}, which also records the
     * transition in the status history.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(columnDefinition = "text")
    private String notes;

    protected JobApplication() {
    }

    public JobApplication(User user, Company company, String jobTitle, ApplicationStatus status) {
        this.user = user;
        this.company = company;
        this.jobTitle = jobTitle;
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Integer salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Integer getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(Integer salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getSalaryCurrency() {
        return salaryCurrency;
    }

    public void setSalaryCurrency(String salaryCurrency) {
        this.salaryCurrency = salaryCurrency;
    }

    public LocalDate getApplicationDate() {
        return applicationDate;
    }

    public void setApplicationDate(LocalDate applicationDate) {
        this.applicationDate = applicationDate;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
