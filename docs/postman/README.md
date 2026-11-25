# Bill-Flex Postman collections

## Collection V6

Import `BillFlex-v6.0.postman_collection.json` and keep the default `baseUrl` at `http://localhost:8080`. Requests go through the Gateway; the Actuator check targets `service-facturation` directly at port 8082. The collection generates a fresh `Idempotency-Key` for each first creation and saves it for its replay request. It also picks an unused period for subscription 3 from the live invoice list.

Run the folders in this order:

1. **A-D - Idempotence des factures:** create a draft (201), replay the exact key/body (201, same ID and `Idempotent-Replayed: true`), GET the invoice list (one invoice for that period), use the same key with a changed body (409), then omit the key (400).
2. **E - Idempotence des paiements:** create a payment (201), replay it (same ID and replay header), then GET the payment list to confirm one payment for that client/reference.
3. **F1/F2 - Remote unavailable:** stop `service-abonnements`, run F1 (503 ApiError), restart the service, then run F2 with the same key/body (201). The failed remote check must not consume the key.
4. **G - Remote 404:** post an invoice with unknown subscription `999999` (404 ApiError; the request is not retried).
5. **H1/H2 - Circuit breaker:** stop `service-abonnements`, run H1 at least four times using the Collection Runner (503 each), then request H2. The `service-abonnements` breaker should report `OPEN`; restart the service and wait for its configured 5-second open-state delay before retrying normal calls.

Manual cases require an available Config Server, Eureka, Gateway, and business services; no Docker container is needed for these V6 cases. `config-repo/service-facturation.properties` must be available to Config Server before starting/restarting `service-facturation`.

## Collection V5

Import `BillFlex-v5.0.postman_collection.json` after starting Docker containers, Eureka, Config Server, the three business services, `notification-service`, and the Gateway. The collection variables are `baseUrl`, `notificationUrl`, `invoiceId`, `secondInvoiceId`, `newInvoicePeriod`, and `newInvoiceDueDate`.

Run the direct notification tests in folder A: valid email returns `204`; invalid email and empty `objet` return `400`; SMTP stopped (`docker stop mailpit`) returns `503`. Restart Mailpit with `docker start mailpit`.

For folder B, run requests in order:

1. **Lister les invoices et sélectionner des BROUILLON** gets invoice 3 from the checked-in invoice seed and calculates a currently unused `yyyy-MM` period for subscription 3.
2. **Créer une facture BROUILLON (pour le test différé)** posts a new invoice using subscription 3 and client 3. Both are `ACTIVE` in their checked-in service seed data. Its response ID is saved as `secondInvoiceId`.
3. **Émettre invoice - 200 et email** emits `invoiceId`; confirm `statut=EMISE` and inspect the email at `http://localhost:8025`.

For folder D, the full-flow request in folder B already emitted `invoiceId`. Restart `service-facturation` to reset its in-memory H2 database from `data.sql`, then rerun the GET and POST requests from folder B so both IDs refer to fresh `BROUILLON` invoices. Stop `notification-service`, run the two emission requests in folder D, and check RabbitMQ `notifications.email` for `Ready=2`. Restart `notification-service`, then check Mailpit for two emails and RabbitMQ for `Ready=0` / `Unacked=0`.

Folder C covers unknown invoice `404` and repeating an emission `409`. For folder E, restart `service-facturation` to reset its in-memory H2 database, then run only the GET and draft-creation POST from folder B (do not emit from folder B). Stop RabbitMQ with `docker stop rabbitmq`, emit `secondInvoiceId`, confirm HTTP `200` and the error log in `service-facturation`, then run `docker start rabbitmq`.

Folder F posts through `{{baseUrl}}/notification-service/notifications/email`; because the Gateway discovery locator excludes notification-service, expected status is `404`. The business routes should remain available through their normal `/service-facturation`, `/service-clients`, and `/service-abonnements` prefixes.

The Postman collection is untested in this environment. If import fails, paste the exact Postman import error and the line/field it reports.
