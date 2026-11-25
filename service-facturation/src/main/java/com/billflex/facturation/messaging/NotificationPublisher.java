package com.billflex.facturation.messaging;

import com.billflex.facturation.config.RabbitConfig;
import com.billflex.facturation.dto.NotificationEmailDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public void publier(NotificationEmailDto notification) {
        rabbitTemplate.convertAndSend(RabbitConfig.QUEUE_EMAIL, notification);
        log.info("Notification publiée dans la file {} : objet={}",
                RabbitConfig.QUEUE_EMAIL, notification.objet());
    }
}
