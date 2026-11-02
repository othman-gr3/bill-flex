package com.billflex.facturation.messaging;

import com.billflex.facturation.config.RabbitConfig;
import com.billflex.facturation.dto.NotificationEmailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publier(NotificationEmailDto notification) {
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_EMAIL, notification);
    }
}
