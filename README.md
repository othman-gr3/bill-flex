# Bill-Flex

Projet de fin de module (PFM) réalisé par **Othmane Lamdala**, **Hiba Zakari** et **Walid Louane**.

Bill-Flex est une plateforme de **facturation récurrente pour un SaaS B2B**, construite sur une architecture microservices (Java, Spring Boot, Spring Cloud). Au renouvellement mensuel d'un abonnement, une facture est générée selon le plan souscrit, envoyée au client, encaissée, puis relancée automatiquement en cas d'impayé.

L'unité de traitement est la **Facture**, dont le cycle de vie est :

`BROUILLON → EMISE → PAYEE / EN_RETARD → ANNULEE`

> Une facture = 1 abonnement + 1 client + 1 période + 1 montant TTC

Le projet est composé de trois services métier :

| Service | Rôle |
|---|---|
| `service-abonnements` | Catalogue des plans et gestion des abonnements clients |
| `service-facturation` (service principal) | Génération et cycle de vie des factures et de leurs lignes |
| `service-clients` | Comptes clients (entreprises) et paiements |

Ces services sont enregistrés dans un serveur **Eureka** (découverte de services), exposés via une **API Gateway** (point d'entrée unique) et configurés de façon centralisée par un **Config Server** (dossier `config-repo`).

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

Le projet est composé de six applications Spring Boot :

```mermaid
flowchart TB
    U([Client / Frontend])

    GW["<b>gateway-service</b><br/>Spring Cloud Gateway<br/>Port 8080"]
    EU["<b>eureka-server</b><br/>Netflix Eureka<br/>Port 8761"]
    CS["<b>config-server</b><br/>Spring Cloud Config<br/>Port 8888"]
    GIT[("config-repo<br/>(GitHub, branche main)")]

    subgraph MS [Services métier]
        direction LR
        SA["<b>service-abonnements</b><br/>Plans et abonnements<br/>Port 8081"]
        SF["<b>service-facturation</b><br/>Factures et lignes<br/>Port 8082"]
        SC["<b>service-clients</b><br/>Clients et paiements<br/>Port 8083"]
    end

    U --> GW
    GW --> SA
    GW --> SF
    GW --> SC

    SA -. enregistrement .-> EU
    SF -. enregistrement .-> EU
    SC -. enregistrement .-> EU
    GW -. découverte .-> EU

    SA -. configuration .-> CS
    SF -. configuration .-> CS
    SC -. configuration .-> CS
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

## Configuration centralisée

Le dossier `config-repo/` (versionné sur la branche `main`) contient :

| Fichier | Contenu |
|---|---|
| `application.properties` | Configuration **globale** partagée (URL Eureka, exposition actuator) |
| `service-abonnements.properties` | Port 8081, base H2 `abonnementsdb` |
| `service-facturation.properties` | Port 8082, base H2 `facturationdb` |
| `service-clients.properties` | Port 8083, base H2 `clientsdb` |
| `gateway-service.properties` | Port 8080, routage dynamique |

Chaque service et la Gateway ne gardent en local que `spring.application.name` et `spring.config.import=configserver:http://localhost:8888`. `eureka-server` et `config-server` gardent leur configuration locale (problème d'amorçage).

Preuve d'agrégation : `GET http://localhost:8888/service-clients/default` renvoie deux `propertySources` : `service-clients.properties` puis `application.properties` (global).

## Démarrage

Ordre de lancement (IntelliJ, une configuration *Spring Boot* par application) :

1. `eureka-server` (8761), tableau de bord : http://localhost:8761
2. `config-server` (8888) : http://localhost:8888/service-clients/default
3. `service-abonnements` (8081), `service-facturation` (8082), `service-clients` (8083)
4. `gateway-service` (8080)

Les services et la Gateway refusent de démarrer si le Config Server est injoignable (comportement voulu, *fail-fast*).

Accès aux API via la Gateway (routage dynamique, préfixe = nom du service en minuscules) :

| URL | Service |
|---|---|
| http://localhost:8080/service-abonnements/plans | `service-abonnements` |
| http://localhost:8080/service-abonnements/subscriptions | `service-abonnements` |
| http://localhost:8080/service-facturation/invoices | `service-facturation` |
| http://localhost:8080/service-clients/clients | `service-clients` |
| http://localhost:8080/service-clients/payments | `service-clients` |

Routage statique (optionnel) : lancer la Gateway avec le profil `statique` (fichier `config-repo/gateway-service-statique.properties`), les chemins sont alors exposés sans préfixe (`/clients`, `/plans`, `/invoices`, ...).

## Stack technique

Java 17, Spring Boot 3.5.6, Spring Cloud 2025.0.0 (Eureka, Gateway reactive, Config Server), Maven.
