package com.billflex.notification.messaging;

import com.billflex.notification.dto.NotificationEmailDto;
import com.billflex.notification.service.NotificationService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private NotificationService notificationService;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void validDto_sendsEmail() {
        NotificationEmailDto dto = new NotificationEmailDto(
                "client@example.com", "Facture n° 42 émise", "Votre facture est disponible.");

        NotificationConsumer notificationConsumer = new NotificationConsumer(notificationService, validator);
        notificationConsumer.recevoir(dto);

        verify(notificationService).envoyerEmail(dto);
    }

    @Test
    void invalidDto_throwsConstraintViolationAndDoesNotSend() {
        NotificationEmailDto dto = new NotificationEmailDto("", "", "");
        NotificationConsumer notificationConsumer = new NotificationConsumer(notificationService, validator);

        assertThrows(ConstraintViolationException.class, () -> notificationConsumer.recevoir(dto));
        verifyNoInteractions(notificationService);
    }
}
