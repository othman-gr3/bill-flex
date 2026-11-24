# Bill-Flex

Projet de fin de module (PFM) réalisé par **Othmane Lamdala**, **Hiba Zakari** et **Walid Louane**.

Bill-Flex est une plateforme de **facturation récurrente pour un SaaS B2B**, construite sur une architecture microservices (Java, Spring Boot, Spring Cloud). Au renouvellement mensuel d'un abonnement, une facture est générée selon le plan souscrit, envoyée au client, encaissée, puis relancée automatiquement en cas d'impayé.

L'unité de traitement est la **Facture**, dont le cycle de vie est :

`BROUILLON → EMISE → PAYEE / EN_RETARD → ANNULEE`

> Une facture = 1 abonnement + 1 client + 1 période + 1 montant TTC

Le projet est composé de sept applications Spring Boot et de deux conteneurs d'infrastructure locaux :

| Service | Rôle |
|---|---|
| `service-abonnements` | Catalogue des plans et gestion des abonnements clients |
| `service-facturation` (service principal) | Génération et cycle de vie des factures et de leurs lignes |
| `service-clients` | Comptes clients (entreprises) et paiements |
| `notification-service` | Consommation des emails RabbitMQ et envoi SMTP Mailpit (interne, non exposé par la Gateway) |

Les trois services métier sont enregistrés dans **Eureka** et exposés via une **API Gateway**. Toutes les applications sont configurées par le **Config Server** (dossier `config-repo`), sauf Eureka et Config Server eux-mêmes. `notification-service` reste interne : il ne s'enregistre pas dans Eureka et n'est pas routé par la Gateway.

## Diagramme de classes

Les flèches en pointillés (`..>`) représentent les dépendances entre services, sans préjuger du protocole de communication.

```mermaid
classDiagram
    direction LR

    namespace service_abonnements {
        class Plan {
            -Long id
            -String nom
            -BigDecimal prixMensuel
            -int quotaUtilisateurs
            -boolean actif
        }
        class Subscription {
            -Long id
            -Long planId
            -Long clientId
            -LocalDate dateDebut
            -LocalDate dateFin
            -LocalDate prochaineEcheance
            -SubscriptionStatus statut
        }
        class SubscriptionStatus {
            <<enumeration>>
            ACTIVE
            SUSPENDUE
            RESILIEE
        }
    }

    namespace service_facturation {
        class Invoice {
            -Long id
            -Long subscriptionId
            -Long clientId
            -String periode
            -BigDecimal montantHT
            -BigDecimal montantTTC
            -LocalDate echeance
            -InvoiceStatus statut
        }
        class InvoiceLine {
            -Long id
            -Long invoiceId
            -String libelle
            -int quantite
            -BigDecimal prixUnitaire
        }
        class InvoiceStatus {
            <<enumeration>>
            BROUILLON
            EMISE
            PAYEE
            EN_RETARD
            ANNULEE
        }
    }

    namespace service_clients {
        class Client {
            -Long id
            -String nom
            -String email
            -String numeroTVA
            -String modePaiement
            -String statut
        }
        class Payment {
            -Long id
            -Long invoiceId
            -LocalDate date
            -BigDecimal montant
            -String moyen
            -PaymentStatus statut
        }
        class PaymentStatus {
            <<enumeration>>
            EN_ATTENTE
            PAYE
            ECHOUE
        }
    }

    %% Relations internes à chaque service
    Plan "1" <-- "0..*" Subscription : souscrit à
    Invoice "1" *-- "1..*" InvoiceLine : contient
    Client "1" <-- "0..*" Payment : effectue

    Subscription --> SubscriptionStatus
    Invoice --> InvoiceStatus
    Payment --> PaymentStatus

    %% Dépendances entre services
    Subscription ..> Client : clientId
    Invoice ..> Subscription : subscriptionId
    Invoice ..> Client : clientId
    Payment ..> Invoice : invoiceId
```

## Architecture de projet

Le projet est composé de sept applications Spring Boot et de deux conteneurs d'infrastructure :

