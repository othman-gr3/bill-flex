package com.billflex.notification.messaging;

import com.billflex.notification.config.RabbitConfig;
import com.billflex.notification.dto.NotificationEmailDto;
import com.billflex.notification.service.NotificationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final Validator validator;

    @RabbitListener(queues = RabbitConfig.QUEUE_EMAIL)
    public void recevoir(NotificationEmailDto dto) {
        log.info("Notification reçue depuis la file {} : objet={}",
                RabbitConfig.QUEUE_EMAIL, dto.objet());
        Set<ConstraintViolation<NotificationEmailDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        notificationService.envoyerEmail(dto);
    }
}
