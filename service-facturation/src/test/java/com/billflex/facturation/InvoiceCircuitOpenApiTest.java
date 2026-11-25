package com.billflex.facturation;

import com.billflex.facturation.feignclient.ResilientRequests;
import com.billflex.facturation.messaging.NotificationPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class InvoiceCircuitOpenApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResilientRequests resilientRequests;

    @MockitoBean
    private NotificationPublisher notificationPublisher;

    @Test
    void openCircuit_returnsClean503() throws Exception {
        when(resilientRequests.getSubscriptionStrict(anyLong()))
                .thenThrow(mock(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class));

        mockMvc.perform(post("/invoices")
                        .header("Idempotency-Key", "invoice-open-circuit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subscriptionId":10,"clientId":1,"periode":"2031-11","echeance":"2031-12-05",
                                 "lignes":[{"libelle":"Abonnement","quantite":1,"prixUnitaire":50.00}]}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("Service distant momentanément indisponible"));
    }
}
