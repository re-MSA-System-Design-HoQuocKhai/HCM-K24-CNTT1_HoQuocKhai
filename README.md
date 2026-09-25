# ShopMart – Báo Cáo Thực Hành Đầu Giờ Java Microservice Session 14

> **Học viên:** Hồ Quốc Khải  
> **Lớp:** HCM-K24-CNTT1  
> **Đề tài:** Nâng cấp phân hệ đặt hàng thành giao dịch phân tán (Saga Pattern) cho hệ thống thương mại điện tử ShopMart  
> **Công nghệ:** Java 17/21, Spring Boot 3.3.5, Spring Cloud 2023.0.3, Apache Kafka, Redis, MySQL, Resilience4j, Spring Cloud Gateway, Netflix Eureka, OpenFeign, Spring Cloud LoadBalancer.

---

## I. Tổng Quan Kiến Trúc Hệ Thống

Hệ thống ShopMart được xây dựng theo mô hình Microservices với nguyên tắc **Database-per-Service**:
- `config-server` (Port 8888): Quản lý cấu hình tập trung lưu tại thư mục `config-repo/`.
- `eureka-server` (Port 8761): Service Registry & Discovery.
- `api-gateway` (Port 8080): Điểm vào duy nhất (Single Point of Entry), định tuyến request tới các service và cân bằng tải client-side qua Spring Cloud LoadBalancer.
- `inventory-service` (Port 8082, instance 2: 8084): Quản lý sản phẩm, tồn kho, tích hợp Redis Caching (Cache-Aside) và Kafka Consumer Reactive (WebFlux).
- `payment-service` (Port 8083): Xử lý thanh toán, giả lập hạn mức và cổng thanh toán để kích hoạt Saga Rollback.
- `order-service` (Port 8081): Quản lý đơn hàng, gọi inventory-service qua FeignClient có bọc Resilience4j Circuit Breaker, đóng vai trò khởi xướng và điều phối Choreography Saga qua Kafka topic `order`.

```
                        +----------------------+
                        |     Client / Web     |
                        +----------+-----------+
                                   | :8080
                                   v
                        +----------------------+
                        |     API Gateway      |
                        +----------+-----------+
                                   | (lb://service)
        +--------------------------+--------------------------+
        |                          |                          |
        v :8081                    v :8082 / :8084            v :8083
+---------------+          +-------------------+      +-----------------+
| order-service | -Feign-> | inventory-service |      | payment-service |
+-------+-------+          +---------+---------+      +--------+--------+
        | (Resilience4j)             |                         |
        |                            |                         |
        +----------------------------+-------------------------+
                                     |
                                     v
                        +----------------------+
                        |  Kafka Topic "order" |
                        +----------------------+
                                     |
               (Choreography Saga / Compensating Rollback)
```

---

## II. Chi Tiết Thực Hiện Theo 4 Câu Hỏi & Tiêu Chí Chấm Điểm

### Câu 1: Hạ Tầng Config Server, Service Discovery (Eureka) & API Gateway (30 điểm)

1. **Config Server (10 điểm):**
   - Đã khai báo `@EnableConfigServer` tại `ConfigServerApplication`.
   - Cấu hình server lưu trữ tập trung dạng native tại `file:./config-repo`:
     - `config-repo/application.yml`: Cấu hình dùng chung (Eureka URL, Kafka broker & serializers, Redis host/port, Log level).
     - `config-repo/order-service.yml`: Cấu hình datasource MySQL, Resilience4j Circuit Breaker và Actuator endpoints.
     - `config-repo/inventory-service.yml`: Cấu hình datasource MySQL, Redis cache TTL và serialization.
     - `config-repo/payment-service.yml`: Cấu hình datasource MySQL, tham số giả lập lỗi `payment.simulate-failure` và hạn mức `payment.max-amount=80000000`.
   - Các business service nạp cấu hình qua `spring.config.import: optional:configserver:http://localhost:8888`.

