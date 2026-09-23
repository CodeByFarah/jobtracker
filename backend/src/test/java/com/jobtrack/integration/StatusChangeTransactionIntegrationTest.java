package com.jobtrack.integration;

import com.jobtrack.TestcontainersConfiguration;
import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.repository.ApplicationStatusHistoryRepository;
import com.jobtrack.repository.CompanyRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * If writing the history entry fails, the status update made earlier in the same transaction
 * must be rolled back: the application keeps its old status in the database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StatusChangeTransactionIntegrationTest {

    @MockitoBean
    ApplicationStatusHistoryRepository historyRepository;

    @Autowired
    ApplicationService applicationService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    JobApplicationRepository applicationRepository;

    @Test
    void statusChangeIsRolledBackWhenHistoryCannotBeSaved() {
        User user = userRepository.save(new User("tx-" + UUID.randomUUID() + "@example.com", "hash", "Tx User"));
        Company company = companyRepository.save(new Company(user, "Tx Co"));
        JobApplication application = applicationRepository.save(
                new JobApplication(user, company, "Engineer", ApplicationStatus.APPLIED));
        when(historyRepository.save(any())).thenThrow(new DataAccessResourceFailureException("history table unavailable"));

        assertThatThrownBy(() -> applicationService.changeStatus(user.getId(), application.getId(), ApplicationStatus.INTERVIEW))
                .isInstanceOf(DataAccessResourceFailureException.class);

        JobApplication reloaded = applicationRepository.findById(application.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
    }
}
