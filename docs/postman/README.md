# Bill-Flex Postman collections

Import `BillFlex-v5.0.postman_collection.json` after starting Eureka, Config Server, the three business services, `notification-service`, the Gateway, RabbitMQ, and Mailpit.

Run the direct notification requests first:

1. valid email: expected `204`;
2. invalid email and empty `objet`: expected `400`;
3. stop Mailpit with `docker stop mailpit`, then run the SMTP failure request: expected `503`;
4. restart Mailpit with `docker start mailpit`.

For the full flow, run `Get invoices and select BROUILLON`. Its test script stores the first `BROUILLON` identifier in the collection variable `invoiceId`. Then run the emission request and check the email manually in `http://localhost:8025`.

The deferred-processing and broker-down cases cannot be automated by this collection because they require stopping and restarting local processes. Follow the descriptions on those requests: stop `notification-service`, emit two `BROUILLON` invoices and verify `Ready = 2`, restart it, then verify two emails and `Ready = 0` / `Unacked = 0`. For the broker case, stop `rabbitmq`, emit an invoice, verify HTTP `200` and the producer error log, then run `docker start rabbitmq`.

The Postman collection is untested in this environment. If import fails, paste the exact Postman import error and the line/field it reports.
