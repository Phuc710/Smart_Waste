Bạn là Senior Software Architect, Senior Security Engineer và Senior Code Reviewer. Hãy review toàn bộ hệ thống được cung cấp gồm:

* Android Mobile App.
* Node.js/Express Backend.
* React Web Admin.
* PostgreSQL/Supabase schema, migrations và RPC functions.
* Socket.IO realtime.
* MQTT broker và ESP32 firmware.
* Các file README/tài liệu kỹ thuật.

## Mục tiêu

Thực hiện code review thực tế, đối chiếu end-to-end giữa:

```text
Mobile/Web
→ REST API hoặc Socket.IO
→ Middleware xác thực/phân quyền
→ Controller/Route
→ Service
→ Database/RPC
→ MQTT Broker
→ ESP32 Firmware
→ ACK/Response quay lại client
```

Tìm:

1. Lỗi compile, runtime và logic.
2. Lỗi bảo mật và phân quyền.
3. Race condition, duplicate request, replay và mất đồng bộ.
4. Sai API contract giữa client, server và firmware.
5. Sai state machine hoặc transition bị thiếu.
6. Lỗi offline sync, retry và idempotency.
7. Rủi ro MQTT, Socket.IO và IoT command.
8. Lỗi database transaction, locking, constraint và index.
9. Rò rỉ tài nguyên, coroutine, listener, socket hoặc timer.
10. Lỗi Android lifecycle, background service và WebView.
11. Lỗi phần cứng/pinout hoặc logic firmware.
12. Điểm nghẽn hiệu năng, khả năng scale và single point of failure.
13. Nội dung README không khớp với code thực tế.
14. Test case quan trọng đang thiếu.

## Quy tắc bắt buộc

* Phải đọc source code thực tế trước khi kết luận.
* Không được suy ra rằng một tính năng tồn tại chỉ vì README nói có.
* Không được tự bịa tên file, số dòng, API, class, function hoặc hành vi.
* Mỗi finding phải dẫn chứng bằng:

  * Đường dẫn file thật.
  * Số dòng hoặc tên function/class cụ thể.
  * Đoạn code liên quan.
  * Luồng gây lỗi.
* Nếu không tìm thấy code để xác minh, ghi rõ:
  `CHƯA XÁC MINH – thiếu source code hoặc chưa tìm thấy implementation`.
* Phân biệt rõ:

  * `CONFIRMED`: Đã chứng minh trực tiếp từ code.
  * `LIKELY`: Có dấu hiệu mạnh nhưng cần chạy/test.
  * `DOC-ONLY`: Chỉ xuất hiện trong tài liệu.
* Không sử dụng các câu như “100% an toàn”, “hoàn toàn đồng bộ” hoặc “không có race condition” nếu chưa có test/chứng minh.
* Không xem khác biệt tên route parameter như `:id` và `:uploadId` là lỗi tương thích nếu URL thực tế không thay đổi.
* Không chỉ review README. README chỉ được dùng để đối chiếu với implementation.
* Không sửa code trong lần review đầu tiên. Trước hết phải báo cáo finding và đề xuất cách sửa.
* Nếu có thể chạy project, hãy chạy build, lint và test. Ghi rõ lệnh đã chạy và kết quả thật.
* Không được tuyên bố đã chạy test nếu thực tế chưa chạy.

## Các kiểm tra bảo mật bắt buộc

### Authentication và session

Kiểm tra:

* Token là JWT hay opaque session token.
* Token được sinh bằng CSPRNG hay không.
* Token raw và token hash được lưu ở đâu.
* Expiration, sliding session, logout và session revocation.
* Cookie có `HttpOnly`, `Secure`, `SameSite` hay không.
* HTTPS/WSS và cleartext HTTP trên Android.
* Default credential hoặc secret hard-code.
* Rate limit và brute-force protection.

### Authorization và IDOR/BOLA

