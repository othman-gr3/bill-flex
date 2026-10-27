package com.billflex.facturation;

import com.billflex.facturation.feignclient.AbonnementsClient;
import com.billflex.facturation.feignclient.ClientDto;
import com.billflex.facturation.feignclient.ClientsClient;
import com.billflex.facturation.feignclient.PlanDto;
import com.billflex.facturation.feignclient.SubscriptionDto;
import feign.FeignException;
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

    // Remote services are mocked: tests need neither Eureka nor other services
    @MockitoBean
    private AbonnementsClient abonnementsClient;

    @MockitoBean
    private ClientsClient clientsClient;

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
        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body))
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
        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body))
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
        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvoice_subscriptionSuspended_returns409() throws Exception {
        when(abonnementsClient.getSubscription(anyLong()))
                .thenReturn(new SubscriptionDto(1L, 1L, 2L, "SUSPENDUE"));

        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body("2031-01")))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvoice_clientSuspended_returns409() throws Exception {
        when(clientsClient.getClient(anyLong()))
                .thenReturn(new ClientDto(1L, "Test", "t@t.com", "MA12345678", "VIREMENT", "SUSPENDU"));

        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body("2031-02")))
                .andExpect(status().isConflict());
    }

    @Test
    void createInvoice_subscriptionNotFound_returns404() throws Exception {
        Request req = Request.create(Request.HttpMethod.GET, "/subscriptions/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(abonnementsClient.getSubscription(anyLong()))
                .thenThrow(new FeignException.NotFound("not found", req, null, null));

        mockMvc.perform(post("/invoices").contentType(MediaType.APPLICATION_JSON).content(body("2031-03")))
                .andExpect(status().isNotFound());
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
    }

    private String body(String periode) {
        return """
                {"subscriptionId":1,"clientId":1,"periode":"%s","echeance":"2031-12-01",
                 "lignes":[{"libelle":"Test","quantite":1,"prixUnitaire":100.00}]}
                """.formatted(periode);
    }
}