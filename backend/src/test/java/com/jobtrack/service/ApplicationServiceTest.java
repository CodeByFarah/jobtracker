package com.jobtrack.service;

import com.jobtrack.dto.application.ApplicationResponse;
import com.jobtrack.dto.application.CreateApplicationRequest;
import com.jobtrack.dto.application.UpdateApplicationRequest;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.ApplicationStatusHistory;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.EmploymentType;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.exception.BadRequestException;
import com.jobtrack.exception.ConflictException;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.ApplicationStatusHistoryRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    private static final long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    @Mock
    JobApplicationRepository applicationRepository;
    @Mock
    ApplicationStatusHistoryRepository historyRepository;
    @Mock
    CompanyService companyService;
    @Mock
    UserRepository userRepository;

    ApplicationService service;
    User user;
    Company company;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applicationRepository, historyRepository, companyService, userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = TestEntities.user(USER_ID);
        company = TestEntities.company(10L, user, "Google");
    }

    @Nested
    class Create {

        @BeforeEach
        void ownedCompany() {
            when(companyService.getOwnedCompany(USER_ID, 10L)).thenReturn(company);
            when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        }

        @Test
        void savesApplicationAndRecordsInitialHistoryEntry() {
            ApplicationResponse response = service.create(USER_ID, request(ApplicationStatus.APPLIED, null));

            ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
            verify(applicationRepository).save(saved.capture());
            assertThat(saved.getValue().getJobTitle()).isEqualTo("Backend Engineer");
            assertThat(saved.getValue().getCompany()).isSameAs(company);
            assertThat(saved.getValue().getUser()).isSameAs(user);

            ArgumentCaptor<ApplicationStatusHistory> history = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getPreviousStatus()).isNull();
            assertThat(history.getValue().getNewStatus()).isEqualTo(ApplicationStatus.APPLIED);
            assertThat(history.getValue().getChangedAt()).isEqualTo(NOW);

            assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
            assertThat(response.company().name()).isEqualTo("Google");
        }

        @Test
        void defaultsToSavedWithoutApplicationDate() {
            ApplicationResponse response = service.create(USER_ID, request(null, null));
            assertThat(response.status()).isEqualTo(ApplicationStatus.SAVED);
            assertThat(response.applicationDate()).isNull();
        }

        @Test
        void defaultsApplicationDateToTodayOnceApplied() {
            assertThat(service.create(USER_ID, request(ApplicationStatus.APPLIED, null)).applicationDate())
                    .isEqualTo(TODAY);
        }

        @Test
        void keepsAnExplicitApplicationDate() {
            LocalDate date = TODAY.minusDays(7);
            assertThat(service.create(USER_ID, request(ApplicationStatus.APPLIED, date)).applicationDate())
                    .isEqualTo(date);
        }

        @Test
        void normalisesTextAndCurrency() {
            CreateApplicationRequest request = new CreateApplicationRequest(10L, "  Backend Engineer  ", " ", " Remote ",
                    EmploymentType.FULL_TIME, 1, 2, "usd", null, null, "   ");
            ApplicationResponse response = service.create(USER_ID, request);
            assertThat(response.jobTitle()).isEqualTo("Backend Engineer");
            assertThat(response.jobUrl()).isNull();
            assertThat(response.location()).isEqualTo("Remote");
            assertThat(response.salaryCurrency()).isEqualTo("USD");
            assertThat(response.notes()).isNull();
        }

        @Test
        void rejectsSalaryMinimumAboveMaximum() {
            CreateApplicationRequest request = new CreateApplicationRequest(10L, "Dev", null, null, null,
                    200, 100, null, null, null, null);
            assertThatThrownBy(() -> service.create(USER_ID, request))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("field").isEqualTo("salaryMax");
            verify(applicationRepository, never()).save(any());
        }

        @Test
        void rejectsApplicationDateInTheFuture() {
            assertThatThrownBy(() -> service.create(USER_ID, request(ApplicationStatus.APPLIED, TODAY.plusDays(5))))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("field").isEqualTo("applicationDate");
        }

        @Test
        void allowsTomorrowToTolerateTimeZones() {
            assertThat(service.create(USER_ID, request(ApplicationStatus.APPLIED, TODAY.plusDays(1))).applicationDate())
                    .isEqualTo(TODAY.plusDays(1));
        }
    }

    @Test
    void createFailsWhenCompanyIsNotOwned() {
        when(companyService.getOwnedCompany(USER_ID, 10L)).thenThrow(new ResourceNotFoundException("Company", 10L));
        assertThatThrownBy(() -> service.create(USER_ID, request(null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(applicationRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
    }

    @Nested
    class ChangeStatus {

        JobApplication application;

        @BeforeEach
        void existingApplication() {
            application = TestEntities.application(5L, user, company, ApplicationStatus.APPLIED);
            application.setApplicationDate(TODAY.minusDays(10));
            when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(application));
        }

        @Test
        void updatesStatusAndRecordsOneHistoryEntryWithPreviousStatus() {
            ApplicationResponse response = service.changeStatus(USER_ID, 5L, ApplicationStatus.INTERVIEW);

            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
            assertThat(response.status()).isEqualTo(ApplicationStatus.INTERVIEW);

            ArgumentCaptor<ApplicationStatusHistory> history = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
            verify(historyRepository).save(history.capture());
            assertThat(history.getValue().getApplication()).isSameAs(application);
            assertThat(history.getValue().getPreviousStatus()).isEqualTo(ApplicationStatus.APPLIED);
            assertThat(history.getValue().getNewStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
            assertThat(history.getValue().getChangedAt()).isEqualTo(NOW);
        }

        @Test
        void rejectsDisallowedTransitionWithoutWritingHistory() {
            assertThatThrownBy(() -> service.changeStatus(USER_ID, 5L, ApplicationStatus.SAVED))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Allowed next statuses");
            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
            verify(historyRepository, never()).save(any());
        }

        @Test
        void rejectsChangingToTheSameStatus() {
            assertThatThrownBy(() -> service.changeStatus(USER_ID, 5L, ApplicationStatus.APPLIED))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already");
            verify(historyRepository, never()).save(any());
        }

        @Test
        void rejectsLeavingAFinalStatus() {
            application.setStatus(ApplicationStatus.REJECTED);
            assertThatThrownBy(() -> service.changeStatus(USER_ID, 5L, ApplicationStatus.INTERVIEW))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("final status");
        }
    }

    @Test
    void changeStatusSetsApplicationDateWhenFirstApplied() {
        JobApplication saved = TestEntities.application(6L, user, company, ApplicationStatus.SAVED);
        when(applicationRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(saved));

        service.changeStatus(USER_ID, 6L, ApplicationStatus.APPLIED);

        assertThat(saved.getApplicationDate()).isEqualTo(TODAY);
    }

    @Test
    void operationsOnAnotherUsersApplicationAreNotFound() {
        when(applicationRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER_ID, 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.changeStatus(USER_ID, 99L, ApplicationStatus.OFFER))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(USER_ID, 99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getHistory(USER_ID, 99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(applicationRepository, never()).delete(any(JobApplication.class));
    }

    @Test
    void updateChangesDetailsButNeverTheStatus() {
        JobApplication application = TestEntities.application(5L, user, company, ApplicationStatus.INTERVIEW);
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(application));

        UpdateApplicationRequest request = new UpdateApplicationRequest(10L, "Staff Engineer", "https://jobs.example.com/1",
                "Zurich", EmploymentType.CONTRACT, 100, 150, "chf", TODAY.minusDays(3), "Referred by Sam");
        ApplicationResponse response = service.update(USER_ID, 5L, request);

        assertThat(response.jobTitle()).isEqualTo("Staff Engineer");
        assertThat(response.location()).isEqualTo("Zurich");
        assertThat(response.salaryCurrency()).isEqualTo("CHF");
        assertThat(response.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void updateCanMoveApplicationToAnotherOwnedCompany() {
        JobApplication application = TestEntities.application(5L, user, company, ApplicationStatus.APPLIED);
        Company other = TestEntities.company(11L, user, "Meta");
        when(applicationRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(application));
        when(companyService.getOwnedCompany(USER_ID, 11L)).thenReturn(other);

        service.update(USER_ID, 5L, new UpdateApplicationRequest(11L, "Dev", null, null, null, null, null, null, null, null));

        assertThat(application.getCompany()).isSameAs(other);
    }

    private static CreateApplicationRequest request(ApplicationStatus status, LocalDate applicationDate) {
        return new CreateApplicationRequest(10L, "Backend Engineer", "https://jobs.example.com/42", "Remote",
                EmploymentType.FULL_TIME, 90_000, 120_000, "EUR", applicationDate, status, null);
    }
}
