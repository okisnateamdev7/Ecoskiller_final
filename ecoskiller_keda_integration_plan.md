# KEDA Integration & Theory Plan for EcoSkiller

This document outlines both the theoretical concepts of KEDA (Kubernetes Event-driven Autoscaling) and how it will be practically implemented in the EcoSkiller multi-cluster setup on our single 8GB RAM host.

---

## 1. Theoretical Concepts: What is KEDA?

In a standard Kubernetes environment, services are scaled using the **Horizontal Pod Autoscaler (HPA)**. 
* **The HPA Analogy:** HPA behaves like a room's **Air Conditioner (AC)**. It only measures internal room metrics like **temperature (CPU/Memory usage)** to decide if it should blow more cool air (run more pods). However, standard HPA cannot scale down to `0` pods—at least 1 pod must always run, wasting RAM when idle.
* **The KEDA Analogy:** KEDA acts like a **Smart Security Guard** standing at the entrance. It looks outside the room to see how many guests are arriving (events in RabbitMQ or incoming API requests). If no guests are coming, the Guard turns the AC **completely off (Scale to 0)**. When the first guest arrives, the Guard instantly turns the AC back **on (Scale to 1)**.

### The Three Pillars of KEDA
1. **Scalers:** Connectors that monitor external event sources (e.g., RabbitMQ queue lengths, database table records, or Prometheus metrics).
2. **Metrics Adapter:** Translates raw event counts (e.g., "50 messages in queue") into Kubernetes-readable metrics.
3. **Controller (Operator):** Commands Kubernetes to scale pods up or down (including all the way to `0`).

### The Event-Driven Lifecycle (Step-by-Step)
1. **Idle State (Sleep Mode):** The system has no traffic. KEDA scales the target microservice (e.g., `whisper-stt`) to **0 pods**. Memory usage for this service on the host is **0 MB**.
2. **The Event:** A user records an interview. The API gateway routes this audio to the upload pipeline, adding a processing task to the RabbitMQ queue.
3. **The Trigger:** KEDA's RabbitMQ scaler detects `1` pending message in the queue.
4. **The Wake-up:** KEDA commands the cluster API to immediately spawn `1` pod of the service.
5. **Processing:** The pod starts, reads the audio message from the queue, processes it, and finishes the task.
6. **Cooldown & Cooldown-to-Zero:** Once the queue is empty (`0` messages), KEDA waits for a configurable cooldown period (e.g., 5 minutes) to ensure no new files are uploaded. If no new events occur, KEDA terminates the pod, releasing RAM back to the host system.

---

## 2. Technical KEDA Architecture & Topology in EcoSkiller

```mermaid
graph TD
    subgraph Host VM [13.234.251.210]
        subgraph Shared Infra Cluster [6443]
            RMQ[RabbitMQ Broker]
            Postgres[(PostgreSQL 15)]
            STT[Shared Whisper STT]
        end

        subgraph Dev Cluster [6453]
            KEDA-Dev[KEDA Operator]
            Dev-Auth[dev-auth Pod]
            Dev-User[dev-user Pod]
            SO1[ScaledObject: dev-auth]
            SO2[ScaledObject: whisper-stt-trigger]
        end
    end

    KEDA-Dev -->|Monitors Queues| RMQ
    SO1 -->|Controls| Dev-Auth
    SO2 -->|Triggers| STT
```

* **Deployment Strategy:** KEDA operators are installed in the `dev` and `test` namespaces to control resource utilization locally.
* **Authentication:** KEDA uses a `TriggerAuthentication` manifest to store credentials, allowing it to authenticate with the databases/message brokers running in the `shared-infra` cluster.

---

## 3. Configuration Manifest Examples

### A. Scaling `whisper-stt` (RabbitMQ Trigger)
Scales the async transcription engine based on queue backlog.

```yaml
apiVersion: keda.sh/v1alpha1
kind: TriggerAuthentication
metadata:
  name: rabbitmq-auth
  namespace: dev-env
spec:
  secretTargetRef:
    - parameter: host
      name: rabbitmq-secret
      key: connection-string # amqp://guest:guest@172.31.40.152:5672/dev-vhost
---
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: whisper-stt-scaler
  namespace: dev-env
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: whisper-stt-service
  minReplicaCount: 0  # <--- Scale to zero when idle
  maxReplicaCount: 5  # Max pods under load
  cooldownPeriod: 300 # 5 minutes cooldown before scaling to 0
  triggers:
    - type: rabbitmq
      authenticationRef:
        name: rabbitmq-auth
      metadata:
        queueName: dev.whisper.jobs
        queueLength: "1" # Deploy 1 pod for every pending message
```

### B. Scaling HTTP APIs (Ingress Metrics Trigger)
Scales user-facing APIs based on request volume.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: user-service-http-scaler
  namespace: dev-env
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: user-service
  minReplicaCount: 0  # Scale down to 0 when idle
  maxReplicaCount: 3
  cooldownPeriod: 600 # 10 minutes idle cooldown
  triggers:
    - type: prometheus
      metadata:
        serverAddress: http://prometheus-k8s.shared-infra.svc.cluster.local:9090
        metricName: http_requests_total
        query: sum(rate(nginx_ingress_controller_requests{namespace="dev-env", service="user-service"}[2m]))
        threshold: "0.1"
```
