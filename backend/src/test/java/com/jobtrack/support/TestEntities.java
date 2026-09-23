package com.jobtrack.support;

import com.jobtrack.entity.ApplicationStatus;
import com.jobtrack.entity.Company;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds entities with ids set, as if they had been loaded from the database. */
public final class TestEntities {

    private TestEntities() {
    }

    public static User user(long id) {
        return withId(new User("user" + id + "@example.com", "hash", "User " + id), id);
    }

    public static Company company(long id, User owner, String name) {
        return withId(new Company(owner, name), id);
    }

    public static JobApplication application(long id, User owner, Company company, ApplicationStatus status) {
        return withId(new JobApplication(owner, company, "Software Engineer", status), id);
    }

    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
