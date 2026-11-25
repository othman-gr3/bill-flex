package com.billflex.clients;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.billflex.clients.repository.IdempotencyRecordRepository;
import com.billflex.clients.repository.PaymentRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class ClientsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void listClients_returns200() throws Exception {
        mockMvc.perform(get("/clients"))
                .andExpect(status().isOk());
    }

    @Test
    void getClient_existing_returns200() throws Exception {
        mockMvc.perform(get("/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("contact@atlastech.ma"));
    }

    @Test
    void getClient_missing_returns404() throws Exception {
        mockMvc.perform(get("/clients/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createClient_valid_returns201() throws Exception {
        String body = """
                {"nom":"Nouveau Client","email":"nouveau@client.ma","numeroTVA":"MA99887766","modePaiement":"CARTE"}
                """;
        mockMvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.statut").value("ACTIF"));
    }

    @Test
    void createClient_invalid_returns400() throws Exception {
        String body = """
                {"nom":"","email":"pas-un-email","numeroTVA":"xx","modePaiement":null}
                """;
        mockMvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.nom").exists())
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void createClient_duplicateEmail_returns409() throws Exception {
        String body = """
                {"nom":"Doublon","email":"contact@atlastech.ma","numeroTVA":"MA12345678","modePaiement":"VIREMENT"}
                """;
        mockMvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createPayment_unknownClient_returns404() throws Exception {
        String body = """
                {"clientId":999,"invoiceId":1,"montant":10.00,"moyen":"CARTE"}
                """;
        mockMvc.perform(post("/payments").header("Idempotency-Key", "payment-unknown-client")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPayment_sameKeyAndBody_replaysOriginalWithoutDuplicate() throws Exception {
        String key = "payment-replay-9001";
        String body = """
                {"clientId":1,"invoiceId":9001,"montant":10.00,"moyen":"CARTE"}
                """;
        long before = paymentRepository.countByClient_IdAndInvoiceId(1L, 9001L);

        String firstResponse = mockMvc.perform(post("/payments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        long originalId = mapper.readTree(firstResponse).get("id").asLong();

        String replayResponse = mockMvc.perform(post("/payments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(originalId))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(mapper.readTree(firstResponse), mapper.readTree(replayResponse));
        org.junit.jupiter.api.Assertions.assertEquals(before + 1,
                paymentRepository.countByClient_IdAndInvoiceId(1L, 9001L));
        org.junit.jupiter.api.Assertions.assertTrue(idempotencyRecordRepository.findByIdempotencyKey(key).isPresent());
    }

    @Test
    void createPayment_sameKeyDifferentBody_returns409() throws Exception {
        String key = "payment-different-body";
        mockMvc.perform(post("/payments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":1,\"invoiceId\":9002,\"montant\":10.00,\"moyen\":\"CARTE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/payments").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":1,\"invoiceId\":9002,\"montant\":20.00,\"moyen\":\"CARTE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requête différente")));
    }

    @Test
    void createPayment_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":1,\"invoiceId\":9003,\"montant\":10.00,\"moyen\":\"CARTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createPayment_blankIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/payments").header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":1,\"invoiceId\":9004,\"montant\":10.00,\"moyen\":\"CARTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void payPayment_alreadyPaid_returns409() throws Exception {
        mockMvc.perform(patch("/payments/1/payer"))
                .andExpect(status().isConflict());
    }
}
