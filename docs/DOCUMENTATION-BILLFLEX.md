# Bill-Flex: team documentation (Jalons 0 to 3)

How we built the Bill-Flex microservices project, step by step, and **why** each step was done that way, so that anyone in the team (or any AI assistant) can repeat the same method for the next jalons.

- **Authors:** Othmane Lamdala, Hiba Zakari, Walid Louane
- **Course:** J2EE avancée, 5th year IDSI (Dr. Salim Khalil)
- **Repository:** https://github.com/othman-gr3/bill-flex (branch `main`, tags `v1.0`, `v2.0`, `v3.0`)
- **Stack:** Java 17 (target), Spring Boot 3.5.6, Spring Cloud 2025.0.0, Maven multi-module, H2, IntelliJ IDEA

> Put this file in the repo as `docs/DOCUMENTATION-BILLFLEX.md` and commit it, so it travels with the code.

---

## Table of contents

1. [How to use this document](#1-how-to-use-this-document)
2. [The project in one page](#2-the-project-in-one-page)
3. [Team workflow and Git rules](#3-team-workflow-and-git-rules)
4. [Tools and environment setup](#4-tools-and-environment-setup)
5. [Jalon 0: README, UML and architecture](#5-jalon-0-readme-uml-and-architecture)
6. [Jalon 1: the three business services (v1.0)](#6-jalon-1-the-three-business-services-v10)
7. [Jalon 2: Eureka and Gateway (v2.0)](#7-jalon-2-eureka-and-gateway-v20)
8. [Jalon 3: Config Server (v3.0)](#8-jalon-3-config-server-v30)
9. [Jalon 5: RabbitMQ et notifications](#9-jalon-5-rabbitmq-et-notifications)
10. [Git, GitHub and GitHub Actions](#10-git-github-and-github-actions)
11. [The method: how to do the next jalon](#11-the-method-how-to-do-the-next-jalon)
12. [Likely next jalons and how to approach them](#12-likely-next-jalons-and-how-to-approach-them)
13. [Problems we hit and how we fixed them](#13-problems-we-hit-and-how-we-fixed-them)
14. [Glossary](#14-glossary)
15. [Prompt for your AI assistant](#15-prompt-for-your-ai-assistant)

---

## 1. How to use this document

- **Sections 2 to 4** are the big picture and the rules. Read them first.
- **Sections 5 to 8** are the history: for each jalon, what the professor asked, what we built, the exact steps, and the reasoning. Use them as a recipe.
- **Section 10** is the generic method to repeat for any new jalon.
- **Section 12** lists the real problems we hit. Read it before you start coding, it saves hours.
- **Section 14** is a ready-to-paste prompt that gives an AI assistant all the context of this project. Paste it at the start of every AI session.

The professor's rule to keep in mind: *the TP is practice (not handed in), the jalon is the graded step of our own project (PFM), delivered on Git with a tag.*

---

## 2. The project in one page

### 2.1 What Bill-Flex does

Recurring billing for a B2B SaaS. At each monthly renewal of a subscription, an invoice is generated from the plan, sent to the client, paid, and followed up automatically when unpaid.

The unit of processing is the **Invoice** (`Facture`). Lifecycle:

```
BROUILLON -> EMISE -> PAYEE / EN_RETARD -> ANNULEE
```

Rule: *one invoice = 1 subscription + 1 client + 1 period + 1 total amount TTC.*

### 2.2 Why we picked it

We compared three project sheets (Bill-Flex, Stock-Alert, Quick-Ship). All three had the same architecture (3 services, OpenFeign, Kafka). Bill-Flex won because it has real domain logic (amounts, VAT, a lifecycle with a state machine, money handled with `BigDecimal`), a strong CV story (Stripe-Billing-like), and a valuable bonus (scheduled batch plus an MRR dashboard).

### 2.3 Applications and ports

| Application | Folder | `spring.application.name` | Port | Added in |
|---|---|---|---|---|
| Plans and subscriptions | `service-abonnements` | `service-abonnements` | 8081 | v1.0 |
| Invoices (main service) | `service-facturation` | `service-facturation` | 8082 | v1.0 |
| Clients and payments | `service-clients` | `service-clients` | 8083 | v1.0 |
| Service registry | `eureka-server` | `eureka-server` | 8761 | v2.0 |
| API Gateway | `gateway-service` | `gateway-service` | 8080 | v2.0 |
| Config Server | `config-server` | `config-server` | 8888 | v3.0 |
| Notifications email | `notification-service` | `notification-service` | 8084 | v5.0 |

### 2.4 Domain model

| Service | Entity | Fields |
|---|---|---|
| `service-abonnements` | `Plan` | id, nom, prixMensuel, quotaUtilisateurs, actif |
| `service-abonnements` | `Subscription` | id, plan, clientId, dateDebut, dateFin, prochaineEcheance, statut |
| `service-facturation` | `Invoice` | id, subscriptionId, clientId, periode, montantHT, montantTTC, echeance, statut, lignes |
| `service-facturation` | `InvoiceLine` | id, invoice, libelle, quantite, prixUnitaire |
| `service-clients` | `Client` | id, nom, email, numeroTVA, modePaiement, statut |
| `service-clients` | `Payment` | id, client, invoiceId, date, montant, moyen, statut |

**Key design rule:** a service never has a JPA relation to an entity of another service. It only stores the other entity's id (`clientId`, `planId`, `subscriptionId`, `invoiceId`). Inside one service, normal JPA relations are fine (`Subscription -> Plan`, `Invoice -> InvoiceLine`, `Payment -> Client`). Why: each service owns its data and can be deployed alone, which is the whole point of microservices.

### 2.4.1 Statuses

| Enum | Values |
|---|---|
| `StatutInvoice` | BROUILLON, EMISE, PAYEE, EN_RETARD, ANNULEE |
| `StatutSubscription` | ACTIVE, SUSPENDUE, RESILIEE |
| `StatutPayment` | EN_ATTENTE, PAYE, ECHOUE |
| `StatutClient` | ACTIF, SUSPENDU |
| `ModePaiement` | CARTE, VIREMENT, PRELEVEMENT |

### 2.5 Final repository structure (v3.0)

```
bill-flex/
├── pom.xml                      (parent, packaging pom, 6 modules)
├── README.md                    (description, UML, architecture, start order)
├── .gitignore
├── docs/
│   ├── DOCUMENTATION-BILLFLEX.md   (this file)
│   ├── postman/                    (BillFlex-v1.0 / v2.0 / v3.0 collections)
│   └── screenshots/
├── config-repo/                 (central configuration, read by config-server)
│   ├── application.properties
│   ├── service-abonnements.properties
│   ├── service-facturation.properties
│   ├── service-clients.properties
│   ├── gateway-service.properties
│   └── gateway-service-statique.properties
├── eureka-server/               (8761)
├── config-server/               (8888)
├── gateway-service/             (8080)
├── service-abonnements/         (8081)
├── service-facturation/         (8082)
└── service-clients/             (8083)
└── notification-service/        (8084, interne)
```

Each business service has this internal layout:

```
src/main/java/com/billflex/<service>/
├── <Service>Application.java
├── controller/     REST controllers (HTTP only)
├── service/        business rules and transactions
├── repository/     Spring Data JPA interfaces
├── model/          JPA entities and enums
├── dto/            Request/Response records and ApiError
└── exception/      custom exceptions and GlobalExceptionHandler
src/main/resources/application.properties, data.sql
src/test/java/com/billflex/<service>/<Service>ApiTest.java
src/test/resources/application.properties        (from Jalon 3)
```

### 2.6 What each jalon delivered

| Jalon | Tag | Deliverable (professor) | What we added |
|---|---|---|---|
| 0 | none | README with description, UML class diagram, architecture schema (names and ports) | `README.md` |
| 1 | `v1.0` | Business services with Controller/Service/Repository, validated DTOs, REST API, HTTP codes, error handling, Postman or Swagger tests | 3 services, tests, Postman v1.0 |
| 2 | `v2.0` | Eureka server, Gateway with dynamic routing, updated architecture schema | `eureka-server`, `gateway-service`, Postman v2.0 |
| 3 | `v3.0` | `config-repo` (global file plus one per service), Config Server, services and Gateway consuming central config | `config-repo`, `config-server`, 2-line local config, Postman v3.0 |

---

## 3. Team workflow and Git rules

### 3.1 Rules we follow

1. **One repository**, one branch of reference: `main`.
2. **One commit and one tag per jalon**, created only when the jalon is finished and tested. Commit messages are fixed:

   | Jalon | Commit message | Tag |
   |---|---|---|
   | 0 | `Jalon 0 - README, diagramme de classes, architecture` | none |
   | 1 | `V1 - services métier` | `v1.0` |
   | 2 | `V2 - Eureka et Gateway` | `v2.0` |
   | 3 | `V3 - Config Server et config-repo` | `v3.0` |

3. **Never start the next jalon before the previous tag is on GitHub and verified.** Later jalons modify files of earlier ones (poms, `application.properties`), and a clean tag per step is what the professor checks.
4. **Tag only from `main`**, after everything is merged and tested.
5. **Verify every tag** (see 3.3). We moved `v1.0` once because it pointed to an old commit.
6. **Pull before you work, pull before you push.**
7. Never commit `target/`, `.idea/`, `*.iml`, logs, or secrets (tokens, passwords).

### 3.2 Who does what: one owner per jalon

Each teammate **owns one jalon from start to finish**: reads the professor's sheet, implements it in every module it touches, tests it, updates Postman, README and this documentation, then commits and tags. Nobody owns a service, because every jalon crosses all the services (Jalon 2 touched the 3 services plus 2 new modules, Jalon 3 touched everything again).

| Jalon | Owner | Branch |
|---|---|---|
| 1 to 3 | Othmane | done, tagged `v1.0`, `v2.0`, `v3.0` |
| 4 | _to fill_ | `jalon-4` |
| 5 | _to fill_ | `jalon-5` |

**The one constraint: jalons are sequential.** Jalon N+1 starts from the tag of Jalon N, because it modifies files that the previous jalon created (poms, `application.properties`, `config-repo`, the README). Two people changing the same files in parallel produce conflicts and code that does not build. So:

1. **Hand-off checklist.** The next owner starts only when the previous owner has: tagged and verified the tag (3.3), run `mvn clean verify` green, started the whole system, added the documentation section, and announced it in the team chat.
2. **While waiting**, the next owner can already read the sheet, run the whole project locally, and prepare the "changes only" list with the AI prompt of section 14. Nothing is pushed to `main` before the previous tag exists.
3. **Branch per jalon** (`jalon-4`, `jalon-5`, ...), merged into `main` through a Pull Request, then tagged from `main`.
4. **The two others review the Pull Request** and must *run* the project (start order of section 8.7), not only read the diff.
5. **Only the current owner edits the root `pom.xml`, `config-repo/` and the README.** Everyone else leaves them alone until the tag is out.
6. **Everyone must understand the whole project**, not only their jalon: before starting yours, run the full system once and read sections 2, 6, 7 and 8. A reviewer who does not know the project cannot review.

### 3.3 The commands we used

```powershell
# one-time identity on a new PC (git refuses to commit without it)
git config --global user.name "Your Name"
git config --global user.email "your-github-email@example.com"

# end of a jalon
git add .
git status                      # check that target/ and .idea/ are NOT listed
git commit -m "V1 - services métier"
git push origin main
git tag -a v1.0 -m "Jalon 1 - services métier"
git push origin v1.0

# verify the tag points to your latest commit: both hashes must be identical
git rev-list -n 1 v1.0
git log --oneline -1
```

If a tag points to the wrong commit (our case with `v1.0`), move it:

```powershell
git tag -d v1.0
git push origin --delete v1.0
git tag -a v1.0 -m "Jalon 1 - services métier" <commit-hash>
git push origin v1.0
```

Moving a tag is acceptable only because it is our own repository and nothing was graded yet. Teammates who already fetched the old tag run `git fetch --tags --force`.

### 3.4 Working on another PC

```powershell
git fetch --all --tags --force
git pull --rebase origin main
git log --oneline -5
git tag
```
Then in IntelliJ: *File -> Reload All from Disk*, Maven tool window -> *Reload All Maven Projects*, *Build -> Rebuild Project*.

---

## 4. Tools and environment setup

| Tool | Version | Why |
|---|---|---|
| JDK | 17 or newer (project targets 17; a newer JDK works) | Spring Boot 3 requires 17+ |
| IntelliJ IDEA | Community or Ultimate | The professor's TPs are done in IntelliJ |
| Maven | bundled in IntelliJ | no separate install needed |
| Git | any recent | version control |
| Postman | any | API tests required by Jalon 1 |
| Browser | any | Eureka dashboard, Swagger, H2 console, Config Server JSON |

Setup checklist on a new PC:

1. Install JDK and Git. Set your Git identity (3.3).
2. IntelliJ: *File -> New -> Project from Version Control*, paste `https://github.com/othman-gr3/bill-flex.git`, sign in to GitHub when asked.
3. *File -> Project Structure -> Project*: set the SDK.
4. Open the root `pom.xml` as a project, *Reload All Maven Projects*.
5. *Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable annotation processing* (needed for Lombok).
6. Run `mvn clean verify` (or right-click each `src/test` -> Run Tests). It must be green before you change anything.

Useful URLs once everything runs:

| What | URL |
|---|---|
| Eureka dashboard | http://localhost:8761 |
| Config Server (example) | http://localhost:8888/service-clients/default |
| Swagger UI (per service) | http://localhost:8081/swagger-ui/index.html (8082, 8083) |
| H2 console | http://localhost:8083/h2-console (JDBC URL `jdbc:h2:mem:clientsdb`, user `sa`, empty password) |
| Gateway example | http://localhost:8080/service-clients/clients |

---

## 5. Jalon 0: README, UML and architecture

### 5.1 What the professor asked

In the `README.md` of the PFM repository:

1. A short description of the project.
2. A global **UML class diagram** with the classes of each business service and their relations. Interactions between services are drawn as **dotted dependency arrows** (`- - ->`), without saying which protocol is used.
3. An **architecture schema** with the names and ports of the applications (business services, Eureka, Gateway).

### 5.2 How we did it

1. We wrote the README in French (the professor's example is in French) with three parts: description, class diagram, architecture.
2. **Diagrams as Mermaid code** inside the README. GitHub renders Mermaid directly, so no image export is needed, and the diagram is versioned as text. If the professor insists on images, export them from https://mermaid.live.
3. **Class diagram:** one `namespace` block per service. Relations inside a service are normal associations (`Plan <-- Subscription`, `Invoice *-- InvoiceLine`, `Client <-- Payment`). Relations between services are dotted dependencies carrying the id name:
   ```
   Subscription ..> Client : clientId
   Invoice ..> Subscription : subscriptionId
   Invoice ..> Client : clientId
   Payment ..> Invoice : invoiceId
   ```
4. **Architecture schema:** a `flowchart` with the applications, their ports, and the dotted "enregistrement / découverte / configuration" links, plus a table `Application | Role | Port`.
5. The README is **updated at every jalon** (Gateway in Jalon 2, Config Server in Jalon 3, start order, URLs). The deliverable of Jalon 2 explicitly asks for an updated architecture schema.

### 5.3 Why

- Mermaid in the README keeps documentation and code in the same place and diff-able.
- The dotted arrows show *dependencies between services* without committing to REST or Kafka yet, as the professor requires.

---

## 6. Jalon 1: the three business services (v1.0)

### 6.1 What the professor asked

Business services in separate IntelliJ modules, each an independent Spring Boot app, respecting **Controller / Service / Repository** layers, **validated DTOs**, each managing **its own data**. Working REST APIs with proper HTTP codes and error handling. **Postman or Swagger tests** covering valid cases, validation errors and missing resources. Code on Git with tag `v1.0`.

### 6.2 Step 1: the Maven multi-module skeleton

**Why multi-module:** one repository, one build, but each service is its own deployable module with its own `pom.xml`.

1. Clone the (empty) GitHub repo, open it in IntelliJ.
2. Create the root `pom.xml` (packaging `pom`). It does two jobs:
   - inherits from `spring-boot-starter-parent` 3.5.6 (managed versions of Spring libraries);
   - imports the `spring-cloud-dependencies` BOM 2025.0.0 (managed versions of all Spring Cloud libraries, so modules never declare a Spring Cloud version).

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.6</version>
    <relativePath/>
</parent>

<groupId>com.billflex</groupId>
<artifactId>bill-flex</artifactId>
<version>1.0.0</version>
<packaging>pom</packaging>

<properties>
    <java.version>17</java.version>
    <spring-cloud.version>2025.0.0</spring-cloud.version>
</properties>

<modules>
    <module>service-abonnements</module>
    <module>service-facturation</module>
    <module>service-clients</module>
</modules>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

3. Create each module: right-click the root -> *New -> Module -> Maven* (see the warning below), Parent = `bill-flex`, GroupId `com.billflex`, ArtifactId = module name.

> **Warning (learned the hard way):** use the plain **Maven** generator, not *Spring Initializr*. Initializr creates its own package (for example `com.billflex.serviceclients`) with a second `@SpringBootApplication` and a default test. That second app starts a second Spring context on the same in-memory database and breaks the tests (see section 12). If it was generated, delete that package in both `src/main/java` and `src/test/java`.

4. Add a module to the root `<modules>` only once its folder exists, otherwise Maven fails with `Child module ... does not exist`.

### 6.3 Step 2: the service `pom.xml` (same for the 3 services)

| Dependency | Why |
|---|---|
| `spring-boot-starter-web` | REST controllers, embedded Tomcat |
| `spring-boot-starter-data-jpa` | JPA/Hibernate and Spring Data repositories |
| `spring-boot-starter-validation` | `@NotBlank`, `@Email`, `@Min`, ... on DTOs |
| `h2` (runtime) | in-memory database, one per service |
| `lombok` (optional) | removes getter/setter/constructor boilerplate |
| `springdoc-openapi-starter-webmvc-ui` 2.8.9 | Swagger UI, an alternative to Postman |
| `spring-boot-starter-test` (test) | JUnit 5, MockMvc |
| `spring-boot-maven-plugin` | runnable jar, `mvn spring-boot:run` |

Then *Reload All Maven Projects* and enable Lombok annotation processing.

### 6.4 Step 3: `application.properties` of a service (Jalon 1 version)

```properties
spring.application.name=service-clients
server.port=8083

spring.datasource.url=jdbc:h2:mem:clientsdb
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
spring.sql.init.encoding=UTF-8
spring.sql.init.data-locations=classpath:data.sql
```

Why these lines:
- One port and one database name **per service** (`clientsdb`, `abonnementsdb`, `facturationdb`): no shared database.
- `ddl-auto=create-drop`: Hibernate builds the schema from the entities at startup.
- `defer-datasource-initialization=true` is **mandatory** when you use `data.sql` with Hibernate: it makes `data.sql` run *after* Hibernate created the tables. Without it you get `Table "X" not found`.

### 6.5 Step 4: build each service in layers

Order that worked best: **exception -> model -> dto -> repository -> service -> controller -> main class -> resources -> test.**

**Model (entity)** and enum:

```java
@Entity
@Table(name = "clients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Client {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "numero_tva", nullable = false)   // explicit, see 6.8
    private String numeroTVA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModePaiement modePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutClient statut;
}
```
Why `EnumType.STRING`: readable data in the database, safe if the enum order changes.

**DTOs as Java records** with validation. The request DTO carries the constraints; the response DTO hides the entity:

```java
public record ClientRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}[A-Z0-9]{8,12}$",
                           message = "Numéro de TVA invalide (ex: MA12345678)") String numeroTVA,
        @NotNull ModePaiement modePaiement) {}

public record ClientResponse(Long id, String nom, String email, String numeroTVA,
                             ModePaiement modePaiement, StatutClient statut) {
    public static ClientResponse from(Client c) { /* map fields */ }
}
```
Why DTOs: the API contract does not depend on the database model, the client cannot set fields it must not set (id, statut, computed amounts), and validation lives at the boundary.

**Repository:** an interface extending `JpaRepository<Client, Long>`, plus derived queries such as `existsByEmail(String)` and `existsByEmailAndIdNot(String, Long)` (duplicate check on update).

**Service:** `@Service @Transactional`, uses the repository, throws business exceptions, maps entities to DTOs. Read methods are `@Transactional(readOnly = true)`.

```java
public ClientResponse create(ClientRequest r) {
    if (repository.existsByEmail(r.email())) {
        throw new DuplicateResourceException("Un client avec l'email " + r.email() + " existe déjà");
    }
    Client c = Client.builder().nom(r.nom()).email(r.email()).numeroTVA(r.numeroTVA())
            .modePaiement(r.modePaiement()).statut(StatutClient.ACTIF).build();
    return ClientResponse.from(repository.save(c));
}
```

**Controller:** only HTTP. `@Valid @RequestBody` triggers validation; `POST` returns `201 Created` with a `Location` header; `DELETE` returns `204`.

```java
@PostMapping
public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
    ClientResponse created = service.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(created.id()).toUri();
    return ResponseEntity.created(location).body(created);
}
```

### 6.6 Step 5: error handling (same in the 3 services)

Three small exceptions and one `@RestControllerAdvice` that returns a JSON `ApiError {timestamp, status, error, message, details}`.

| Situation | Exception | HTTP |
|---|---|---|
| Invalid body (validation) | `MethodArgumentNotValidException` | 400, with `details` per field |
| Unreadable JSON, bad enum or date | `HttpMessageNotReadableException` | 400 |
| Bad path parameter (`/clients/abc`) | `MethodArgumentTypeMismatchException` | 400 |
| Resource does not exist | `ResourceNotFoundException` | 404 |
| Duplicate (email, plan name, subscription+period) | `DuplicateResourceException` | 409 |
| Forbidden state change (invalid transition) | `InvalidStateException` | 409 |
| Delete a record still referenced (FK) | `DataIntegrityViolationException` | 409 |

Why: the professor grades "codes HTTP adaptés et gestion des erreurs". One handler per service gives uniform, predictable errors. To get a clean 409 on a foreign-key violation, `delete` calls `repository.flush()` right after `repository.delete(...)`, so the constraint is checked inside the service method.

Each service has its **own copy** of these classes (no shared library): services stay independent.

### 6.7 Step 6: business rules per service

**`service-clients`**
- `/clients` full CRUD; duplicate email -> 409; new client is `ACTIF`.
- `/payments`: `GET`, `GET /{id}`, `POST` (needs `clientId`, unknown client -> 404, starts `EN_ATTENTE`), `PATCH /{id}/payer` (`EN_ATTENTE -> PAYE`, otherwise 409).

**`service-abonnements`**
- `/plans` full CRUD; plan name unique -> 409.
- `/subscriptions` full CRUD plus `PATCH /{id}/resilier`.
- Creating a subscription: the plan must exist (404) and be `actif` (409); `statut = ACTIVE`; `prochaineEcheance = dateDebut + 1 month`.
- `resilier` sets `RESILIEE` and `dateFin = today`; a second call -> 409.

**`service-facturation`** (main service)
- `/invoices` with lines; `PUT` and `DELETE` only while `BROUILLON` (else 409).
- Amounts are **computed on the server**, never trusted from the client: `montantHT = sum(quantite x prixUnitaire)`, `montantTTC = montantHT x 1.20`, `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Why `BigDecimal`: `double` loses cents.
- Unique `(subscriptionId, periode)` so the same period cannot be billed twice -> 409.
- Status changes go through a state machine in the enum, not scattered `if`s:

```java
public enum StatutInvoice {
    BROUILLON, EMISE, PAYEE, EN_RETARD, ANNULEE;

    public boolean peutPasserA(StatutInvoice cible) {
        return switch (this) {
            case BROUILLON -> cible == EMISE || cible == ANNULEE;
            case EMISE     -> cible == PAYEE || cible == EN_RETARD || cible == ANNULEE;
            case EN_RETARD -> cible == PAYEE || cible == ANNULEE;
            case PAYEE, ANNULEE -> false;
        };
    }
}
```
  `Invoice.changerStatut(cible)` throws `InvalidStateException` (409) when the transition is not allowed. Endpoints: `PATCH /invoices/{id}/emettre`, `/payer`, `/en-retard`, `/annuler`.

### 6.8 Step 7: seed data and the column-name trap

Each service has a `data.sql` with 2 to 3 rows per table, with ids that are consistent across services (client 1, 2, 3; subscriptions 1, 2, 3 for those clients; invoices 1, 2, 3 for those subscriptions). This makes demos and cross-service tests (Jalon 4+) meaningful.

> **Trap:** Spring's default naming turns `modePaiement` into `mode_paiement`, but it does **not** split consecutive capitals: `numeroTVA` becomes `numerotva`, `montantHT` becomes `montantht`. Our first `data.sql` used `numero_tva` and failed with `Column "NUMERO_TVA" not found`.
> **Rule:** every field with two or more consecutive capitals gets an explicit `@Column(name = "snake_case")`, and `data.sql` uses exactly that name. We use `numero_tva`, `montant_ht`, `montant_ttc`.

### 6.9 Step 8: automated tests

One `@SpringBootTest` + `@AutoConfigureMockMvc` class per service (`ClientsApiTest`, `AbonnementsApiTest`, `FacturationApiTest`). They cover, per service: list (200), read existing (200), read missing (404), valid create (201), invalid create (400 with `details`), duplicate (409), and for invoices an invalid transition (409) and the computed amounts. Example:

```java
@Test
void createClient_invalid_returns400() throws Exception {
    String body = """
            {"nom":"","email":"pas-un-email","numeroTVA":"xx","modePaiement":null}
            """;
    mockMvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.nom").exists());
}
```
Run them with `mvn clean verify` or right-click `src/test` -> *Run Tests*.

### 6.10 Step 9: Postman collection (graded deliverable)

`docs/postman/BillFlex-v1.0.postman_collection.json`: one folder per service (ports 8081, 8082, 8083), about 15 requests each, every request with a status-code test. Cases: list, read existing, read missing (404), valid create (201), invalid create (400), duplicate (409), update, delete (204), delete missing (404), invalid transition (409). Import in Postman, run the collection, take 2 or 3 screenshots into `docs/screenshots/`.

### 6.11 Step 10: run, verify, tag

1. Run the three `*Application` classes (ports 8081, 8082, 8083). Check Swagger and H2 console.
2. `mvn clean verify` must be green.
3. Commit `V1 - services métier`, push, tag `v1.0`, verify the tag (3.3).

### 6.12 Jalon 1 checklist

- [ ] 3 modules, each runs alone on its own port
- [ ] controller / service / repository / model / dto / exception in each
- [ ] validated request DTOs, response DTOs, no entity returned by a controller
- [ ] one `GlobalExceptionHandler` per service (400, 404, 409)
- [ ] own H2 database and `data.sql` per service, no cross-service JPA relation
- [ ] tests green, Postman collection committed, screenshots
- [ ] commit `V1 - services métier`, tag `v1.0` verified on GitHub

---

## 7. Jalon 2: Eureka and Gateway (v2.0)

### 7.1 What the professor asked

A **Eureka server** where the business services register, and an **API Gateway** that reaches the services through **dynamic routing**. An updated architecture schema (names and ports), and the project on Git with a tag. The TP also asks to demonstrate that a service that joins the registry becomes reachable **without adding a route and without restarting the Gateway**.

### 7.2 The idea

Before Jalon 2, a client had to know every service address (`localhost:8081`, `8082`, `8083`). In real life instances move and multiply. So:

- **Eureka** is a phone book: each service announces its *name* and address when it starts, and the others look services up by name.
- The **Gateway** is the single entrance (port 8080). It asks Eureka where a service is and forwards the request to it.

### 7.3 Step 1: the `eureka-server` module

1. New module `eureka-server` (plain Maven, parent `bill-flex`), package `com.billflex.eurekaserver`. Add it to the root `<modules>`.
2. Only one dependency:
   ```xml
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
   </dependency>
   ```
3. Main class with `@EnableEurekaServer`:
   ```java
   @SpringBootApplication
   @EnableEurekaServer
   public class EurekaServerApplication {
       public static void main(String[] args) {
           SpringApplication.run(EurekaServerApplication.class, args);
       }
   }
   ```
4. `application.properties`:
   ```properties
   spring.application.name=eureka-server
   server.port=8761
   eureka.client.register-with-eureka=false
   eureka.client.fetch-registry=false
   ```
   Why the last two lines: the registry must not try to register inside itself.
5. Run it and open http://localhost:8761.

### 7.4 Step 2: register the three business services

In each service:

- `pom.xml`: add `spring-cloud-starter-netflix-eureka-client`.
- `application.properties`: add `eureka.client.service-url.defaultZone=http://localhost:8761/eureka/`.
- Nothing else. In recent Spring Cloud versions the client starts automatically, `@EnableDiscoveryClient` is not needed.
- In the unit tests, change the annotation to `@SpringBootTest(properties = "eureka.client.enabled=false")` so tests do not try to register.

Restart the services, wait about 30 seconds, check the dashboard: `SERVICE-ABONNEMENTS`, `SERVICE-FACTURATION`, `SERVICE-CLIENTS` must be **UP**. The red banner *"EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP..."* is normal in local development (self-preservation mode).

### 7.5 Step 3: the `gateway-service` module

1. New module `gateway-service`, package `com.billflex.gateway`, added to the root `<modules>`.
2. Dependencies:
   ```xml
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.cloud</groupId>
       <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
   </dependency>
   ```
   > **Never add `spring-boot-starter-web` to the Gateway.** It is a *reactive* gateway (WebFlux, non-blocking). Mixing it with the servlet stack breaks startup. The reactive version is the one the professor chose because a slow service does not exhaust the Gateway's threads.
3. Main class: a plain `@SpringBootApplication`.
4. `application.properties` (dynamic routing):
   ```properties
   spring.application.name=gateway-service
   server.port=8080
   eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
   spring.cloud.gateway.server.webflux.discovery.locator.enabled=true
   spring.cloud.gateway.server.webflux.discovery.locator.lower-case-service-id=true
   ```
   How it works: the *discovery locator* creates one route per registered service, named after the service in lowercase. `/service-clients/clients` is forwarded to `service-clients` as `/clients` (the first segment is removed).

### 7.6 Step 4: optional static routing

A profile `statique` disables the locator and declares routes by hand with `lb://` URIs (client-side load balancing through Eureka). Exposed paths have no service prefix (`/clients`, `/plans`, `/invoices`).

```properties
spring.cloud.gateway.server.webflux.discovery.locator.enabled=false
spring.cloud.gateway.server.webflux.routes[0].id=service-clients
spring.cloud.gateway.server.webflux.routes[0].uri=lb://service-clients
spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/clients/**,/payments/**
```
(In Jalon 3 this file moved to `config-repo/gateway-service-statique.properties`.)

### 7.7 Step 5: test through the Gateway

Start order: **Eureka -> services -> Gateway.**

```
http://localhost:8080/service-clients/clients
http://localhost:8080/service-clients/payments
http://localhost:8080/service-abonnements/plans
http://localhost:8080/service-abonnements/subscriptions
http://localhost:8080/service-facturation/invoices
```

**Dynamic discovery demo (graded):** with everything running, stop `service-clients`, start it again. After about 30 seconds it answers again through the Gateway. The Gateway was never touched or restarted.

Deliverables: Postman collection `docs/postman/BillFlex-v2.0.postman_collection.json` (all calls through port 8080), screenshots of the Eureka dashboard with the services UP and of one call through 8080, README architecture updated.

### 7.8 Step 6: README and tag

Update the README: `gateway-service` name, the "Démarrage" section (start order, Gateway URLs), table of applications and ports. Commit `V2 - Eureka et Gateway`, push, tag `v2.0`, verify the tag.

### 7.9 What changed in existing files (summary)

| File | Change |
|---|---|
| root `pom.xml` | + modules `eureka-server`, `gateway-service` |
| 3 service `pom.xml` | + `spring-cloud-starter-netflix-eureka-client` |
| 3 service `application.properties` | + `eureka.client.service-url.defaultZone=...` |
| 3 test classes | `@SpringBootTest(properties = "eureka.client.enabled=false")` |
| `README.md` | gateway name, start order, URLs |

### 7.10 Jalon 2 checklist

- [ ] Eureka dashboard shows the 3 services (and the Gateway) UP
- [ ] all APIs reachable through 8080 with the service-name prefix
- [ ] restart of one service tested without restarting the Gateway
- [ ] Gateway has no `spring-boot-starter-web`
- [ ] architecture schema updated, Postman v2.0 and screenshots committed
- [ ] commit `V2 - Eureka et Gateway`, tag `v2.0` verified

---

## 8. Jalon 3: Config Server (v3.0)

### 8.1 What the professor asked

A `config-repo` folder versioned on Git with **one global configuration file** and **one file per service**. A **Config Server** that serves them, with the **aggregation of the global file and the specific file demonstrated by a REST call**. The business services **and the Gateway** consume the central config, **with no business configuration left locally**. Project on Git with a tag.

### 8.2 The idea

Until now each service carried its own `application.properties` (port, database, Eureka URL duplicated three times). With 20 services that is unmanageable. The Config Server serves the configuration from a Git repository, so changing a setting means editing a file and pushing, with history and review.

How the files are resolved for an application named `service-clients`:

1. `application.properties` (global, for everybody), then
2. `service-clients.properties` (specific).

The server returns both, the specific one first. That is the "aggregation" to demonstrate.

### 8.3 Step 1: create `config-repo/` and push it first

> **Order matters.** The Config Server reads **GitHub**, not your disk. Create, commit **and push** `config-repo` to `main` *before* starting the Config Server, otherwise it serves nothing.

Files:

`config-repo/application.properties` (global)
```properties
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
management.endpoints.web.exposure.include=health,info
```

`config-repo/service-clients.properties` (the other two are identical with their port and database)
```properties
server.port=8083
spring.datasource.url=jdbc:h2:mem:clientsdb
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
spring.sql.init.encoding=UTF-8
spring.sql.init.data-locations=classpath:data.sql
```
(`service-abonnements.properties`: port 8081, `abonnementsdb`. `service-facturation.properties`: port 8082, `facturationdb`.)

`config-repo/gateway-service.properties`
```properties
server.port=8080
spring.cloud.gateway.server.webflux.discovery.locator.enabled=true
spring.cloud.gateway.server.webflux.discovery.locator.lower-case-service-id=true
```
`config-repo/gateway-service-statique.properties`: the static routes of 7.6, served when the Gateway runs with the profile `statique`.

File naming rule: **`{spring.application.name}-{profile}.properties`**. The file name must equal the application name, otherwise the server finds nothing.

```powershell
git add config-repo
git commit -m "V3 - externalisation des configurations dans config-repo"
git push origin main
```

### 8.4 Step 2: the `config-server` module (8888)

1. New module `config-server`, package `com.billflex.configserver`, added to the root `<modules>`.
2. Dependency: `spring-cloud-config-server`.
3. Main class with `@EnableConfigServer`.
4. `application.properties` (stays **local**):
   ```properties
   spring.application.name=config-server
   server.port=8888
   spring.cloud.config.server.git.uri=https://github.com/othman-gr3/bill-flex.git
   spring.cloud.config.server.git.clone-on-start=true
   spring.cloud.config.server.git.refresh-rate=60
   spring.cloud.config.server.git.search-paths=config-repo
   spring.cloud.config.server.git.default-label=main
   ```
   - `search-paths=config-repo`: the config files are in that sub-folder of the project repo.
   - `default-label=main`: the branch to read.
   - `refresh-rate=60`: re-fetch Git at most every 60 seconds.
   - The repository must be **public**, otherwise add `spring.cloud.config.server.git.username` and a personal access token as `...password` (never commit the token).
5. Test: `http://localhost:8888/service-clients/default`. The JSON must contain **two** `propertySources`: `config-repo/service-clients.properties` first, then `config-repo/application.properties`. **Take a screenshot: this is the graded proof of aggregation.** Check the other three names too (`service-abonnements`, `service-facturation`, `gateway-service`).

### 8.5 Step 3: switch the services and the Gateway to the central config

For the 3 services **and** `gateway-service`:

1. `pom.xml`: add `spring-cloud-starter-config`.
2. Replace the whole `src/main/resources/application.properties` with two lines:
   ```properties
   spring.application.name=service-clients
   spring.config.import=configserver:http://localhost:8888
   ```
   - `spring.application.name` tells the Config Server which file to serve.
   - `spring.config.import` loads the remote config at startup. It replaces the old `bootstrap.properties`.
   - We deliberately do **not** write `optional:`: if the Config Server is down the service refuses to start (*fail-fast*), which is the wanted behaviour.
3. Keep `data.sql` in each service: it is a resource, not configuration.
4. Delete the Gateway's local `application-statique.properties` (now in `config-repo`).

`eureka-server` and `config-server` **keep their local configuration**. Why:

- **Bootstrap problem:** if Eureka's config came from the Config Server, Eureka could only start after it, while the Config Server and the others register with Eureka. That is a circular dependency and a fragile start order.
- Their configuration is tiny and never changes, so externalizing it gains nothing.

### 8.6 Step 4: keep the unit tests independent of the Config Server

With `spring.config.import=configserver:...` in the main `application.properties`, `mvn verify` would fail whenever the Config Server is not running. Fix: add a `src/test/resources/application.properties` in each service. In tests it **replaces** the main file (test resources come first on the classpath), so tests never call the Config Server or Eureka:

```properties
spring.application.name=service-clients
spring.cloud.config.enabled=false
eureka.client.enabled=false

spring.datasource.url=jdbc:h2:mem:clientsdb
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
spring.sql.init.encoding=UTF-8
spring.sql.init.data-locations=classpath:data.sql
```
This also means the datasource and JPA settings are needed in the test file, because they no longer exist locally.

### 8.7 Step 5: start order and proof

**Eureka (8761) -> Config Server (8888) -> services (8081, 8082, 8083) -> Gateway (8080).**

Expected final state:

- the 6 URLs answer `200` (3 direct on the business ports, 3 through the Gateway);
- Eureka shows all instances UP;
- the services started with only two local lines;
- stopping the Config Server and restarting a service makes it **fail to start** (fail-fast demo);
- **live change demo:** edit a value in `config-repo/service-clients.properties`, commit, push, wait 60 seconds, reload `http://localhost:8888/service-clients/default`: the new value appears.

Deliverables: `config-repo` with the global file and one file per service, the aggregation screenshot, Postman `BillFlex-v3.0.postman_collection.json` (4 config calls plus the 6 API calls), README with 6 applications, the "Configuration centralisée" section and the new start order.

### 8.8 Step 6: tag

Commit `V3 - Config Server et config-repo`, push, tag `v3.0`, verify the tag.

### 8.9 What changed in existing files (summary)

| File | Change |
|---|---|
| root `pom.xml` | + module `config-server` |
| 3 service `pom.xml` + `gateway-service/pom.xml` | + `spring-cloud-starter-config` |
| 3 service `application.properties` + gateway | replaced by 2 lines |
| `gateway-service/.../application-statique.properties` | deleted (moved to `config-repo`) |
| 3 services | new `src/test/resources/application.properties` |
| `README.md` | 6 applications, config section, start order |

### 8.10 Jalon 3 checklist

- [ ] `config-repo` committed **and pushed** to `main`: global file plus one file per service (and the Gateway)
- [ ] `config-server` serves the four URLs with two `propertySources` (screenshot)
- [ ] services and Gateway have only `spring.application.name` and `spring.config.import` locally
- [ ] Eureka and Config Server still use local config
- [ ] unit tests green without the Config Server running
- [ ] 6 URLs answer 200, Eureka shows all UP
- [ ] README and Postman v3.0 updated
- [ ] commit `V3 - Config Server et config-repo`, tag `v3.0` verified

---

## 9. Jalon 5: RabbitMQ et notifications

### 9.1 What was asked

Send an email after the validation of an invoice operation, using RabbitMQ for asynchronous JSON communication and Mailpit for local SMTP testing. The message must be published after the invoice transaction commits, and the consumer must process messages left in the queue after a restart.

### 9.2 The idea

`service-facturation` remains the business producer: it validates the client through the existing OpenFeign client, changes `BROUILLON` to `EMISE`, saves the invoice, then registers an `afterCommit` synchronization. The synchronization publishes a local copy of `NotificationEmailDto` to the durable `notifications.email` queue. `notification-service` is an internal consumer on port 8084; it does not use Eureka and has no Gateway route. It validates the message and sends it through Mailpit with `JavaMailSender`.

### 9.3 Steps

1. Start the two local infrastructure containers:
   `docker compose up -d`.
2. Push `config-repo/application.properties` and `config-repo/notification-service.properties` to `main` before starting Config Server. RabbitMQ uses the local demo credentials `tp/tp123`; Mailpit uses SMTP `1025`.
3. Start Eureka, Config Server, the three business services, `notification-service`, and finally the Gateway.
4. Call `PATCH /service-facturation/invoices/{id}/emettre` through the Gateway. The HTTP response is returned after the database transaction; email delivery is handled independently.
5. Stop the notification service, emit two invoices, and verify `Ready = 2` for `notifications.email` at RabbitMQ's management UI. Restart the consumer and verify that both messages are delivered and the counters return to zero.

### 9.4 Why publish after commit

Publishing before commit could leave an email in the queue for an invoice whose transaction later rolls back. `afterCommit` makes the event represent only a successfully persisted `EMISE` invoice. A broker failure is logged and does not change the already successful business response.

### 9.5 Changes in existing files

| File | Change |
|---|---|
| root `pom.xml` | + `notification-service` module |
| `service-facturation/pom.xml` | + AMQP starter |
| `service-facturation` | durable queue, JSON converter, publisher, after-commit publication and tests |
| `config-repo/application.properties` | shared local RabbitMQ connection |
| `config-repo/notification-service.properties` | port, Mailpit and listener retry settings |
| `README.md` | architecture, ports, Docker commands and test procedure |
| `docs/postman/BillFlex-v5.0.postman_collection.json` | success, validation, 404/409 and deferred-processing cases |
| `docs/DOCUMENTATION-BILLFLEX.md` | this section and new traps/glossary terms |
| `docker-compose.yml` | Mailpit and RabbitMQ containers |

### 9.6 Jalon 4 note

OpenFeign communication, already merged; details to be completed by the teammate who did it.

### 9.7 Problems and traps

- The queue must be declared by the producer as well as the consumer, so it exists while the consumer is stopped.
- Use `Jackson2JsonMessageConverter` with the Spring Boot 3.5.6 dependency set; do not copy the Spring AMQP 4.x `JacksonJsonMessageConverter` name.
- `max-attempts` is the Spring Boot 3.x listener retry property; `max-retries` is not used here.
- Do not add Eureka or Gateway discovery to `notification-service`; its direct controller is only a manual email check.
- Do not publish from the controller or before `repository.save` commits.

### 9.8 Jalon 5 checklist

- [ ] Mailpit and RabbitMQ start with Docker Compose or the documented `docker run` commands.
- [ ] `notification-service` sends a valid email with 204 and reports validation/SMTP errors as 400/503.
- [ ] The invoice emission publishes one JSON message only after commit.
- [ ] Broker failure does not turn a successful invoice emission into an HTTP error.
- [ ] Stopped-consumer messages are deferred and delivered after restart.
- [ ] README, architecture diagram and Postman v5.0 are updated.

## 10. Git, GitHub and GitHub Actions

### 9.1 What we do on GitHub

1. **Repository** `othman-gr3/bill-flex`, public (the Config Server needs to clone it without credentials), collaborators added in *Settings -> Collaborators*.
2. **Commits and tags** exactly as in section 3. Tags appear in the *Tags* page (`https://github.com/othman-gr3/bill-flex/tags`). The professor checks the tag, so always verify that it points to the final commit.
3. **Pull Requests** for each jalon branch (`jalon-N`), reviewed by the two other teammates, merged into `main`, then tagged.
4. `.gitignore` at the root:
   ```
   target/
   .idea/
   *.iml
   *.log
   .mvn/wrapper/maven-wrapper.jar
   ```

### 9.2 GitHub Actions (continuous integration)

> **Note for the author before sharing this document:** the Jalon 1 to 3 deliverables were built and tagged with manual Git commands (section 3). No workflow file was produced in those steps. The workflow below is the **recommended** one. If your repository already has a `.github/workflows/*.yml` that you wrote yourself, **replace this section with the content of your file** and explain what it does.

**Why:** every push and pull request builds all modules and runs all tests on a clean machine, so a broken `main` is detected immediately and nobody tags a jalon that does not build. This is possible because, since Jalon 3, the unit tests do not need Eureka or the Config Server.

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build and test all modules
        run: mvn -B clean verify

      - name: Upload test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: '**/target/surefire-reports/'
```

How to use it:

1. Add the file, commit, push. Open the *Actions* tab on GitHub: the run `CI` appears.
2. A green check on the commit means build and tests pass. Add the badge to the README if you like:
   `![CI](https://github.com/othman-gr3/bill-flex/actions/workflows/ci.yml/badge.svg)`
3. Optional rule: *Settings -> Branches -> Branch protection* on `main`, require the `build` check before merging a Pull Request.
4. Tags `v*` also trigger a run, so each jalon tag is proven to build.

Limits to know: the workflow builds and tests; it does not start the whole system (Eureka + Config Server + services + Gateway). End-to-end checks stay manual with Postman.

---

## 11. The method: how to do the next jalon

This is the loop we followed three times. Follow it again, in this order.

### 10.1 Before coding

1. **Read the professor's sheet** (objective, deliverables, the example repo tag) and copy the deliverables into a checklist. A jalon is graded on those bullets, nothing else.
2. **Check the previous tag.** `git rev-list -n 1 vX.0` must equal the commit you consider final, and the previous jalon must still run.
3. **List the changes before writing code:** *new modules*, *new files*, *modified files* (we always produced this "changes only" list, it avoids forgetting a pom or a property).
4. **Announce in the team chat that you own this jalon**, and check that nobody else is editing the root `pom.xml`, `config-repo/` or the README meanwhile (see 3.2).

### 10.2 Build, smallest step first

5. Create the new module or code in this order: pom -> main class -> configuration -> logic -> tests.
6. **Configuration rule since Jalon 3:** a new application keeps only `spring.application.name` and `spring.config.import=configserver:http://localhost:8888` locally. Its port, database and other settings go into `config-repo/<name>.properties`, and `config-repo` is **pushed to GitHub before** you start the Config Server.
7. **Test rule:** each service has `src/test/resources/application.properties` so tests never depend on Eureka or the Config Server.
8. Add to the root `pom.xml` `<modules>` only after the folder exists.

### 10.3 Prove it works

9. Run `mvn clean verify`: green.
10. Start the whole system in order (Eureka -> Config Server -> services -> Gateway) and check Eureka.
11. Run the Postman collection (valid cases, validation errors, missing resources). Create `BillFlex-vN.0.postman_collection.json`.
12. Do the **demo the sheet asks for** (dynamic discovery, aggregation, ...). Take screenshots into `docs/screenshots/`.
13. **Regression:** the URLs of the previous jalons still answer.

### 10.4 Deliver

14. Update the README (architecture diagram, ports table, start order, URLs).
15. Commit with the standard message, push, create the tag, **verify the tag** (3.3), and check the *Tags* page on GitHub.
16. Update this documentation with a new section for the jalon (what, how, why, problems).

### 10.5 Design principles we keep

- **One service, one database, one responsibility.** No JPA relation across services.
- **Request and response DTOs**, never entities in controllers.
- **Business rules in the service layer** (and state machines in the enum), not in controllers.
- **One uniform error format** and the right HTTP code.
- **Money in `BigDecimal`.**
- **Everything configurable lives in `config-repo`** (from Jalon 3).
- **No shared library between services** (each owns its copy of DTOs and exceptions) to keep them independent.

---

## 12. Likely next jalons and how to approach them

> These are our **expectation**, based on the Bill-Flex project sheet (synchronous calls, asynchronous events, bonus). Always follow the professor's actual jalon sheet; if it differs, the sheet wins.

### 11.1 Synchronous calls with OpenFeign

From the sheet: `service-facturation` calls `service-abonnements` (is the subscription ACTIVE, which plan) before computing an invoice, and `service-clients` (VAT number, payment mode) before moving an invoice to `EMISE`.

How we would do it:

1. In `service-facturation`: dependency `spring-cloud-starter-openfeign` (version managed by the Spring Cloud BOM, already imported), `@EnableFeignClients` on the main class.
2. One interface per remote service, addressed **by Eureka name**, so no port is hardcoded:
   ```java
   @FeignClient(name = "service-abonnements")
   public interface AbonnementsClient {
       @GetMapping("/subscriptions/{id}")
       SubscriptionDto getSubscription(@PathVariable("id") Long id);
   }
   ```
3. Create the small `SubscriptionDto` / `ClientDto` records **inside `service-facturation`** (copy only the fields needed). Do not share a jar.
4. Map remote failures to meaningful errors: 404 from the remote -> 404 or 409 in our API, remote down -> 503. Add Resilience4j (retry, circuit breaker, fallback) as a follow-up.
5. Tests: mock the Feign client with `@MockitoBean` (or WireMock) so unit tests need no other service.
6. Postman: a scenario that creates an invoice for an `ACTIVE` subscription (ok) and for a missing/terminated one (error).

### 11.2 Asynchronous events with Kafka

From the sheet: `subscription-renewed` (abonnements -> facturation creates a `BROUILLON` invoice), `invoice-issued` (facturation -> clients notifies and creates a pending `Payment`), `payment-received` (clients -> facturation sets the invoice `PAYEE`).

How we would do it:

1. Run Kafka locally with Docker Compose (Docker Desktop required), add `docker-compose.yml` at the repo root.
2. `spring-kafka` in the three services. Kafka bootstrap servers go in `config-repo/application.properties` (global).
3. A daily `@Scheduled` job in `service-abonnements` emits `subscription-renewed` when `prochaineEcheance` is reached (needs `@EnableScheduling`).
4. **Idempotent consumers:** if the same event arrives twice, only one invoice must be created. The unique constraint `(subscriptionId, periode)` we already have on `Invoice` is exactly for this.
5. Consider the **Outbox pattern** so the database update and the event cannot diverge.
6. Tests with Embedded Kafka or Testcontainers; keep ordinary unit tests free of a running broker.

### 11.3 Bonus items

- Monthly batch job: automatic reminders for `EN_RETARD` invoices (Spring Scheduler or Spring Batch).
- Angular dashboard of monthly recurring revenue (MRR), served through the Gateway (remember CORS).

### 11.4 Per-jalon rule of thumb

Same loop as section 10: write the changes-only list, implement, test, Postman, demo, README, commit, tag, verify.

---

## 13. Problems we hit and how we fixed them

| # | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | `Author identity unknown` on `git commit` | Git on this PC has no name/email | `git config --global user.name "..."` and `user.email "..."` |
| 2 | `fatal: tag 'v1.0' already exists`, `Everything up-to-date` | tag created earlier on an old commit | check with `git rev-list -n 1 v1.0`; delete and recreate the tag on the right commit (3.3) |
| 3 | `Unique index or primary key violation` on `data.sql` at startup | a generated Initializr class (for example `com.billflex.serviceabonnements.ServiceAbonnementsApplicationTests`) started a **second** Spring context on the same `jdbc:h2:mem:...` database | delete the generated package in `src/main/java` **and** `src/test/java`; keep only `com.billflex.<service>`; use the plain Maven module generator |
| 4 | `Table "CLIENTS" not found (this database is empty)` | same generated app scans an empty package, so Hibernate creates no tables, then `data.sql` runs | same fix as #3 |
| 5 | `Column "NUMERO_TVA" not found` | Spring naming does not split consecutive capitals: `numeroTVA` -> `numerotva` | `@Column(name = "numero_tva")`; likewise `montant_ht`, `montant_ttc` |
| 6 | `Table not found` even without a second app | `data.sql` runs before Hibernate creates tables | `spring.jpa.defer-datasource-initialization=true` |
| 7 | `Child module ... does not exist` | `<module>` listed before the folder exists | create the folder first or add the module line later |
| 8 | Red `@Getter`, `@Builder` in IntelliJ | annotation processing disabled | *Settings -> Compiler -> Annotation Processors -> Enable* |
| 9 | `release version 17 not supported` | wrong JDK in IntelliJ or in the Maven runner | set the SDK in *Project Structure* and the Maven JRE in *Settings -> Build Tools -> Maven -> Runner* |
| 10 | Gateway 404 on `/service-clients/clients` | service not yet registered, or the name is not lowercase | wait 30 s, check the exact name in the Eureka dashboard |
| 11 | Gateway 503 | service registered but down | restart the service |
| 12 | Gateway fails at startup with a servlet/WebFlux error | `spring-boot-starter-web` added to the Gateway | remove it |
| 13 | `Connection refused localhost:8761` in a service | Eureka not started | start `eureka-server` first |
| 14 | Config Server returns empty `propertySources` | `config-repo` not pushed to `main`, wrong repo URL, or file name different from `spring.application.name` | push, check the URL, rename the file |
| 15 | Config Server cannot clone | private repo | make it public or add a username and token in the Config Server properties |
| 16 | Service does not start after Jalon 3 | Config Server not running | start it before the services (fail-fast is intended) |
| 17 | `mvn verify` fails with a Config Server error | missing `src/test/resources/application.properties` | add it (section 8.6) |
| 18 | A change in `config-repo` does not appear | not pushed, or less than 60 s since the push | push and wait |
| 19 | Port already in use | an old run is still active | stop it in the Run/Services window |
| 20 | Services listed twice or on an old port in Eureka | stale registrations | stop all apps, wait 30 s, restart |
| 21 | Delete of a referenced record gives 500 | FK violation raised only at commit | `repository.flush()` after `delete` and handle `DataIntegrityViolationException` as 409 |
| 22 | Email is sent before the invoice is durable | publication was performed inside the transaction | register `TransactionSynchronization.afterCommit`; log an `AmqpException` without changing the 200 response |
| 23 | Deferred messages are lost or rejected | queue was transient or consumer requeued failures forever | declare `notifications.email` durable and configure `default-requeue-rejected=false` with bounded retry |
| 24 | Notification service appears in Gateway or Eureka | an unnecessary Eureka client or Gateway route was added | keep the module without the Eureka dependency; use only the direct manual controller |

**Harmless messages (do not waste time on them):**
- `Mockito is currently self-attaching ...` and `Sharing is only supported for boot loader classes ...` on recent JDKs.
- `spring.jpa.open-in-view is enabled by default` (WARN).
- The red *EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP...* banner in local development.
- `(grafted)` next to a commit in `git log`: the clone is shallow.

**How to read a Spring Boot stack trace:** scroll to the last `Caused by:` line. That is the real cause, everything above is framework noise. When you ask for help (teammate or AI), paste that line first.

---

## 14. Glossary

| Term | Meaning |
|---|---|
| **Jalon** | a graded milestone of the PFM, delivered on Git with a tag |
| **PFM** | the end-of-module project (Bill-Flex) |
| **TP** | practice work on the professor's sample (`tp-ms-ecom`), not handed in |
| **Microservice** | a small application that owns one business area and its data |
| **Maven multi-module** | one parent `pom.xml` and several child modules built together |
| **BOM** | a pom that only manages dependency versions (`spring-cloud-dependencies`) |
| **DTO** | Data Transfer Object: the shape of data in the API (request/response) |
| **Entity** | a class mapped to a database table |
| **Repository** | Spring Data interface for database access |
| **Eureka** | registry where services register their name and address |
| **Service discovery** | finding a service by name instead of a hardcoded address |
| **API Gateway** | single entrance that routes requests to services |
| **Reactive (WebFlux)** | non-blocking web stack used by the Gateway |
| **Discovery locator** | Gateway feature creating one route per Eureka service |
| **`lb://name`** | Gateway URI meaning "load-balance through Eureka" |
| **Config Server** | serves configuration files from a Git repository |
| **config-repo** | the folder of the repo holding the central configuration |
| **Bootstrap problem** | Eureka and Config Server cannot get their own config from the Config Server |
| **Fail-fast** | the service refuses to start when it cannot get its config |
| **OpenFeign** | declarative HTTP client between services (next jalon, probably) |
| **Kafka / event** | asynchronous messaging between services (next jalon, probably) |
| **RabbitMQ** | AMQP broker that stores and delivers the notification JSON message |
| **Queue** | durable ordered buffer; here `notifications.email` holds emails while the consumer is stopped |
| **Mailpit** | local SMTP server and web inbox used to inspect test emails |
| **After commit** | transaction callback executed only after the invoice database transaction succeeds |
| **Consumer** | notification component that reads a queue and sends the email |
| **Idempotent** | processing the same message twice has the same effect as once |
| **Tag (`v1.0`)** | a named Git pointer to a commit, what the professor grades |
| **CI** | automatic build and tests on every push (GitHub Actions) |

---

## 15. Prompt for your AI assistant

Paste the block below **at the start of every AI session** (ChatGPT, Claude, Copilot, ...) before asking for anything. It gives the assistant the whole context, the conventions, the traps and the working rules. Then add your request using one of the templates at the end.

````text
You are a senior Java / Spring Boot microservices engineer helping a team of three students
(Othmane Lamdala, Hiba Zakari, Walid Louane) with their end-of-module project "Bill-Flex"
for the course "J2EE avancée" (5th year IDSI, Dr. Salim Khalil). The project is delivered in
graded milestones called "jalons", each one delivered on GitHub with a tag (v1.0, v2.0, ...).
Repository: https://github.com/othman-gr3/bill-flex (branch main). We work in IntelliJ IDEA.
Write code comments in English. Keep French names for domain entities, fields, enums and
for the README. Answer in the language I use.

## 1. The project
Bill-Flex is a recurring-billing platform for a B2B SaaS. At each monthly renewal of a
subscription, an invoice is generated from the plan, sent to the client, paid, and followed up
automatically when unpaid. The unit of processing is the Invoice, lifecycle:
BROUILLON -> EMISE -> PAYEE / EN_RETARD -> ANNULEE.
Rule: one invoice = 1 subscription + 1 client + 1 period + 1 total amount TTC.

## 2. Fixed technical stack (never change it)
- Java 17 as target (newer JDKs run it), Maven multi-module, Spring Boot 3.5.6, Spring Cloud 2025.0.0.
- Group com.billflex, parent artifact bill-flex 1.0.0 (packaging pom). Root pom inherits
  spring-boot-starter-parent 3.5.6 and imports the spring-cloud-dependencies BOM.
- Configuration in .properties files (not yaml). Lombok allowed. DTOs are Java records.
- H2 in-memory database per business service. springdoc-openapi-starter-webmvc-ui 2.8.9.

## 3. Applications (current state: Jalons 1, 2 and 3 are DONE and tagged v1.0, v2.0, v3.0)
| Module | spring.application.name | Port | Role |
| service-abonnements | service-abonnements | 8081 | Plan, Subscription |
| service-facturation | service-facturation | 8082 | Invoice, InvoiceLine (main service) |
| service-clients     | service-clients     | 8083 | Client, Payment |
| eureka-server       | eureka-server       | 8761 | service registry |
| gateway-service     | gateway-service     | 8080 | reactive Spring Cloud Gateway (WebFlux), dynamic routing /<service-name>/** |
| config-server       | config-server       | 8888 | Spring Cloud Config Server reading folder config-repo of the GitHub repo, branch main |
Base packages: com.billflex.abonnements, .facturation, .clients, .eurekaserver, .gateway, .configserver.
Start order: Eureka (8761) -> Config Server (8888) -> services -> Gateway (8080).

## 4. Domain model
- Plan(id, nom, prixMensuel, quotaUtilisateurs, actif)
- Subscription(id, plan [ManyToOne, same service], clientId, dateDebut, dateFin, prochaineEcheance, statut ACTIVE|SUSPENDUE|RESILIEE)
- Invoice(id, subscriptionId, clientId, periode "yyyy-MM", montantHT, montantTTC, echeance, statut, lignes) with unique (subscription_id, periode)
- InvoiceLine(id, invoice, libelle, quantite, prixUnitaire)
- Client(id, nom, email unique, numeroTVA, modePaiement CARTE|VIREMENT|PRELEVEMENT, statut ACTIF|SUSPENDU)
- Payment(id, client [ManyToOne, same service], invoiceId, date, montant, moyen, statut EN_ATTENTE|PAYE|ECHOUE)
Cross-service dependencies are plain Long ids (clientId, planId, subscriptionId, invoiceId),
NEVER a JPA relation across services.
REST endpoints: /plans, /subscriptions (+ PATCH /{id}/resilier), /invoices (+ PATCH /{id}/emettre,
/payer, /en-retard, /annuler), /clients, /payments (+ PATCH /{id}/payer). Through the Gateway they are
reached as /service-clients/clients, /service-abonnements/plans, /service-facturation/invoices, ...

## 5. Code conventions (follow them exactly)
- Layers in every business service: controller / service / repository / model / dto / exception.
  Controllers only handle HTTP. Business rules live in the service layer. State machines live in the enum
  (StatutInvoice.peutPasserA).
- Request DTOs carry jakarta.validation annotations; response DTOs have a static from(entity).
  Controllers never return entities.
- Each service has its OWN copy of ApiError, ResourceNotFoundException, DuplicateResourceException,
  InvalidStateException and a @RestControllerAdvice GlobalExceptionHandler. No shared library.
- HTTP codes: 200, 201 (+ Location header), 204, 400 (validation, unreadable body, bad path param),
  404 (not found), 409 (duplicate, invalid state transition, DataIntegrityViolation).
  Error body: {timestamp, status, error, message, details}.
- Money is BigDecimal, scale 2, RoundingMode.HALF_UP. Never double. TTC = HT x 1.20, computed on the server.
- Enums are stored with @Enumerated(EnumType.STRING).
- Every field with two or more consecutive capitals needs an explicit @Column(name="snake_case")
  (numeroTVA -> numero_tva, montantHT -> montant_ht, montantTTC -> montant_ttc), because Spring does
  not split consecutive capitals. data.sql uses those exact column names.
- Each service has its own data.sql seed with ids consistent across services (clients 1..3,
  subscriptions 1..3 for those clients, invoices 1..3).
- data.sql requires spring.jpa.defer-datasource-initialization=true.

## 6. Configuration rules (since Jalon 3)
- Business services and the Gateway keep ONLY two local lines in src/main/resources/application.properties:
    spring.application.name=<name>
    spring.config.import=configserver:http://localhost:8888
  (no "optional:", fail-fast is wanted). Their port, H2/JPA settings and other properties live in
  config-repo/<name>.properties. Shared settings (Eureka URL, actuator) live in config-repo/application.properties.
- eureka-server and config-server keep their configuration LOCAL (bootstrap problem).
- Any change in config-repo must be committed AND pushed to main before starting the Config Server
  (it reads GitHub, not the disk). Repository is public.
- Each business service has src/test/resources/application.properties (it replaces the main file in tests)
  with spring.cloud.config.enabled=false, eureka.client.enabled=false and its own H2 + JPA settings,
  so unit tests never need Eureka or the Config Server.
- The Gateway must NEVER get spring-boot-starter-web (it is reactive/WebFlux).
- A module is added to the root <modules> only after its folder exists.
- Modules are created with the plain Maven generator in IntelliJ (NOT Spring Initializr, which generates a
  second @SpringBootApplication and a default test that break the tests).

## 7. Known traps (do not reintroduce them)
1. A leftover generated package (e.g. com.billflex.serviceclients) starts a second Spring context on the
   same in-memory H2 database -> "Unique index violation" or "Table not found". One @SpringBootApplication per module.
2. Column naming with consecutive capitals (see section 5).
3. Tests failing because they depend on the Config Server or Eureka (see section 6).
4. Gateway 404/503: service not yet registered (wait ~30 s), name not lowercase, or service down.
5. Config Server serving nothing: config-repo not pushed, wrong file name (must equal spring.application.name).
6. Tags must point to the final commit; verify with: git rev-list -n 1 vX.0 == git log --oneline -1.

## 8. Git rules
- Do NOT run git commands. Give me the commands to run.
- One commit and one tag per jalon: "V1 - services métier"/v1.0, "V2 - Eureka et Gateway"/v2.0,
  "V3 - Config Server et config-repo"/v3.0. Next ones follow the same pattern ("V4 - <topic>"/v4.0).
- Never commit target/, .idea/, *.iml, logs or secrets.
- I own this jalon end to end (no split by service). I work on a branch named jalon-N, open a Pull Request
  into main, and tag from main after review. Do not touch things outside the scope of this jalon.

## 9. How to work with me
1. I will paste the professor's jalon sheet. Treat its deliverables as the acceptance checklist. Do not invent
   requirements. If something is unclear, state your assumption in one line and continue (ask at most one question).
2. FIRST print a "changes only" list: new modules, new files (with path), modified files (with path).
3. Then give complete, compilable code for each file (no "...", no TODO), exact paths, exact dependency
   coordinates. Prefer the smallest change that satisfies the sheet. Do not refactor unrelated code.
4. Follow the existing conventions above, including config-repo, test resources, Postman and README updates.
5. End with: how to run it (start order), the URLs to test, expected results, the Postman cases
   (valid, validation error 400, missing resource 404, conflict 409), the screenshots to take, the
   README changes, and the git commands (commit, push, tag, verify tag).
6. You cannot run Maven or my machine. Say clearly that the code is untested, and tell me which log line
   (the last "Caused by:") to paste back if something fails.
7. If you are not sure a library version or property name is right for Spring Boot 3.5.6 / Spring Cloud 2025.0.0,
   say so and tell me how to verify it, instead of guessing.
8. Keep explanations short and practical. Explain WHY only for non-obvious choices.

Reply "Context loaded" and wait for my jalon sheet.
````

### 14.1 Request templates (paste after the context prompt)

**Start a new jalon**
```text
Jalon N: <title>. Here is the professor's sheet:
<paste the objective and the deliverables>
Current state: v3.0 (or the last tag) is done and working.
Give me the "changes only" list first, then the code, then the test and Git steps.
```

**OpenFeign (probable next jalon)**
```text
Jalon: synchronous communication with OpenFeign.
service-facturation must call service-abonnements (is the subscription ACTIVE and which plan) before computing
an invoice, and service-clients (VAT number and payment mode) before moving an invoice to EMISE.
Use Eureka service names in @FeignClient, copy only the needed DTO fields inside service-facturation,
map remote errors (404 / down) to clean API errors, mock Feign in unit tests, add Postman scenarios.
```

**Kafka (probable next jalon)**
```text
Jalon: asynchronous communication with Kafka.
Topics: subscription-renewed (abonnements -> facturation creates a BROUILLON invoice),
invoice-issued (facturation -> clients creates a pending Payment and notifies),
payment-received (clients -> facturation sets the invoice to PAYEE).
Add a docker-compose.yml for Kafka, put Kafka settings in config-repo, make consumers idempotent
(use the existing unique (subscription_id, periode)), keep unit tests free of a running broker.
```

**Fix an error**
```text
The service <name> fails. Here is the last "Caused by:" line and the lines around it:
<paste>
Find the root cause, give the smallest fix with exact file paths, and tell me how to verify it.
```

**Review before tagging**
```text
Review the project against this jalon checklist: <paste the sheet's deliverables>.
List every gap as a checklist and fix only the gaps. Do not change anything else.
```

**Update this documentation**
```text
Add a section "Jalon N" to docs/DOCUMENTATION-BILLFLEX.md in the same format as sections 6 to 8:
what the professor asked, the idea, the steps with code, why, what changed in existing files,
problems met, and the checklist. Also add new traps to section 12 and new terms to section 13.
```

---

*End of the documentation. Keep it up to date: after every jalon, add a section and the new traps you met.*
