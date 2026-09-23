package com.jobtrack.repository;

import com.jobtrack.dto.application.ApplicationSearchCriteria;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the WHERE clause for the application list. The owner restriction is always applied;
 * each optional filter is added only when present, and all filters are combined with AND.
 */
public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    public static Specification<JobApplication> forUser(Long userId, ApplicationSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (criteria.query() != null) {
                Join<JobApplication, Company> company = root.join("company");
                String pattern = likePattern(criteria.query());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("jobTitle")), pattern, '\\'),
                        cb.like(cb.lower(company.get("name")), pattern, '\\')));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.location() != null) {
                predicates.add(cb.like(cb.lower(root.get("location")), likePattern(criteria.location()), '\\'));
            }
            if (criteria.employmentType() != null) {
                predicates.add(cb.equal(root.get("employmentType"), criteria.employmentType()));
            }
            if (criteria.companyId() != null) {
                predicates.add(cb.equal(root.get("company").get("id"), criteria.companyId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Case-insensitive "contains" pattern with LIKE wildcards in the user's input escaped. */
    static String likePattern(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