2. **Eureka Server & Service Discovery (10 điểm):**
   - Khai báo `@EnableEurekaServer` tại `EurekaServerApplication` (Port 8761).
   - Đã bổ sung `@EnableDiscoveryClient` và dependency `spring-cloud-starter-netflix-eureka-client` vào tất cả các service (`api-gateway`, `order-service`, `inventory-service`, `payment-service`).
   - Kiểm tra trực quan trên Dashboard: `http://localhost:8761` hiển thị đầy đủ các service instances.

3. **API Gateway & Client-side Load Balancing (10 điểm):**
   - Sử dụng `spring-cloud-starter-gateway` kết hợp `spring-cloud-starter-loadbalancer`.
   - Khai báo routes linh hoạt theo prefix:
     - `/api/order/**` $\rightarrow$ `lb://order-service`
     - `/api/inventory/**` $\rightarrow$ `lb://inventory-service`
     - `/api/payment/**` $\rightarrow$ `lb://payment-service`

---

### Câu 2: Giao Tiếp Đồng Bộ Bằng FeignClient & Kháng Lỗi Circuit Breaker (20 điểm)

1. **OpenFeign & Spring Cloud LoadBalancer (10 điểm):**
   - Tạo interface `InventoryClient` với `@FeignClient(name = "inventory-service")`.
   - Khai báo các endpoint: lấy thông tin sản phẩm (`getProduct`), trừ kho (`decreaseStock`), hoàn kho (`increaseStock`).
   - Bật `@EnableFeignClients` tại `OrderServiceApplication`.
   - Cơ chế Spring Cloud LoadBalancer tự động phân phối request luân phiên qua các instances của inventory-service đã đăng ký với Eureka.

2. **Resilience4j Circuit Breaker & Cascading Failure Prevention (10 điểm):**
   - Tạo lớp `InventoryServiceFacade` bọc gọi `InventoryClient` với annotation `@CircuitBreaker(name = "inventoryService", fallbackMethod = "...")`.
   - Thiết lập cấu hình trượt (sliding window 5 calls, min 3 calls, failure rate threshold 50%, wait duration 10s):
     - **Trạng thái CLOSED:** Hoạt động bình thường. Nếu tỷ lệ gọi thất bại vượt 50% $\rightarrow$ tự động mở mạch chuyển sang OPEN.
     - **Trạng thái OPEN:** Ngắt kết nối ngay lập tức, không gửi request tới service đích (chống quá tải và ngăn chặn lỗi dây chuyền Cascading Failure), gọi ngay fallback method (`getProductFallback`).
     - **Trạng thái HALF-OPEN:** Sau 10 giây chờ, cho phép 2 request thăm dò đi qua. Nếu thành công $\rightarrow$ chuyển về CLOSED; nếu tiếp tục lỗi $\rightarrow$ quay lại OPEN.
   - Thử nghiệm minh chứng: Tắt inventory-service hoặc tạo request lỗi liên tiếp $\rightarrow$ Order-service không bị crash mà trả về fallback ngay lập tức.

3. **Minh chứng Load Balancing với 2 Instances:**
   - Khởi chạy instance 1 trên port 8082, instance 2 trên port 8084 (`-Dserver.port=8084`).
   - Gọi endpoint `GET /api/inventory/instance` qua Gateway (`http://localhost:8080/api/inventory/instance`) nhiều lần: Gateway sẽ trả về luân phiên giữa port 8082 và 8084.

---

### Câu 3: Giao Dịch Phân Tán Với Saga Pattern & Apache Kafka (25 điểm)

1. **Hạ Tầng Kafka Broker & Topic (10 điểm):**
   - Cấu hình hạ tầng trong `docker-compose.yml` gồm Kafka Broker (cổng 9092) kết nối Zookeeper (cổng 2181) và hỗ trợ native KRaft.
   - `order-service` cấu hình bean `NewTopic orderTopic()` tự động tạo topic `order` với 3 partitions và 1 replica.

