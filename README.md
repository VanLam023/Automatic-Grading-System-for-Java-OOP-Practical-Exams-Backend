# AGSFJOPE – Backend API Service

> **Automated Practical Exam Evaluation & Grading System for Java OOP**  
> *Hệ thống chấm điểm tự động bài thi thực hành Lập trình Hướng đối tượng Java (FPT University)*

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot 3.x](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Clean Architecture](https://img.shields.io/badge/Architecture-Clean%20%2F%20Hexagonal-blueviolet?style=for-the-badge)](#-kiến-trúc-hệ-thống)
[![Unit Tests](https://img.shields.io/badge/Service_Tests-100%25_Coverage-success?style=for-the-badge&logo=junit5&logoColor=white)](#-chất-lượng-code--kiểm-thử-unit-test)

---

## 📌 1. Dự án này là gì? (Project Overview)

**AGSFJOPE Backend** là hệ thống xử lý trung tâm (Core Microservice/RESTful API) chịu trách nhiệm tự động hóa toàn bộ quy trình chấm điểm, đánh giá cấu trúc hướng đối tượng (OOP), quản lý kỳ thi, vận hành tài chính phúc khảo và ghi nhật ký hoạt động cho bài thi thực hành Java OOP.

Hệ thống được thiết kế theo chuẩn **Clean Architecture / Hexagonal Architecture**, đáp ứng các tiêu chuẩn khắt khe về độ tin cậy, tính nhất quán, khả năng mở rộng và hiệu năng xử lý cao.

---

## 💡 2. Dùng để làm gì? (Problem Solved & Value)

Trong môi trường đào tạo CNTT, việc chấm bài thi lập trình Java OOP thủ công gặp nhiều thách thức:
- **Tốn thời gian & công sức**: Giảng viên phải mở từng project, tải dependency, chạy test thủ công cho hàng trăm sinh viên.
- **Tính cảm quan & Thiếu nhất quán**: Đánh giá vi phạm chuẩn OOP (encapsulated fields, interface implementation, override annotations...) dễ bị bỏ sót hoặc không đồng đều giữa các giảng viên.
- **Quy trình phúc khảo chậm trễ**: Xử lý thu phí, phân công chấm lại và hoàn tiền diễn ra thủ công.

**Giải pháp của AGSFJOPE Backend:**
- ⚡ **Chấm tự động siêu tốc**: Rút ngắn thời gian chấm toàn khóa thi từ **vài ngày xuống còn vài giây**.
- 🎯 **Đánh giá chuẩn xác 100%**: Kết hợp giữa **Chạy Test Case tự động** và **Engine Phân tích Cấu trúc AST (JavaParser)** để bắt chính xác từng lỗi vi phạm OOP.
- 💳 **Tự động hóa Fintech & Ví điện tử**: Tích hợp thanh toán QR PayOS, trừ phí ví khi tạo phúc khảo và **hoàn tiền tự động vào ví sinh viên** ngay khi phúc khảo được chấp thuận.

---

## 🏗️ 3. Kiến trúc & Công nghệ (Architecture & Tech Stack)

### 🧱 Mô hình Clean Architecture (Ports & Adapters)
Hệ thống phân tách tuyệt đối giữa Domain Logic và các Infrastructure bên ngoài:

```txt
agsfjope.backend/
├── presentation/      # REST Controllers, Global Exception Handlers, DTO Requests/Responses
├── application/       # UseCases/Services, DTOs, Ports (Inbound & Outbound Interfaces)
├── core/              # Domain Entities, Repositories Interfaces, Enums, Custom Exceptions
├── infrastructure/    # JPA Implementations, JavaParser AST Analyzers, AI Adapters, PayOS, MinIO/Supabase
└── configuration/     # Security (JWT), Async/ThreadPool, Swagger Javadoc, Storage, Database Flyway
```

### 🛠️ Tech Stack Chính
- **Core Framework**: Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA, Spring AOP.
- **Database & Migration**: PostgreSQL 15, Flyway Migration (V1~V18).
- **Static Analysis Engine**: JavaParser AST (Abstract Syntax Tree), Java Reflection API.
- **Storage & Cloud**: Supabase Storage / MinIO S3 Object Storage API.
- **Payment & FinTech**: PayOS API (QR Code Payment, Webhook Signature HMAC-SHA256).
- **AI Integration**: Strategy Pattern cho Gemini API, OpenAI API, Claude API.
- **Documentation**: Therapi Runtime Javadoc + SpringDoc OpenAPI.

---

## ⭐ 4. Điểm nhấn Kĩ thuật Nổi bật (Engineering Highlights)

### 1️⃣ Deterministic JavaParser AST Grading Engine (Chấm điểm Cấu trúc OOP không dùng AI)
Engine phân tích cú pháp tĩnh dựa trên cây cú pháp trừu tượng (AST) của **JavaParser**, hỗ trợ kiểm tra định lượng 9 tiêu chí OOP mà không cần phụ thuộc vào AI:
- `ClassExistsHandler` & `InterfaceExistsHandler`: Kiểm tra sự tồn tại của Lớp và Giao diện.
- `FieldCheckHandler`: Kiểm tra thuộc tính, kiểu dữ liệu, modifier (`private`, `protected`).
- `MethodSignatureHandler`: Phân tích chữ ký phương thức và **kiểm tra bắt buộc annotation `@Override`**.
- `ConstructorHandler`, `GetterSetterHandler`: Kiểm tra tính đóng gói (Encapsulation).
- `ExtendsHandler` & `ImplementsHandler`: Kiểm tra quan hệ Kế thừa & Đa hình.
- `NamingConventionHandler`: Kiểm tra quy chuẩn đặt tên Java (CamelCase, PascalCase).

### 2️⃣ Multi-LLM Strategy Pattern (Chấm điểm hỗ trợ bởi AI)
Thiết kế theo **Strategy Pattern** cho phép hệ thống linh hoạt chuyển đổi hoặc fallback giữa các nhà cung cấp LLM (Gemini, OpenAI, Claude) kèm Prompt Engineering tối ưu để sinh nhận xét mã nguồn tự động.

### 3️⃣ Isolated Sandbox Execution & Anti-Tampering Security
- **Sandbox Executor**: Biên dịch và chạy test cases sinh viên trong môi trường isolated cách ly với giới hạn thời gian (Timeout) và bộ nhớ khắt khe.
- **Anti-Tampering Checksum**: Tự động so sánh Checksum của các file `.class` tiền biên dịch (precompiled template interfaces/classes của đề thi). Nếu phát hiện sinh viên chỉnh sửa file gốc của đề thi -> **Tự động kích hoạt điểm 0 bài nộp**.

### 4️⃣ Concurrency & Parallel Submission Batch Processing
- Xử lý hàng trăm bài nộp song song bằng `CompletableFuture.runAsync()`.
- Kiểm soát tài nguyên máy chủ thông qua `Semaphore(3)` giới hạn tối đa 3 bài nộp được chấm đồng thời trong thread pool riêng biệt.
- **Pre-fetch Transaction Isolation**: Đọc cấu hình bài thi và đề thi trong Transaction độc lập trước khi đẩy vào worker thread, tránh xung đột ORM Hibernate Session.

### 5️⃣ Hệ thống Ví điện tử & Tích hợp Cổng thanh toán PayOS
- Hỗ trợ Nạp tiền ví qua PayOS Dynamic QR Code với Webhook xác thực chữ ký an toàn.
- Vòng đời giao dịch ví rõ ràng: `DEPOSIT`, `APPEAL_PAYMENT`, `APPEAL_REFUND`, `WITHDRAWAL`.
- Tự động trừ tiền ví khi sinh viên phúc khảo và **tự động hoàn tiền vào ví sinh viên** nếu đơn phúc khảo được Exam Staff duyệt **APPROVED**.

### 6️⃣ AOP Audit Logging & Security System
- Sử dụng **Spring AOP (`@Auditable`)** ghi nhận vết mọi thao tác nhạy cảm (Tạo đề thi, sửa điểm, đổi cấu hình hệ thống, duyệt rút tiền) vào bảng `AuditLogs` bất đồng bộ mà không ảnh hưởng latency của API.
- Cấu hình SMTP Email linh hoạt đọc trực tiếp từ Database (`SystemConfigs`), thay đổi cấu hình mail không cần khởi động lại Server.

---

## 🧪 5. Chất lượng Code & Kiểm thử (Unit Test Coverage)

Dự án áp dụng quy chuẩn kiểm thử diện rộng cho toàn bộ tầng **Service Implementation (`*ServiceImpl.java`)**:
- Áp dụng ma trận Test Case **Decision Table**: (N) Normal, (B) Boundary, (A) Abnormal.
- Bao phủ 100% các Use Cases chính: `AuthService`, `BlockService`, `ExamPaperService`, `SubmissionService`, `GradingService`, `StaffAppealService`, `LecturerAppealService`, `WalletService`, `NotificationService`, `ExamStatisticsService`.
- Tự động sinh báo cáo kiểm thử đạt chuẩn mẫu báo cáo kỹ thuật.

---

## 🚀 6. Hướng dẫn Cài đặt & Chạy ứng dụng

### Yêu cầu hệ thống
- Java Development Kit (JDK) 17+
- Apache Maven 3.8+
- PostgreSQL 15+
- Docker & Docker Compose (Tùy chọn)

### Các bước khởi chạy

1. **Clone Repository**
   ```bash
   git clone https://github.com/VanLam023/AGSFJOPE-Backend.git
   cd AGSFJOPE-Backend
   ```

2. **Cấu hình Biến Môi trường**
   Tạo file `src/main/resources/application-local.yml` hoặc thiết lập môi trường:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/oop_exam_db
       username: postgres
       password: your_password
   ```

3. **Biên dịch & Khởi chạy**
   ```bash
   # Biên dịch dự án và chạy Flyway DB Migration
   mvn clean package -DskipTests

   # Khởi chạy ứng dụng
   mvn spring-boot:run
   ```

4. **Truy cập Swagger API Documentation**
   Sau khi server khởi động (mặc định port `8080`):
   `http://localhost:8080/swagger-ui.html`

---

## 📝 License & Contact
- **Project**: FPT University Capstone Project
- **Authors**: AGSFJOPE Development Team
- **Contact**: `lamtvse173173@fpt.edu.vn`
