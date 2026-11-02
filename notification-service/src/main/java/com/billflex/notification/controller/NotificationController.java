package com.billflex.notification.controller;

import com.billflex.notification.dto.NotificationEmailDto;
import com.billflex.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/email")
    public ResponseEntity<Void> envoyerEmail(@Valid @RequestBody NotificationEmailDto dto) {
        notificationService.envoyerEmail(dto);
        return ResponseEntity.noContent().build();
    }
}