```mermaid
flowchart TB
    U([Client / Frontend])

    GW["<b>gateway-service</b><br/>Spring Cloud Gateway<br/>Port 8080"]
    EU["<b>eureka-server</b><br/>Netflix Eureka<br/>Port 8761"]
    CS["<b>config-server</b><br/>Spring Cloud Config<br/>Port 8888"]
    GIT[("config-repo<br/>(GitHub, branche main)")]
    RMQ["<b>RabbitMQ</b><br/>AMQP 5672 / UI 15672<br/>queue notifications.email"]
    MP["<b>Mailpit</b><br/>SMTP 1025 / UI 8025"]

    subgraph MS [Services métier]
        direction LR
        SA["<b>service-abonnements</b><br/>Plans et abonnements<br/>Port 8081"]
        SF["<b>service-facturation</b><br/>Factures et lignes<br/>Port 8082"]
        SC["<b>service-clients</b><br/>Clients et paiements<br/>Port 8083"]
        NS["<b>notification-service</b><br/>Interne, emails<br/>Port 8084"]
    end

    U --> GW
    GW --> SA
    GW --> SF
    GW --> SC
    SF --> RMQ
    RMQ --> NS
    NS --> MP

    SA -. enregistrement .-> EU
    SF -. enregistrement .-> EU
    SC -. enregistrement .-> EU
    GW -. découverte .-> EU

    SA -. configuration .-> CS
    SF -. configuration .-> CS
    SC -. configuration .-> CS
    NS -. configuration .-> CS
    GW -. configuration .-> CS
    CS --> GIT
```

