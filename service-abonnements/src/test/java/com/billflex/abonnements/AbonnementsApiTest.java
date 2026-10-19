package com.billflex.abonnements;

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
class AbonnementsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listPlans_returns200() throws Exception {
        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk());
    }

    @Test
    void getPlan_missing_returns404() throws Exception {
        mockMvc.perform(get("/plans/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPlan_valid_returns201() throws Exception {
        String body = """
                {"nom":"Premium","prixMensuel":149.00,"quotaUtilisateurs":50,"actif":true}
                """;
        mockMvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    void createPlan_invalid_returns400() throws Exception {
        String body = """
                {"nom":"","prixMensuel":-5,"quotaUtilisateurs":0,"actif":true}
                """;
        mockMvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.nom").exists())
                .andExpect(jsonPath("$.details.quotaUtilisateurs").exists());
    }

    @Test
    void createPlan_duplicateName_returns409() throws Exception {
        String body = """
                {"nom":"Starter","prixMensuel":10.00,"quotaUtilisateurs":1,"actif":true}
                """;
        mockMvc.perform(post("/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createSubscription_unknownPlan_returns404() throws Exception {
        String body = """
                {"planId":999,"clientId":1,"dateDebut":"2026-11-01"}
                """;
        mockMvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void createSubscription_valid_returns201_andNextDueDateIsOneMonthLater() throws Exception {
        String body = """
                {"planId":1,"clientId":3,"dateDebut":"2026-11-01"}
                """;
        mockMvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACTIVE"))
                .andExpect(jsonPath("$.prochaineEcheance").value("2026-12-01"));
    }

    @Test
    void resilierSubscription_twice_returns200_then409() throws Exception {
        mockMvc.perform(patch("/subscriptions/3/resilier"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RESILIEE"));
        mockMvc.perform(patch("/subscriptions/3/resilier"))
                .andExpect(status().isConflict());
    }
}
