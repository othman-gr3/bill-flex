package com.billflex.facturation;

import com.billflex.facturation.feignclient.AbonnementsClient;
import com.billflex.facturation.feignclient.ClientDto;
import com.billflex.facturation.feignclient.ClientsClient;
import com.billflex.facturation.feignclient.PlanDto;
import com.billflex.facturation.feignclient.SubscriptionDto;
import com.billflex.facturation.messaging.NotificationPublisher;
import com.billflex.facturation.repository.IdempotencyRecordRepository;
import com.billflex.facturation.repository.InvoiceRepository;
import feign.FeignException;
import feign.RetryableException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class FacturationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    // Remote services are mocked: tests need neither Eureka nor other services
    @MockitoBean
    private AbonnementsClient abonnementsClient;

    @MockitoBean
    private ClientsClient clientsClient;

    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @BeforeEach
    void stubRemote() {
        when(abonnementsClient.getSubscription(anyLong()))
                .thenAnswer(i -> new SubscriptionDto(i.getArgument(0), 1L, 2L, "ACTIVE"));
        when(abonnementsClient.getPlan(anyLong()))
                .thenReturn(new PlanDto(2L, "Business", new BigDecimal("99.00")));
        when(clientsClient.getClient(anyLong()))
                .thenReturn(new ClientDto(1L, "Test", "t@t.com", "MA12345678", "VIREMENT", "ACTIF"));
    }

    @Test
    void listInvoices_returns200() throws Exception {
        mockMvc.perform(get("/invoices"))
                .andExpect(status().isOk());
    }

    @Test
    void getInvoice_existing_returns200_withLines() throws Exception {
        mockMvc.perform(get("/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes").isNotEmpty());
    }

    @Test
    void getInvoice_missing_returns404() throws Exception {
        mockMvc.perform(get("/invoices/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getInvoiceDetail_returns200_withClientAndPlan() throws Exception {
        mockMvc.perform(get("/invoices/1/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facture.id").value(1))
                .andExpect(jsonPath("$.client.nom").value("Test"))
                .andExpect(jsonPath("$.abonnement.planNom").value("Business"));
    }

    @Test
    void createInvoice_valid_returns201_andComputesAmounts() throws Exception {
        String body = """
                {"subscriptionId":10,"clientId":1,"periode":"2030-01","echeance":"2030-02-05",
                 "lignes":[{"libelle":"Abonnement Pro","quantite":2,"prixUnitaire":50.00}]}
                """;
        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-valid-2030-01")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.statut").value("BROUILLON"))
                .andExpect(jsonPath("$.montantHT").value(100.0))
                .andExpect(jsonPath("$.montantTTC").value(120.0));
    }

    @Test
    void createInvoice_invalid_returns400() throws Exception {
        String body = """
                {"subscriptionId":null,"clientId":1,"periode":"2026-13","echeance":"2030-02-05","lignes":[]}
                """;
        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.subscriptionId").exists())
                .andExpect(jsonPath("$.details.periode").exists())
                .andExpect(jsonPath("$.details.lignes").exists());
    }

    @Test
    void createInvoice_duplicateSubscriptionAndPeriod_returns409() throws Exception {
        String body = """
                {"subscriptionId":1,"clientId":1,"periode":"2026-09","echeance":"2030-02-05",
                 "lignes":[{"libelle":"Doublon","quantite":1,"prixUnitaire":10.00}]}
                """;
        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-duplicate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvoice_subscriptionSuspended_returns409() throws Exception {
        when(abonnementsClient.getSubscription(anyLong()))
                .thenReturn(new SubscriptionDto(1L, 1L, 2L, "SUSPENDUE"));

        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-suspended-subscription")
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-01")))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvoice_clientSuspended_returns409() throws Exception {
        when(clientsClient.getClient(anyLong()))
                .thenReturn(new ClientDto(1L, "Test", "t@t.com", "MA12345678", "VIREMENT", "SUSPENDU"));

        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-suspended-client")
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-02")))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvoice_subscriptionNotFound_returns404() throws Exception {
        Request req = Request.create(Request.HttpMethod.GET, "/subscriptions/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(abonnementsClient.getSubscription(anyLong()))
                .thenThrow(new FeignException.NotFound("not found", req, null, null));

        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-missing-subscription")
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-03")))
                .andExpect(status().isNotFound());
        verify(abonnementsClient, times(1)).getSubscription(1L);
    }

    @Test
    void createInvoice_remote503_retriesThenReturns503() throws Exception {
        Request req = Request.create(Request.HttpMethod.GET, "/subscriptions/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(abonnementsClient.getSubscription(anyLong()))
                .thenThrow(new FeignException.ServiceUnavailable("down", req, null, Map.of()));

        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-503")
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-09")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
        verify(abonnementsClient, times(3)).getSubscription(1L);
    }

    @Test
    void createInvoice_timeout_retriesThenReturns503() throws Exception {
        RetryableException timeout = mock(RetryableException.class);
        when(timeout.status()).thenReturn(-1);
        when(abonnementsClient.getSubscription(anyLong())).thenThrow(timeout);

        mockMvc.perform(post("/invoices").header("Idempotency-Key", "invoice-timeout")
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-10")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
        verify(abonnementsClient, times(3)).getSubscription(1L);
    }

    @Test
    void createInvoice_sameKeyAndBody_replaysOriginalWithoutDuplicate() throws Exception {
        String key = "invoice-replay-2031-04";
        String requestBody = body("2031-04");
        long before = invoiceRepository.countBySubscriptionIdAndPeriode(1L, "2031-04");

        String firstResponse = mockMvc.perform(post("/invoices").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        long originalId = mapper.readTree(firstResponse).get("id").asLong();

        String replayResponse = mockMvc.perform(post("/invoices").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(originalId))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(mapper.readTree(firstResponse), mapper.readTree(replayResponse));
        org.junit.jupiter.api.Assertions.assertEquals(before + 1,
                invoiceRepository.countBySubscriptionIdAndPeriode(1L, "2031-04"));
        org.junit.jupiter.api.Assertions.assertTrue(idempotencyRecordRepository.findByIdempotencyKey(key).isPresent());
    }

    @Test
    void createInvoice_sameKeyDifferentBody_returns409() throws Exception {
        String key = "invoice-different-body";
        mockMvc.perform(post("/invoices").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-05")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/invoices").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-06")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("requête différente")));
    }

    @Test
    void createInvoice_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body("2031-07")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createInvoice_blankIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/invoices").header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createInvoice_remoteUnavailable_doesNotStoreKeyAndSameKeyCanRetry() throws Exception {
        String key = "invoice-remote-retry";
        Request req = Request.create(Request.HttpMethod.GET, "/subscriptions/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(abonnementsClient.getSubscription(anyLong()))
                .thenThrow(new FeignException.ServiceUnavailable("down", req, null, Map.of()));

        mockMvc.perform(post("/invoices").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-08")))
                .andExpect(status().isServiceUnavailable());
        org.junit.jupiter.api.Assertions.assertTrue(idempotencyRecordRepository.findByIdempotencyKey(key).isEmpty());

        when(abonnementsClient.getSubscription(anyLong()))
                .thenAnswer(i -> new SubscriptionDto(i.getArgument(0), 1L, 2L, "ACTIVE"));
        mockMvc.perform(post("/invoices").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("2031-08")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void payInvoice_alreadyPaid_returns409() throws Exception {
        mockMvc.perform(patch("/invoices/1/payer"))
                .andExpect(status().isConflict());
    }

    @Test
    void emettreInvoice_brouillon_returns200_thenCannotBeDeleted() throws Exception {
        mockMvc.perform(patch("/invoices/3/emettre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EMISE"));
        mockMvc.perform(delete("/invoices/3"))
                .andExpect(status().isConflict());
        verify(notificationPublisher).publier(any());
    }

    @Test
    void emettreInvoice_publishFailure_doesNotBreakResponse() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.amqp.AmqpConnectException(
                new java.net.ConnectException("broker down")))
                .when(notificationPublisher).publier(any());

        mockMvc.perform(patch("/invoices/3/emettre"))
                .andExpect(status().isOk());
    }

    @Test
    void emettreInvoice_invalidTransition_publishesNothing() throws Exception {
        mockMvc.perform(patch("/invoices/1/emettre"))
                .andExpect(status().isConflict());
        verify(notificationPublisher, never()).publier(any());
    }

    private String body(String periode) {
        return """
                {"subscriptionId":1,"clientId":1,"periode":"%s","echeance":"2031-12-01",
                 "lignes":[{"libelle":"Test","quantite":1,"prixUnitaire":100.00}]}
                """.formatted(periode);
    }
}