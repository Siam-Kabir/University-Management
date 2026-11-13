package com.university.management;

import com.university.management.model.Account;
import com.university.management.model.Role;
import com.university.management.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:universitytest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "demo.users.enabled=false"})
class UniversityApiTests {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository repository;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(new Account("Admin", "admin@test.com", encoder.encode("Admin123!"), Role.ADMIN));
        repository.save(new Account("Teacher", "teacher@test.com", encoder.encode("Teacher123!"), Role.TEACHER));
        repository.save(new Account("Student", "student@test.com", encoder.encode("Student123!"), Role.STUDENT));
    }

    @Test
    void loginUsesSessionAndLogoutEndsIt() throws Exception {
        MvcResult csrfResult = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        String token = new tools.jackson.databind.ObjectMapper()
                .readTree(csrfResult.getResponse().getContentAsString()).get("token").asText();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/admins").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admins").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDeleteOwnAccountButCanDeleteAnotherAdmin() throws Exception {
        long ownId = repository.findByEmailIgnoreCase("admin@test.com").orElseThrow().getId();
        Account other = repository.save(new Account("Other Admin", "other-admin@test.com",
                encoder.encode("Secret123!"), Role.ADMIN));
        mvc.perform(delete("/api/admins/{id}", ownId)
                .with(user("admin@test.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You cannot delete your own account"));
        assertTrue(repository.existsById(ownId));
        mvc.perform(delete("/api/admins/{id}", other.getId())
                .with(user("admin@test.com").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertFalse(repository.existsById(other.getId()));
    }

    @Test
    void frontendIsPublicWhileSessionDetailsStayProtected() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("University Management System")));
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/styles.css")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").with(user("teacher@test.com").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"));
        mvc.perform(get("/api/auth/me").with(user("missing@test.com").roles("STUDENT")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/private-file")).andExpect(status().isUnauthorized());
    }

    @Test
    void incorrectLoginAndAnonymousAccessAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").exists());
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/teachers").with(user("teacher").roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesMatchThePermissionTable() throws Exception {
        for (String path : new String[]{"/api/admins", "/api/teachers", "/api/students"}) {
            mvc.perform(get(path).with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/teachers").with(user("teacher@test.com").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/admins").with(user("teacher").roles("TEACHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/students").with(user("teacher").roles("TEACHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/students").with(user("student@test.com").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/admins").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/teachers").with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
    }

    @Test
    void teachersAndStudentsCanViewAndEditOnlyTheirOwnAccount() throws Exception {
        String body = "{\"name\":\"Another Person\",\"email\":\"another@test.com\",\"password\":\"Secret123!\"}";
        long teacherId = repository.findByEmailIgnoreCase("teacher@test.com").orElseThrow().getId();
        long studentId = repository.findByEmailIgnoreCase("student@test.com").orElseThrow().getId();
        mvc.perform(post("/api/teachers").with(user("teacher@test.com").roles("TEACHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/students").with(user("teacher").roles("TEACHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admins").with(user("student").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/students").with(user("student@test.com").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("another@test.com", "student2@test.com"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/teachers/{id}", teacherId).with(user("teacher@test.com").roles("TEACHER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/students/{id}", studentId).with(user("student@test.com").roles("STUDENT")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/teachers/1").with(user("student").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/students/1").with(user("teacher").roles("TEACHER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/teachers/{id}", teacherId).with(user("teacher@test.com").roles("TEACHER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/students/{id}", studentId).with(user("student@test.com").roles("STUDENT")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/teachers/{id}", teacherId).with(user("teacher@test.com").roles("TEACHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Teacher\",\"email\":\"teacher@test.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Teacher"));
        mvc.perform(put("/api/students/{id}", studentId).with(user("student@test.com").roles("STUDENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Student\",\"email\":\"student@test.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Student"));
        MvcResult other = mvc.perform(post("/api/teachers").with(user("admin@test.com").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
        long otherId = new tools.jackson.databind.ObjectMapper()
                .readTree(other.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/teachers").with(user("teacher@test.com").roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/teachers/{id}", otherId).with(user("teacher@test.com").roles("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/teachers/{id}", otherId).with(user("teacher@test.com").roles("TEACHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
    }

    @Test
    void createReadUpdateDeleteAndHashPasswords() throws Exception {
        MvcResult created = mvc.perform(post("/api/students").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Student\",\"email\":\"new@test.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist()).andReturn();
        long id = new tools.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("id").asLong();
        Account account = repository.findById(id).orElseThrow();
        assertNotEquals("Secret123!", account.getPassword());
        assertTrue(encoder.matches("Secret123!", account.getPassword()));
        mvc.perform(get("/api/students/{id}", id).with(user("new@test.com").roles("STUDENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New Student"));
        mvc.perform(put("/api/students/{id}", id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Student\",\"email\":\"new@test.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Student"));
        mvc.perform(delete("/api/students/{id}", id).with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/students/{id}", id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validationDuplicatesAndMissingRecordsReturnUsefulErrors() throws Exception {
        mvc.perform(post("/api/teachers").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/api/teachers").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Duplicate\",\"email\":\"ADMIN@test.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/teachers/999999").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
    }
}
