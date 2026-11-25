# V5 screenshot checklist

Capture the following evidence after starting the services and Docker containers. Save each image with the exact filename shown:

| Screenshot | File |
|---|---|
| Mailpit showing the received invoice email | `mailpit-email-recu.png` |
| RabbitMQ `notifications.email` with `Ready = 2` while the consumer is stopped | `rabbitmq-queue-ready-2.png` |
| RabbitMQ after restarting the consumer, with `Ready = 0` and `Unacked = 0` | `rabbitmq-queue-apres-redemarrage.png` |
| Postman successful invoice emission with HTTP 200 and `EMISE` | `postman-emettre-200.png` |
| Postman unknown invoice with HTTP 404 | `postman-emettre-404.png` |
| Postman invalid transition with HTTP 409 | `postman-emettre-409.png` |
| Postman invalid notification request with HTTP 400 | `postman-notification-400.png` |
| Postman SMTP failure with HTTP 503 | `postman-notification-503.png` |
| Config Server response showing `notification-service.properties` | `config-server-notification-service.png` |
| Exported V5 Mermaid architecture diagram | `architecture-v5.png` |

Expected capture locations:

```text
docs/screenshots/mailpit-email-recu.png
docs/screenshots/rabbitmq-queue-ready-2.png
docs/screenshots/rabbitmq-queue-apres-redemarrage.png
docs/screenshots/postman-emettre-200.png
docs/screenshots/postman-emettre-404.png
docs/screenshots/postman-emettre-409.png
docs/screenshots/postman-notification-400.png
docs/screenshots/postman-notification-503.png
docs/screenshots/config-server-notification-service.png
docs/screenshots/architecture-v5.png
```

The image files are intentionally not generated here. Add them after the manual demonstration; until then, the README references this checklist as `[À COMPLÉTER: capture d'écran]`.