Với mỗi endpoint có `:id`, kiểm tra:

* User có quyền truy cập resource đó không.
* Job có thuộc đúng tài xế đang đăng nhập không.
* Staff có thể thao tác job của staff khác không.
* Ai được phép điều khiển từng thùng rác.
* Việc kiểm tra quyền nằm trong middleware, service hay RPC.
* Có thể bypass kiểm tra bằng gọi trực tiếp RPC hay không.

Đặc biệt kiểm tra:

```text
/api/mobile/jobs/:id/accept
/api/mobile/jobs/:id/reject
/api/mobile/jobs/:id/start
/api/mobile/jobs/:id/pause
/api/mobile/jobs/:id/resume
/api/mobile/jobs/:id/collect-bin
/api/bins/:id/command
/api/incidents/uploads/:uploadId/complete
```

### MQTT và ESP32

Kiểm tra:

* TLS, authentication và topic ACL.
* Khả năng giả mạo `binId/deviceId`.
* Command có dùng retained message không.
* QoS 1 duplicate delivery.
* Firmware có deduplicate `commandId` không.
* Command có `issuedAt`, `expiresAt`, nonce hoặc signature không.
* ACK có thể bị giả mạo hoặc replay không.
* Thiết bị reconnect có thực thi lại command cũ không.
* Poller có publish lại command `sent` nhiều lần không.
* Command waiter có bị mất khi backend restart không.
* ESP32 có xử lý malformed/oversized JSON an toàn không.

### Database và concurrency

Kiểm tra:

* Transaction boundary.
* `FOR UPDATE`, optimistic concurrency và version check.
* Row count sau `UPDATE`.
* Unique constraint chống duplicate.
* Idempotency của collect-bin, self-pick, assign, cancel và upload complete.
* Hai tài xế có thể khóa cùng thùng không.
* Worker hoặc nhiều backend instance có xử lý trùng một record không.
* RPC có xác minh caller/employee ownership hay chỉ tin ID truyền vào.

### Offline GPS

Kiểm tra:

* FIFO queue có lưu bền vững qua app restart không.
* Có giới hạn kích thước queue không.
* Batch có idempotency key không.
* Điểm cũ có thể ghi đè vị trí realtime mới không.
* Timestamp client có được validate không.
* Dữ liệu có được sort/deduplicate không.
* Đơn vị `speed` là m/s hay km/h.
* Có chống GPS spoofing hoặc tọa độ bất khả thi không.

### Upload ảnh

Kiểm tra:

* Upload session có thuộc đúng user không.
* `complete` có thể finalize upload của người khác không.
* File size limit.
* MIME type và magic bytes.
* Object path có thể bị sửa không.
* Signed URL expiration.
* Direct `photoUrl` có cho phép URL tùy ý không.
* Có nguy cơ SSRF, stored XSS hoặc file độc hại không.

### Android

Kiểm tra:

* Permission foreground location trên từng Android API.
* `foregroundServiceType`.
* Lifecycle của location callback, WebView và Socket.IO listener.
* Coroutine scope và cancellation.
* Room/offline queue.
* Token storage.
* Network Security Config.
* JavaScript bridge exposure.
* Dữ liệu đưa vào `evaluateJavascript`.
* Reroute debounce/cooldown.
* Heading threshold và khoảng ngưỡng chưa xác định.
* Chuyển đổi pixel ↔ tọa độ Leaflet có đúng projection không.

### Firmware và phần cứng

Kiểm tra:

* HC-SR04 ECHO 5V có đi thẳng vào GPIO 3.3V không.
* Servo có dùng nguồn phù hợp và chung GND không.
* GPIO có xung đột với boot, flash hoặc native USB không.
* Watchdog và reconnect strategy.
* `millis()` overflow.
* Blocking delay.
* Sensor timeout và dữ liệu NaN/out-of-range.
* Clamp phần trăm mức rác về 0–100%.
* Chống servo rung hoặc nhận command lặp.
* NVS có lưu credential an toàn không.

