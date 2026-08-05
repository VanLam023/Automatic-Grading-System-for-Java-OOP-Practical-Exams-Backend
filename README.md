# AGSFJOPE – Backend API Service

> **Automated Practical Exam Evaluation & Grading System for Java OOP**  
> *Core backend microservice powering automated practical exam evaluation and grading for FPT University's Java OOP curriculum.*

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Clean Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20Hexagonal-blueviolet?style=for-the-badge)](#3-architecture--tech-stack)
[![PayOS Ready](https://img.shields.io/badge/PayOS-FinTech_Integrated-0088CC?style=for-the-badge)](#4-key-engineering-highlights)

---

## 📌 1. Project Overview

**AGSFJOPE Backend** is the core processing microservice (RESTful API) responsible for automating practical exam evaluations, enforcing Object-Oriented Programming (OOP) structural rules, managing exam blocks, processing financial wallet transactions, and maintaining system-wide audit logging for Java OOP practical exams.

Built following **Clean Architecture / Hexagonal Architecture** principles, the system delivers exceptional reliability, strict evaluation consistency, fault tolerance, and high-throughput concurrent batch grading capabilities.

---

## 💡 2. Problem Solved & Core Value

In IT education, evaluating practical Java OOP programming exams manually presents significant operational hurdles:
- **Time-Consuming & Labor-Intensive**: Instructors must manually unpack projects, configure pre-compiled dependencies, and execute test suites individually for hundreds of students.
- **Subjective & Inconsistent Feedback**: Compliance checks for OOP standards (encapsulated fields, interface implementations, mandatory `@Override` annotations) are easily overlooked or inconsistently enforced across different graders.
- **Manual & Opaque Appeals Process**: Fee collection, regrading re-assignment, and refund management traditionally rely on fragmented, manual workflows.

**AGSFJOPE Backend Solution:**
- ⚡ **High-Speed Automated Evaluation**: Reduces batch grading processing time from **days down to seconds**.
- 🎯 **100% Deterministic & Objective Scoring**: Blends **automated Sandbox Test Case execution** with a custom **JavaParser AST Analysis Engine** to detect structural OOP violations with absolute precision.
- 💳 **Automated FinTech & Wallet Lifecycle**: Seamlessly integrates PayOS QR payment gateways, debits student wallets upon appeal submission, and **automatically issues refunds** directly to student wallets upon approved appeals.

---

## 🏗️ 3. Architecture & Tech Stack

### 🧱 Clean Architecture (Ports & Adapters)
The backend maintains strict isolation between Core Domain Logic and external Infrastructure adapters:

```txt
agsfjope.backend/
├── presentation/      # REST Controllers, Global Exception Handlers, DTO Requests/Responses
├── application/       # Use Cases/Services, DTOs, Ports (Inbound & Outbound Interfaces)
├── core/              # Domain Entities, Repository Interfaces, Enums, Custom Exceptions
├── infrastructure/    # JPA Implementations, JavaParser AST Analyzers, AI Adapters, PayOS, MinIO/Supabase
└── configuration/     # Security (JWT), Async/ThreadPool, Swagger Javadoc, Storage, Database Flyway
```

### 🛠️ Core Technology Stack
- **Framework & Core**: Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA, Spring AOP.
- **Database & Schema**: PostgreSQL 15, Flyway Database Migration (V1~V18).
- **Static Analysis Engine**: JavaParser AST (Abstract Syntax Tree), Java Reflection API.
- **Cloud & Object Storage**: Supabase Storage / MinIO S3 Object Storage API.
- **FinTech Payment Gateway**: PayOS API (Dynamic QR Code Generation, Webhook Signature Verification with HMAC-SHA256).
- **AI Integration**: Strategy Pattern supporting Gemini API, OpenAI API, and Claude API.
- **API Documentation**: Therapi Runtime Javadoc + SpringDoc OpenAPI.

---

## ⭐ 4. Key Engineering Highlights

### 1️⃣ Deterministic JavaParser AST Grading Engine
A static code analysis engine powered by **JavaParser** (Abstract Syntax Tree), delivering quantitative evaluation for 9 core OOP structural criteria without relying on external AI models:
- `ClassExistsHandler` & `InterfaceExistsHandler`: Verifies existence and visibility of required Classes and Interfaces.
- `FieldCheckHandler`: Validates field attributes, data types, and access modifiers (`private`, `protected`).
- `MethodSignatureHandler`: Analyzes method signatures and **enforces mandatory `@Override` annotations**.
- `ConstructorHandler` & `GetterSetterHandler`: Verifies Encapsulation standards.
- `ExtendsHandler` & `ImplementsHandler`: Validates Inheritance & Polymorphism hierarchies.
- `NamingConventionHandler`: Enforces Java Naming Conventions (CamelCase, PascalCase).

### 2️⃣ Multi-LLM Strategy Pattern
Designed using the **Strategy Pattern** to enable dynamic switching and fallback mechanisms across multiple LLM providers (Gemini, OpenAI, Claude), leveraging tailored Prompt Engineering to generate qualitative code reviews.

### 3️⃣ Isolated Sandbox Execution & Anti-Tampering Security
- **Sandbox Executor**: Compiles and executes student test cases inside an isolated runtime sandbox with strict memory limits and execution timeouts.
- **Anti-Tampering Checksum Verification**: Automatically compares cryptographic checksums of pre-compiled template `.class` files provided in exam papers. If student code modifies original exam paper binaries, the submission is **automatically awarded 0 points**.

### 4️⃣ Concurrency & High-Throughput Parallel Batch Grading
- Asynchronously processes hundreds of submissions in parallel using `CompletableFuture.runAsync()`.
- Controls server hardware load via `Semaphore(3)` throttling, permitting a maximum of 3 concurrent submission evaluations within dedicated thread pools.
- **Pre-fetch Transaction Isolation**: Pre-loads exam metadata in isolated transactions prior to worker thread dispatch, preventing Hibernate Session concurrency conflicts.

### 5️⃣ Digital Wallet & PayOS FinTech Integration
- Real-time wallet top-ups via PayOS Dynamic QR Codes backed by cryptographic Webhook signature verification.
- Complete financial transaction lifecycle tracking: `DEPOSIT`, `APPEAL_PAYMENT`, `APPEAL_REFUND`, and `WITHDRAWAL`.
- Automatic wallet debit upon appeal creation and **instant automated refund credit** when an appeal is approved (`APPROVED`) by Exam Staff.

### 6️⃣ AOP Audit Logging & Dynamic System Security
- Utilizes **Spring AOP (`@Auditable`)** to asynchronously log sensitive system operations (exam paper creation, score overrides, system configuration updates, withdrawal approvals) into `AuditLogs` without affecting API latency.
- Dynamic SMTP Email configuration loaded directly from Database (`SystemConfigs`), allowing instant email credential updates without requiring server restarts.
