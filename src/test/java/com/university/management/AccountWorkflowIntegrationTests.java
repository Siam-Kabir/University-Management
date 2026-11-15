package com.university.management;
//hello
import com.university.management.model.Account;
import com.university.management.model.Role;
import com.university.management.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import javax.sql.DataSource;
import java.sql.SQLException;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("workflow")
class AccountWorkflowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository repository;
    @Autowired PasswordEncoder encoder;
    @Autowired DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        if ("true".equals(System.getenv("EXPECT_POSTGRES"))) {
            try (var connection = dataSource.getConnection()) {
                assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            }
        }
        repository.deleteAll();
        repository.save(new Account("Admin", "admin@test.com", encoder.encode("Admin123!"), Role.ADMIN));
        repository.save(new Account("Teacher", "teacher@test.com", encoder.encode("Teacher123!"), Role.TEACHER));
        repository.save(new Account("Student", "student@test.com", encoder.encode("Student123!"), Role.STUDENT));
    }

    @Test
    void adminCreatesStudentWhoCanSeeOnlyTheirOwnRecord() throws Exception {
        mvc.perform(post("/api/students").with(user("admin@test.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Student\",\"email\":\"New@Example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@example.com"));

        Account created = repository.findByEmailIgnoreCase("new@example.com").orElseThrow();
        assertTrue(encoder.matches("Secret123!", created.getPassword()));
        mvc.perform(get("/api/students").with(user("new@example.com").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("new@example.com"));
        mvc.perform(get("/api/students/{id}", created.getId())
                .with(user("student@test.com").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void conflictingUpdateReturns409AndLeavesStoredAccountIntact() throws Exception {
        Account student = repository.findByEmailIgnoreCase("student@test.com").orElseThrow();
        String originalHash = student.getPassword();

        mvc.perform(put("/api/students/{id}", student.getId())
                .with(user("admin@test.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Changed\",\"email\":\"TEACHER@test.com\",\"password\":\"NewSecret123!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email is already in use"));

        Account unchanged = repository.findById(student.getId()).orElseThrow();
        assertEquals("Student", unchanged.getName());
        assertEquals("student@test.com", unchanged.getEmail());
        assertEquals(originalHash, unchanged.getPassword());
    }

    @Test
    void existingMixedCaseEmailCanBeNormalizedOnUpdate() throws Exception {
        Account student = repository.save(new Account("Legacy Student", "Legacy@Example.com",
                encoder.encode("Legacy123!"), Role.STUDENT));

        mvc.perform(put("/api/students/{id}", student.getId())
                .with(user("admin@test.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Legacy Student\",\"email\":\"LEGACY@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("legacy@example.com"));

        assertEquals("legacy@example.com", repository.findById(student.getId()).orElseThrow().getEmail());
    }
}
