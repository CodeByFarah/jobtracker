package com.jobtrack.service;

import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.company.CompanyRequest;
import com.jobtrack.dto.company.CompanyResponse;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.CompanyLogo;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.gmail.CompanyMatcher;
import com.jobtrack.logo.CompanyLogoService;
import com.jobtrack.mapper.ApplicationMapper;
import com.jobtrack.mapper.CompanyMapper;
import com.jobtrack.mapper.TextUtils;
import com.jobtrack.repository.CompanyApplicationCount;
import com.jobtrack.repository.CompanyRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final CompanyLogoService logoService;

    public CompanyService(CompanyRepository companyRepository, JobApplicationRepository applicationRepository,
                          UserRepository userRepository, CompanyLogoService logoService) {
        this.companyRepository = companyRepository;
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.logoService = logoService;
    }

    /** Lists the user's companies alphabetically, optionally filtered by a name substring. */
    @Transactional(readOnly = true)
    public List<CompanyResponse> list(Long userId, String query) {
        String name = TextUtils.clean(query);
        List<Company> companies = name == null
                ? companyRepository.findByUserIdOrderByNameAsc(userId)
                : companyRepository.findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(userId, name);
        Map<Long, Long> counts = applicationRepository.countByCompanyForUser(userId).stream()
                .collect(Collectors.toMap(CompanyApplicationCount::companyId, CompanyApplicationCount::count));
        return companies.stream()
                .map(company -> CompanyMapper.toResponse(company, counts.getOrDefault(company.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(Long userId, Long companyId) {
        Company company = getOwnedCompany(userId, companyId);
        return CompanyMapper.toResponse(company, applicationRepository.countByCompanyId(companyId));
    }

    @Transactional
    public CompanyResponse create(Long userId, CompanyRequest request) {
        String name = TextUtils.clean(request.name());
        if (companyRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("You already have a company named '" + name + "'");
        }
        Company company = new Company(userRepository.getReferenceById(userId), name);
        applyDetails(company, request);
        Company saved = companyRepository.save(company);
        logoService.lookUp(saved.getId());
        return CompanyMapper.toResponse(saved, 0);
    }

    @Transactional
    public CompanyResponse update(Long userId, Long companyId, CompanyRequest request) {
        Company company = getOwnedCompany(userId, companyId);
        String name = TextUtils.clean(request.name());
        if (companyRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, companyId)) {
            throw new ConflictException("You already have a company named '" + name + "'");
        }
        boolean lookAgain = !name.equals(company.getName())
                || !Objects.equals(TextUtils.clean(request.website()), company.getWebsite());
        company.setName(name);
        applyDetails(company, request);
        companyRepository.flush();
        if (lookAgain) {
            logoService.lookUp(companyId);
        }
        return CompanyMapper.toResponse(company, applicationRepository.countByCompanyId(companyId));
    }

    /**
     * Deletion strategy: a company that still has applications cannot be deleted (409). Silently
     * cascading would destroy the user's application history, and nulling the reference would
     * leave applications without an employer. The user must delete or move those applications
     * first. The foreign key is also ON DELETE RESTRICT, so the database enforces the same rule.
     */
    @Transactional
    public void delete(Long userId, Long companyId) {
        Company company = getOwnedCompany(userId, companyId);
        long applications = applicationRepository.countByCompanyId(companyId);
        if (applications > 0) {
            throw new ConflictException("Cannot delete company '" + company.getName() + "' because it has "
                    + applications + " application(s). Delete or move them to another company first.");
        }
        companyRepository.delete(company);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listApplications(Long userId, Long companyId) {
        getOwnedCompany(userId, companyId);
        return applicationRepository.findByCompanyIdAndUserIdOrderByUpdatedAtDesc(companyId, userId).stream()
                .map(ApplicationMapper::toResponse)
                .toList();
    }

    /**
     * The user's company with this name, ignoring case, punctuation and legal endings, so "Acme
     * Inc." finds "ACME" and no second company is created for the same employer.
     */
    @Transactional(readOnly = true)
    public Optional<Company> findSameCompany(Long userId, String name) {
        String wanted = CompanyMatcher.coreName(name);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return companyRepository.findByUserIdOrderByNameAsc(userId).stream()
                .filter(company -> CompanyMatcher.coreName(company.getName()).equals(wanted))
                .findFirst();
    }

    /** A company's new job link may point at its website: look for a logo if there isn't one yet. */
    void lookUpLogoIfMissing(Company company) {
        logoService.lookUpIfMissing(company);
    }

    /** The company's logo image, if one was found. */
    @Transactional(readOnly = true)
    public Optional<CompanyLogo> logo(Long userId, Long companyId) {
        getOwnedCompany(userId, companyId);
        return logoService.logo(companyId);
    }

    /** Returns the company if it belongs to the user; otherwise 404 (not 403) to avoid leaking ids. */
    Company getOwnedCompany(Long userId, Long companyId) {
        return companyRepository.findByIdAndUserId(companyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
    }

    private static void applyDetails(Company company, CompanyRequest request) {
        company.setWebsite(TextUtils.clean(request.website()));
        company.setIndustry(TextUtils.clean(request.industry()));
        company.setLocation(TextUtils.clean(request.location()));
        company.setNotes(TextUtils.clean(request.notes()));
    }
}
