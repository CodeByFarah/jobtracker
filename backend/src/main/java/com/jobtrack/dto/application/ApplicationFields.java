package com.jobtrack.dto.application;

import com.jobtrack.entity.EmploymentType;

import java.time.LocalDate;

/**
 * The editable details shared by the create and update requests, so the service can validate
 * and apply them with one piece of code.
 */
public interface ApplicationFields {

    Long companyId();

    String jobTitle();

    String jobUrl();

    String location();

    EmploymentType employmentType();

    Integer salaryMin();

    Integer salaryMax();

    String salaryCurrency();

    LocalDate applicationDate();

    String notes();
}