2. **Choreography Saga & Compensating Transaction (15 điểm):**
   Luồng nghiệp vụ xử lý đặt hàng phân tán qua Kafka topic `order`:
   - **Bước 1:** `order-service` tạo đơn với trạng thái ban đầu `PENDING`, đồng thời publish event `ORDER_CREATED`.
   - **Bước 2 (Trừ kho):** `InventorySagaConsumer` nhận `ORDER_CREATED`:
     - Nếu đủ tồn kho: Giảm stock, publish `INVENTORY_RESERVED`.
     - Nếu thiếu tồn kho: Publish `INVENTORY_FAILED` $\rightarrow$ `order-service` nhận và cập nhật đơn thành `CANCELLED`.
   - **Bước 3 (Thanh toán):** `PaymentSagaConsumer` nhận `INVENTORY_RESERVED`:
     - Nếu hợp lệ: Xử lý thanh toán thành công, publish `PAYMENT_COMPLETED`.
     - Nếu thất bại (thanh toán giả lập lỗi hoặc số tiền đơn hàng vượt hạn mức 80.000.000đ): Cập nhật trạng thái thanh toán `FAILED`, publish `PAYMENT_FAILED`.
   - **Bước 4 (Compensating Rollback):**
     - Khi `order-service` nhận `PAYMENT_COMPLETED` $\rightarrow$ Đổi trạng thái đơn sang `COMPLETED`.
     - Khi `order-service` nhận `PAYMENT_FAILED` $\rightarrow$ Đổi trạng thái đơn sang `CANCELLED`, đồng thời gửi event `INVENTORY_RELEASED`.
     - `InventorySagaConsumer` lắng nghe `INVENTORY_RELEASED` và gọi `productService.increaseStock(...)` để **hoàn lại số lượng tồn kho** ban đầu.
   - **Chứng minh Rollback:**
     - Đặt hàng 3 chiếc MacBook (Product id=3, đơn giá 28.000.000đ, tổng tiền 84.000.000đ > hạn mức 80.000.000đ).
     - Kho hàng ban đầu giảm tồn kho $\rightarrow$ Payment thất bại $\rightarrow$ Saga kích hoạt hoàn kho $\rightarrow$ Tồn kho trở lại đúng số lượng ban đầu, đơn hàng chuyển trạng thái `CANCELLED` kèm lý do lỗi rõ ràng trong log SLF4J.

3. **Nâng Cao – Reactive WebFlux Kafka Consumer (5 điểm):**
   - Triển khai `ReactiveInventoryConsumer` bằng `reactor-kafka` và `Project Reactor Flux`.
   - Chạy độc lập trong consumer group `inventory-reactive-monitor`, lắng nghe các event trên topic `order` theo cơ chế Non-blocking Reactive Streams và commit offset tự động.

---

### Câu 4: Tối Ưu Hiệu Năng Với Distributed Caching (Redis) (10 điểm)

Áp dụng chiến lược **Cache-Aside** cho `inventory-service`:
1. **Cấu hình Redis & Serialization:**
   - Tích hợp `spring-boot-starter-data-redis` và `spring-boot-starter-cache`.
   - Cấu hình `RedisCacheManager` tùy biến key serializer (`StringRedisSerializer`), value serializer (`GenericJackson2JsonRedisSerializer`), TTL 10 phút.
2. **Sử dụng đúng bộ Annotation:**
   - `@Cacheable(value = "products", key = "#id")`: Lưu kết quả tra cứu chi tiết sản phẩm.
     - Lần gọi đầu: Truy vấn DB, in log `[CACHE MISS] Querying DB for product id=...`.
     - Các lần gọi tiếp theo: Lấy trực tiếp từ Redis cache trong RAM, không in log query DB $\rightarrow$ Tốc độ phản hồi cực nhanh.
   - `@CachePut(value = "products", key = "#id")`: Cập nhật lại thông tin vào cache khi sửa sản phẩm.
   - `@CacheEvict(value = "products", key = "#id")`: Xóa cache tương ứng khi xóa sản phẩm hoặc khi tồn kho thay đổi (trừ tồn kho, hoàn tồn kho).

