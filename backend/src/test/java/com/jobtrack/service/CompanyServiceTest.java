package com.jobtrack.service;

import com.jobtrack.dto.company.CompanyRequest;
import com.jobtrack.dto.company.CompanyResponse;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.User;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.CompanyApplicationCount;
import com.jobtrack.repository.CompanyRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final long USER_ID = 1L;

    @Mock
    CompanyRepository companyRepository;
    @Mock
    JobApplicationRepository applicationRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    CompanyService service;

    User user;

    @BeforeEach
    void setUp() {
        user = TestEntities.user(USER_ID);
    }

    @Test
    void createsCompanyForCurrentUserWithCleanedFields() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyResponse response = service.create(USER_ID,
                new CompanyRequest("  Stripe ", "https://stripe.com", "Fintech", " ", null));

        assertThat(response.name()).isEqualTo("Stripe");
        assertThat(response.website()).isEqualTo("https://stripe.com");
        assertThat(response.location()).isNull();
        assertThat(response.applicationCount()).isZero();
    }

    @Test
    void rejectsDuplicateCompanyName() {
        when(companyRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Stripe")).thenReturn(true);
        assertThatThrownBy(() -> service.create(USER_ID, new CompanyRequest("Stripe", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        verify(companyRepository, never()).save(any());
    }

    @Test
    void renamingToAnotherExistingNameIsAConflict() {
        Company company = TestEntities.company(3L, user, "Stripe");
        when(companyRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(company));
        when(companyRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Square", 3L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(USER_ID, 3L, new CompanyRequest("Square", null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(company.getName()).isEqualTo("Stripe");
    }

    @Test
    void refusesToDeleteCompanyThatHasApplications() {
        Company company = TestEntities.company(3L, user, "Stripe");
        when(companyRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(company));
        when(applicationRepository.countByCompanyId(3L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(USER_ID, 3L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("2 application(s)");
        verify(companyRepository, never()).delete(any());
    }

    @Test
    void deletesCompanyWithoutApplications() {
        Company company = TestEntities.company(3L, user, "Stripe");
        when(companyRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.of(company));
        when(applicationRepository.countByCompanyId(3L)).thenReturn(0L);

        service.delete(USER_ID, 3L);

        verify(companyRepository).delete(company);
    }

    @Test
    void anotherUsersCompanyIsNotFound() {
        when(companyRepository.findByIdAndUserId(3L, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(USER_ID, 3L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(USER_ID, 3L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.listApplications(USER_ID, 3L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listIncludesApplicationCountsAndSearchesByName() {
        Company stripe = TestEntities.company(3L, user, "Stripe");
        Company square = TestEntities.company(4L, user, "Square");
        when(companyRepository.findByUserIdAndNameContainingIgnoreCaseOrderByNameAsc(USER_ID, "s"))
                .thenReturn(List.of(square, stripe));
        when(applicationRepository.countByCompanyForUser(USER_ID))
                .thenReturn(List.of(new CompanyApplicationCount(3L, 4L)));

        List<CompanyResponse> result = service.list(USER_ID, " s ");

        assertThat(result).extracting(CompanyResponse::name).containsExactly("Square", "Stripe");
        assertThat(result).extracting(CompanyResponse::applicationCount).containsExactly(0L, 4L);
    }
}
