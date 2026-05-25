# EcoSkiller Service Categorization (Shared vs. Isolated)

This artifact details the categorization of services in the EcoSkiller platform based on resource usage and database interactivity. Heavily resource-intensive or stateless processing services are deployed once in the `shared-infra` layer, whereas stateful services that interact with the databases are isolated per environment.

## 1. Shared Infrastructure Services (`shared-infra`)
These services run a single instance shared across **Dev**, **Test**, and **Stage** environments to optimize RAM and CPU usage.

| Service Category | Service / Component Name | Description |
| :--- | :--- | :--- |
| **Cognitive Speech AI** | `whisper-stt async engine` | Audio-to-text transcription engine. |
| | `alphacep/kaldi-vosk-server` | Speech recognition server. |
| | `pyannote/speaker-diarization` | Speaker segmentation & identification. |
| **Local AI Agents** | `ollama/ollama Engine` (`llama3.1:8b`) | Heavy local LLM inference engine. |
| | `gd-topic-generator-service` | Topic generation based on LLM outputs. |
| **Advanced AI & Vectors** | `embedding-model-inference` | Text embedding vector generator. |
| | `feature-store-service` | Embedding caching & management. |
| | `model-registry-service` | AI Model weights storage. |
| **Stateless Predictive AI**| `intelligence-prediction-eng` | Prediction scoring system. |
| | `passive-intelligence-engine` | Background data parser. |
| **Media Routing** | `jitsi/jicofo` & `jitsi/prosody` | WebRTC orchestration. |
| | `jitsi/jvb SFU Media Bridge` | WebRTC media router. |
| | `jitsi/web` & `coturn` | TURN server and Jitsi web frontend. |
| **PSTN Telephony** | `signalwire/freeswitch` | SIP/PSTN Telephony processing. |
| | `gd-phone-bridge-svc` | Telephone routing connector. |
| **Calculation & Matching**| `scoring-engine` | Standard math scoring rules. |
| | `dojo-match-engine` | Matchmaking logic. |
| | `idea-dna-fingerprint-engine` | Fingerprints files. |
| | `idea-similarity-anti-theft` | Vector similarity search. |
| | `innovation-scoring-engine` | Scores marketplace entries. |
| | `scoring-offline-service` | Offline score calculator. |
| **Document & Signature** | `legal-document-gen-service` | CPU-heavy PDF generation. |
| | `digital-signature-service` | Cryptographic file signing. |
| **Common Storage/Broker** | `PostgreSQL 15 Engine` | Database engine (uses isolated schemas per env). |
| | `Apache Kafka Broker` | Message broker (uses prefixed channels per env). |
| | `Redis 7 Memory Cache` | Key-value store (uses prefixed keys per env). |
| | `MinIO Object Storage` | Asset storage (uses separate buckets per env). |
| | `Qdrant Vector Engine` | Vector store (uses separate collections per env). |
| | `OpenSearch Engine` | Text indexer (uses separate indices per env). |
| | `ClickHouse Database` | Analytics logging database. |
| **Workflow & Telecom** | `temporalio/server Engine` | Workflow orchestrator (uses namespaces per env). |
| | `docker-mailserver` (SMTP) | Outbound SMTP pipeline. |
| | `jasmin-sms gateway` (SMPP) | Outbound SMS pipeline. |
| | `search-indexer` | Document indexing queue processor. |
| | `metabase/metabase UI` | BI Dashboard (configured with multiple datasources). |

---

## 2. Environment-Specific Isolated Services
These services read/write to the database and manage distinct business logic. Each environment (**Dev**, **Test**, **Stage**) runs its own dedicated instance.

| Pod / Domain | Service Name | DB Dependency |
| :--- | :--- | :--- |
| **Pod 1: Identity & SSO** | `keycloak:24.0` | Environment realm config (Dev/Test/Stage schemas). |
| | `auth-service` | Reads & writes tenant/client credentials. |
| **Pod 2: Talent Acquisition**| `recruiter-service` | CRUD for recruiters/applications. |
| | `job-service` | CRUD for jobs, applications, pipeline. |
| **Pod 3: Onboarding** | `application-service` | Onboarding lifecycle database entries. |
| | `user-service` | User profile credentials & details. |
| **Pod 4: Notifications** | `notification-service` | Notification history state & templates. |
| **Pod 5: Financials** | `billing-service` | Financial billing records. |
| | `kill-bill Core / Mock` | Payment state machines. |
| **Pod 6: Governance** | `admin-service` | Admin portal configurations. |
| | `Wiki.js Panel` | Documentation database schemas. |
| **Pod 10: Live Matches** | `interview-service` (WSS) | Realtime active interview DB states. |
| | `gd-orchestrator Engine` | Active session state tracking. |
| **Pod 14: Testing Profiles**| `intelligence-profile-svc` | User competency profiles. |
| | `dojo-intel-testing-service` | Online test configurations and states. |
| **Pod 15: Timelines** | `intel-evolution-timeline` | Career timeline history. |
| **Pod 16: Evaluation** | `certification-engine` | Handles DB records of issued certificates. |
| **Pod 21: Ideas** | `idea-registry-service` | User project and IP registry database. |
| **Pod 22: Marketplace** | `idea-marketplace-service` | Bids, listings, and trade orders. |
| | `project-execution-orchestrator` | Milestone execution state database. |
| **Pod 23: Licensing** | `licensing-contract-service` | Licensing documents and database. |
| | `royalty-accounting-engine` | Financial double-entry ledgers. |
| | `royalty-wallet-service` | Balance calculations and wallets. |
| **Pod 24: Trust & Audits** | `revenue-ingestion-gateway` | Revenue flows. |
| | `innovation-trust-governance` | Rules and compliance database. |
| | `royalty-audit-compliance-svc` | Auditing history. |
| **Pod 27: Archives** | `immutable-archive-service` | Cold-storage indexes and file database. |
| **Pod 31: Territories** | `society-service` | Territory structure database. |
| | `franchise-service` | Franchise registry and status database. |
| **Pod 32: Field Ops** | `coordinator-service` | Regional supervisor database. |
| | `coach-service` | Local center coaches database. |
| **Pod 33: Workshops** | `workshop-service` | Training workshop details. |
| | `enrollment-service` | Student enrollments database. |
| **Pod 34: Registrations** | `attendance-service` | Student attendance logs. |
| | `couchdb:3` | Local node offline sync backend database. |
| **Pod 35: Certificate** | `certificate-service` | PDF certificate issuance & template database. |
| **Pod 36: Tournaments** | `tournament-service` | Live leaderboard and match databases. |
| **Pod 37: Payouts** | `commission-engine-service` | Commission tracking database. |
| | `payout-service` | Bank payout logs. |
| **Pod 38: Rural Finance** | `unit-finance-service` | Microfinance accounts ledger database. |
| | `scheme-service` | Financial support schemes database. |
| **Pod 39: Scheme Accounts**| `scheme-accounting-service` | Scheme balance bookkeeping database. |
| | `csr-contract-service` | CSR project contracts database. |
| **Pod 40: Inventory** | `product-inventory-service` | Inventory items database. |
| | `expo-service` | Exhibition stall allocation database. |
| **Pod 41: Risk Control** | `dispute-service` | Conflict resolution database. |
| | `compliance-service` | Disciplinary record database. |
| **Pod 42: Inspections** | `audit-service` | Physical center audit records database. |
| **Pod 43: Analytics** | `society-analytics-service` | Regional trends database. |
| | `longitudinal-impact-service` | Long-term study tracking database. |
