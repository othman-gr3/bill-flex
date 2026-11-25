package com.billflex.facturation;

import com.billflex.facturation.dto.NotificationEmailDto;
import com.billflex.facturation.feignclient.ClientDto;
import com.billflex.facturation.feignclient.ResilientRequests;
import com.billflex.facturation.messaging.NotificationPublisher;
import com.billflex.facturation.model.Invoice;
import com.billflex.facturation.model.StatutInvoice;
import com.billflex.facturation.repository.InvoiceRepository;
import com.billflex.facturation.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceNotificationTransactionTest {

    @Mock
    private InvoiceRepository repository;

    @Mock
    private ResilientRequests resilientRequests;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private InvoiceService invoiceService;

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoice = new Invoice();
        invoice.setId(42L);
        invoice.setClientId(9L);
        invoice.setPeriode("2026-11");
        invoice.setMontantTTC(new BigDecimal("120.00"));
        invoice.setEcheance(LocalDate.of(2026, 12, 5));
        invoice.setStatut(StatutInvoice.BROUILLON);

        when(repository.findById(42L)).thenReturn(Optional.of(invoice));
        when(repository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(resilientRequests.getClientStrict(9L))
                .thenReturn(new ClientDto(9L, "Client Exemple", "client@example.com",
                        "MA12345678", "VIREMENT", "ACTIF"));
    }

    @Test
    void rollback_doesNotPublishNotification() {
        TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());
        transaction.execute(status -> {
            invoiceService.emettre(42L);
            status.setRollbackOnly();
            return null;
        });

        verify(notificationPublisher, never()).publier(any(NotificationEmailDto.class));
    }

    @Test
    void afterCommit_publishesOnceWithExpectedEmailFields() {
        TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());
        transaction.execute(status -> {
            invoiceService.emettre(42L);
            verify(notificationPublisher, never()).publier(any(NotificationEmailDto.class));
            return null;
        });

        ArgumentCaptor<NotificationEmailDto> captor = ArgumentCaptor.forClass(NotificationEmailDto.class);
        verify(notificationPublisher, times(1)).publier(captor.capture());

        NotificationEmailDto notification = captor.getValue();
        assertEquals("client@example.com", notification.destinataire());
        assertEquals("Facture n° 42 émise", notification.objet());
        assertTrue(notification.contenu().contains("Client Exemple"));
        assertTrue(notification.contenu().contains("42"));
        assertTrue(notification.contenu().contains("2026-11"));
        assertTrue(notification.contenu().contains("120.00"));
        assertTrue(notification.contenu().contains("2026-12-05"));
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
