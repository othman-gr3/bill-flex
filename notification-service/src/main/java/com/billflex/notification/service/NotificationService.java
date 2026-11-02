package com.billflex.notification.service;

import com.billflex.notification.dto.NotificationEmailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    public void envoyerEmail(NotificationEmailDto dto) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@billflex.com");
        message.setTo(dto.destinataire());
        message.setSubject(dto.objet());
        message.setText(dto.contenu());
        mailSender.send(message);
    }
}