---

### Câu 5: Chất Lượng Code, Kiến Trúc & Kiểm Thử (10 điểm)

1. **Clean Code & Kiến Trúc Chuẩn:**
   - Đặt tên class, package theo chuẩn Clean Architecture (controller, service, repository, entity, dto, event, config).
   - Không hard-code các thông số nhạy cảm/kết nối; ưu tiên lấy cấu hình từ Config Server.
   - Ghi log SLF4J đầy đủ các cấp độ `INFO` và `ERROR` để dễ dàng truy vết và quan sát trạng thái giao dịch phân tán.

2. **Bộ Unit Test Toàn Diện (21 tests - 100% Pass):**
   - Đã viết unit test Mockito độc lập (không phụ thuộc external infrastructure):
     - `order-service`: `OrderServiceImplTest` (4 tests), `OrderSagaConsumerTest` (3 tests) bao gồm test tạo đơn, hoàn tất đơn, hủy đơn, và test Saga Rollback khi nhận `PAYMENT_FAILED` (kiểm tra gửi event bù trừ `INVENTORY_RELEASED`).
     - `inventory-service`: `ProductServiceImplTest` (4 tests), `InventorySagaConsumerTest` (3 tests) kiểm tra trừ kho, kiểm tra ném ngoại lệ khi hết hàng, và kiểm tra Rollback khôi phục tồn kho khi nhận `INVENTORY_RELEASED`.
     - `payment-service`: `PaymentServiceImplTest` (5 tests), `PaymentSagaConsumerTest` (2 tests) kiểm tra thanh toán thành công, thanh toán vượt hạn mức, hoàn tiền và phát sinh event `PAYMENT_FAILED`.

---

## III. Hướng Dẫn Chạy & Kiểm Thử Hệ Thống

### 1. Khởi động hạ tầng Docker
```bash
docker compose up -d
```
Lệnh trên sẽ khởi chạy MySQL (3306), Zookeeper (2181), Kafka (9092) và Redis (6379).

### 2. Build toàn bộ project
```powershell
mvn clean package -DskipTests
# Hoặc chạy kiểm thử:
mvn test
```

### 3. Khởi chạy các service theo đúng thứ tự
1. **Config Server:** Run `ConfigServerApplication` (cổng 8888).
2. **Eureka Server:** Run `EurekaServerApplication` (cổng 8761) $\rightarrow$ Mở `http://localhost:8761` để xem dashboard.
3. **API Gateway:** Run `ApiGatewayApplication` (cổng 8080).
4. **Inventory Service (Instance 1):** Run `InventoryServiceApplication` (cổng 8082).
5. **Inventory Service (Instance 2):** Run với VM option `-Dserver.port=8084` để test Load Balancing.
6. **Payment Service:** Run `PaymentServiceApplication` (cổng 8083).
7. **Order Service:** Run `OrderServiceApplication` (cổng 8081).

### 4. Sử dụng Postman để kiểm thử
Import file Postman collection có sẵn tại: `postman/ShopMart.postman_collection.json`.
Bộ sưu tập gồm các thư mục:
- `1. API Gateway (Port 8080)`: Thử nghiệm toàn bộ hệ thống qua Gateway.
- `2. Inventory Service (Port 8082)`: Test cache Redis, trừ/hoàn kho.
- `3. Payment Service (Port 8083)`: Test thanh toán, giả lập lỗi vượt hạn mức, hoàn tiền.
- `4. Order Service (Port 8081)`: Test gọi Feign + Circuit Breaker, Saga Happy Path và Saga Rollback.
