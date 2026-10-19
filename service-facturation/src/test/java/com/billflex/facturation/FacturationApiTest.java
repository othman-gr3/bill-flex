package com.billflex.facturation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FacturationApiTest {

    @Autowired
    private MockMvc mockMvc;

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
}
