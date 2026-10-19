package com.billflex.clients;

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
class ClientsApiTest {

    @Autowired
    private MockMvc mockMvc;

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
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void payPayment_alreadyPaid_returns409() throws Exception {
        mockMvc.perform(patch("/payments/1/payer"))
                .andExpect(status().isConflict());
    }
}
