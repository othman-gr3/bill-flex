package com.billflex.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void validEmail_returns204() throws Exception {
        mockMvc.perform(post("/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinataire":"client@example.com","objet":"Facture","contenu":"Bonjour"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinataire":"not-an-email","objet":"Facture","contenu":"Bonjour"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mailFailure_returns503() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("Mailpit down"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        mockMvc.perform(post("/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinataire":"client@example.com","objet":"Facture","contenu":"Bonjour"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }
}
