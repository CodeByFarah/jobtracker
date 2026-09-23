package com.jobtrack.mapper;

import com.jobtrack.dto.company.CompanyResponse;
import com.jobtrack.entity.Company;

public final class CompanyMapper {

    private CompanyMapper() {
    }

    public static CompanyResponse toResponse(Company company, long applicationCount) {
        return new CompanyResponse(company.getId(), company.getName(), company.getWebsite(), company.getIndustry(),
                company.getLocation(), company.getNotes(), applicationCount, company.getCreatedAt(),
                company.getUpdatedAt());
    }
}