## Quy trình review

Thực hiện theo thứ tự:

1. Liệt kê cây thư mục và xác định module.
2. Đọc README để hiểu contract dự kiến.
3. Tìm implementation thật của từng endpoint/event/topic.
4. Lập bảng mapping:

```text
Client call
→ API/event
→ server handler
→ service/RPC
→ database mutation
→ realtime/MQTT output
→ client/firmware consumer
```

5. Chạy build/lint/test nếu môi trường cho phép.
6. Review security và concurrency.
7. Đối chiếu README với source.
8. Tổng hợp finding theo severity.
9. Đề xuất test tái hiện và cách sửa.

## Định dạng kết quả bắt buộc

### 1. Executive summary

Nêu ngắn gọn:

* Hệ thống có build/chạy được không.
* Có thể deploy production hay chưa.
* Số lượng finding theo P0/P1/P2/P3.
* Ba rủi ro lớn nhất.

### 2. Findings

Sắp xếp từ nghiêm trọng nhất:

#### `[P0/P1/P2/P3] Tiêu đề finding`

* **Trạng thái:** CONFIRMED / LIKELY / DOC-ONLY
* **Vị trí:** `path/to/file:line`
* **Thành phần liên quan:** Mobile / Web / Backend / DB / MQTT / Firmware
* **Bằng chứng:** Trích đoạn code ngắn.
* **Luồng gây lỗi:** Mô tả từng bước cụ thể.
* **Tác động:** Điều gì có thể xảy ra.
* **Cách tái hiện:** Request, event hoặc test case cụ thể.
* **Cách sửa:** Giải pháp kỹ thuật rõ ràng.
* **Regression test:** Test cần thêm sau khi sửa.

Severity:

* `P0`: Có thể chiếm hệ thống, điều khiển thiết bị trái phép, mất dữ liệu nghiêm trọng hoặc gây nguy hiểm vật lý.
* `P1`: Lỗi production nghiêm trọng, sai dữ liệu, race condition hoặc gián đoạn dịch vụ.
* `P2`: Lỗi chức năng có workaround, hiệu năng hoặc maintainability đáng kể.
* `P3`: Lỗi tài liệu, naming, clean code hoặc cải tiến nhỏ.

### 3. Contract mismatch matrix

Lập bảng:

| Client/File | Client gửi/đợi | Server thực tế | Firmware/DB thực tế | Match? | Mức độ |
| ----------- | -------------- | -------------- | ------------------- | ------ | ------ |

Bao gồm REST method/path/body, response, enum, Socket event, MQTT topic/action, trạng thái và đơn vị dữ liệu.

### 4. README discrepancies

Chỉ liệt kê tài liệu không khớp source. Không trộn lỗi tài liệu với bug runtime.

### 5. Test coverage còn thiếu

Đưa ra test cụ thể cho:

* Unauthorized/forbidden access.
* Hai tài xế thao tác cùng job.
* Duplicate MQTT command.
* Retained command sau reconnect.
* Backend restart khi đang chờ ACK.
* Offline GPS batch gửi lại hai lần.
* Batch cũ đến sau realtime update.
* Upload complete của user khác.
* Socket reconnect và duplicate listener.
* Database/OSRM/MQTT unavailable.
* ESP32 nhận JSON lỗi hoặc command hết hạn.

### 6. Action plan

Chia thành:

* Việc phải sửa trước production.
* Việc nên sửa trong sprint tiếp theo.
* Việc chỉ cần cập nhật tài liệu.

## Kết luận bắt buộc

Kết luận bằng một trong ba trạng thái:

* `PRODUCTION READY`
* `CONDITIONAL GO`
* `NO-GO`

Giải thích bằng finding đã có bằng chứng. Không kết luận dựa trên độ dài hoặc mức độ chi tiết của README.