| Application | Rôle | Port |
|---|---|---|
| `eureka-server` | Serveur de découverte de services | **8761** |
| `config-server` | Serveur de configuration centralisée (lit `config-repo`) | **8888** |
| `gateway-service` | Point d'entrée unique, routage vers les services | **8080** |
| `service-abonnements` | Plans et abonnements | **8081** |
| `service-facturation` | Factures et lignes de facturation (service principal) | **8082** |
| `service-clients` | Clients et paiements | **8083** |
| `notification-service` | Notifications email, consommateur interne (pas d'Eureka, pas de Gateway) | **8084** |
| `RabbitMQ` | Broker AMQP, queue durable `notifications.email` | **5672**, UI **15672** |
| `Mailpit` | SMTP local et interface de consultation | **1025**, UI **8025** |

## Communication asynchrone (RabbitMQ)

Lorsqu'une facture `BROUILLON` est émise par `PATCH /invoices/{id}/emettre`, `service-facturation` sauvegarde le statut `EMISE`, puis publie après le commit un message JSON dans la file durable `notifications.email`. `notification-service` consomme le message, valide explicitement le DTO et envoie l'email via SMTP à Mailpit. La réponse HTTP n'attend pas l'envoi de l'email.

```mermaid
sequenceDiagram
    participant C as Client
    participant G as Gateway :8080
    participant F as service-facturation :8082
    participant DB as Base de données
    participant R as RabbitMQ :5672
    participant N as notification-service :8084
    participant M as Mailpit SMTP :1025

    C->>G: PATCH /service-facturation/invoices/{id}/emettre
    G->>F: PATCH /invoices/{id}/emettre
    F->>DB: Sauvegarde EMISE
    DB-->>F: Commit
    F-->>G: 200 EMISE
    G-->>C: 200 EMISE
    F->>R: JSON notifications.email
    R->>N: Livraison du message
    N->>M: Envoi SMTP
```

## Configuration centralisée

Le dossier `config-repo/` (versionné sur la branche `main`) contient :

| Fichier | Contenu |
|---|---|
| `application.properties` | Configuration **globale** partagée (URL Eureka, exposition actuator et RabbitMQ local) |
| `service-abonnements.properties` | Port 8081, base H2 `abonnementsdb` |
| `service-facturation.properties` | Port 8082, base H2 `facturationdb` |
| `service-clients.properties` | Port 8083, base H2 `clientsdb` |
| `gateway-service.properties` | Port 8080, routage dynamique |
| `notification-service.properties` | Port 8084, Mailpit et retry RabbitMQ |

Chaque service et la Gateway ne gardent en local que `spring.application.name` et `spring.config.import=configserver:http://localhost:8888`. `eureka-server` et `config-server` gardent leur configuration locale (problème d'amorçage).

Le fichier `config-repo/notification-service.properties` contient le port 8084, la connexion SMTP Mailpit et les paramètres de retry RabbitMQ. `config-repo/application.properties` contient l'hôte, le port et les identifiants locaux RabbitMQ (`tp` / `tp123`). Le dépôt de configuration doit être poussé sur la branche lue par Config Server avant son démarrage.

Preuve d'agrégation : `GET http://localhost:8888/service-clients/default` renvoie deux `propertySources` : `service-clients.properties` puis `application.properties` (global).

## Démarrage

Ordre de lancement (IntelliJ, une configuration *Spring Boot* par application) :

1. Docker : `docker compose up -d` (ou les commandes `docker run` ci-dessous)
2. `eureka-server` (8761), tableau de bord : http://localhost:8761
3. `config-server` (8888) : http://localhost:8888/service-clients/default
4. `service-abonnements` (8081), `service-facturation` (8082), `service-clients` (8083)
5. `notification-service` (8084), puis `gateway-service` (8080)

Les services et la Gateway refusent de démarrer si le Config Server est injoignable (comportement voulu, *fail-fast*).

Accès aux API via la Gateway (routage dynamique, préfixe = nom du service en minuscules) :

| URL | Service |
|---|---|
| http://localhost:8080/service-abonnements/plans | `service-abonnements` |
| http://localhost:8080/service-abonnements/subscriptions | `service-abonnements` |
| http://localhost:8080/service-facturation/invoices | `service-facturation` |
| http://localhost:8080/service-clients/clients | `service-clients` |
| http://localhost:8080/service-clients/payments | `service-clients` |
| http://localhost:8084/notifications/email | `notification-service` (test manuel direct uniquement) |

## Docker

Le fichier `docker-compose.yml` existe dans le dépôt. Les commandes équivalentes sont :

```powershell
docker run -d --name mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit
docker run -d --name rabbitmq -p 127.0.0.1:5672:5672 -p 127.0.0.1:15672:15672 -e RABBITMQ_DEFAULT_USER=tp -e RABBITMQ_DEFAULT_PASS=tp123 -v tp-rabbitmq-data:/var/lib/rabbitmq rabbitmq:4-management
```

Après émission d'une facture (`PATCH /service-facturation/invoices/{id}/emettre`), la facture est sauvegardée puis le message JSON est publié après commit. Le consommateur envoie l'email sans bloquer la réponse HTTP. Arrêter `notification-service`, émettre deux factures, vérifier `Ready = 2` dans RabbitMQ, puis le redémarrer : les deux messages sont alors traités. Mailpit est visible sur http://localhost:8025 et RabbitMQ sur http://localhost:15672 (tp/tp123).

## Tester la notification

Requête métier exacte :

```http
PATCH http://localhost:8080/service-facturation/invoices/{id}/emettre
```

Pour la donnée de démonstration actuelle, l'invoice `3` est `BROUILLON` dans `service-facturation/src/main/resources/data.sql`. Une émission valide retourne `200` avec `statut: "EMISE"`. Le message est ensuite consommé et l'email est visible dans http://localhost:8025.

Pour tester le traitement différé :

1. Arrêter `notification-service`.
2. Émettre deux factures `BROUILLON` et vérifier `200` pour chacune.
3. Ouvrir http://localhost:15672 et vérifier `Ready = 2` dans `notifications.email`.
4. Redémarrer `notification-service`.
5. Vérifier les deux emails dans Mailpit, `Ready = 0` et `Unacked = 0`.

## Gestion des erreurs

| Situation | Résultat |
|---|---|
| Corps invalide ou champs `destinataire`, `objet`, `contenu` invalides sur le endpoint manuel | **400** avec `ApiError` |
| SMTP indisponible sur `POST /notifications/email` | **503** avec `ApiError` |
| Invoice introuvable | **404** |
| Transition invalide, par exemple émission d'une invoice déjà émise | **409**, aucun message publié |
| RabbitMQ indisponible après le commit | **200** pour l'émission, erreur AMQP journalisée par `service-facturation` |
| `notification-service` arrêté | Les messages restent dans la file durable jusqu'au redémarrage |

## Captures d'écran

Voir la checklist et les noms de fichiers dans [`docs/screenshots/README-V5.md`](docs/screenshots/README-V5.md).

> [À COMPLÉTER : ajouter les captures après la démonstration manuelle.]

![Email reçu dans Mailpit](docs/screenshots/mailpit-email-recu.png)
![File RabbitMQ avec Ready = 2](docs/screenshots/rabbitmq-queue-ready-2.png)
![File RabbitMQ après redémarrage](docs/screenshots/rabbitmq-queue-apres-redemarrage.png)
![Réponse Postman 200](docs/screenshots/postman-emettre-200.png)
![Réponse Postman 404](docs/screenshots/postman-emettre-404.png)
![Réponse Postman 409](docs/screenshots/postman-emettre-409.png)
![Réponse Postman notification 400](docs/screenshots/postman-notification-400.png)
![Réponse Postman notification 503](docs/screenshots/postman-notification-503.png)
![Configuration notification dans Config Server](docs/screenshots/config-server-notification-service.png)
![Architecture V5](docs/screenshots/architecture-v5.png)

Routage statique (optionnel) : lancer la Gateway avec le profil `statique` (fichier `config-repo/gateway-service-statique.properties`), les chemins sont alors exposés sans préfixe (`/clients`, `/plans`, `/invoices`, ...).

## Stack technique

Java 17, Spring Boot 3.5.6, Spring Cloud 2025.0.0 (Eureka, Gateway reactive, Config Server), Spring AMQP, RabbitMQ, Mailpit, Maven.
