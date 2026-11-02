package com.billflex.notification.messaging;

import com.billflex.notification.config.RabbitConfig;
import com.billflex.notification.dto.NotificationEmailDto;
import com.billflex.notification.service.NotificationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final Validator validator;

    @RabbitListener(queues = RabbitConfig.QUEUE_EMAIL)
    public void recevoir(NotificationEmailDto dto) {
        Set<ConstraintViolation<NotificationEmailDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        notificationService.envoyerEmail(dto);
    }
}
