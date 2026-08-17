1. Kiến trúc đúng của module NHIỆM VỤ

Nó nên rất đơn giản:

                 NHIỆM VỤ
                 JobsFragment
                      │
         ┌────────────┴────────────┐
         │                         │
     Đang xử lý                  Lịch sử
         │                         │
      Card Job                  Card Job
         │                         │
         ▼                         ▼
 JobDetailActivity       JobHistoryDetailActivity
         │
         ├── Xem tuyến đường ─────► MAP
         │
         ├── Nhận nhiệm vụ
         ├── Từ chối
         ├── Bắt đầu
         ├── Tiếp tục
         │
         ▼
 JobExecutionActivity
         │
         ├── Chỉ đường tới thùng
         ├── Mở nắp IoT
         ├── Báo sự cố
         ├── Tạm dừng
         └── Xác nhận đã thu gom
                   │
                   ▼
             Thùng tiếp theo
                   │
              gom đủ 100%
                   ▼
               COMPLETED
                   │
                   ▼
                 Lịch sử

Đây là cấu trúc tao khuyên giữ.

2. JobsFragment chỉ là trang tổng quan

Trang Nhiệm vụ chính không nên nhét toàn bộ chức năng vào card.

Card chỉ cần đủ thông tin để tài xế biết:

#JOB_172...
Trạng thái


Giao lúc / bắt đầu lúc


3 / 5 điểm


4.8 km
26 phút


Quận 1 → Quận 3

Rồi:

Click card → JobDetailActivity

Đúng như mày nói.

Không cần trên card có:

Mở nắp
Xác nhận thu gom
Báo sự cố
Điều khiển IoT

Những action này quá sâu để cho vào trang danh sách.

3. JobsFragment nên có đúng 2 tab
Đang xử lý

Bao gồm:

ASSIGNED
ACCEPTED
IN_PROGRESS
PAUSED

Backend hiện định nghĩa đúng các trạng thái này trong vòng đời job. ASSIGNED → ACCEPTED → IN_PROGRESS, IN_PROGRESS ↔ PAUSED.

Lịch sử

Bao gồm:

COMPLETED
REJECTED
EXPIRED
CANCELLED

Backend cũng có endpoint history cho job đã hoàn thành hoặc hủy.

4. Mỗi trạng thái card dẫn đến cùng JobDetailActivity

Tao khuyên không tạo 4 trang detail khác nhau.

Một:

JobDetailActivity

render theo job.status.

Status	Detail hiển thị	Action chính
ASSIGNED	Nhiệm vụ mới được giao	Từ chối / Nhận
ACCEPTED	Đã nhận, chưa bắt đầu	Bắt đầu thu gom
IN_PROGRESS	Đang thực hiện	Tiếp tục thực hiện
PAUSED	Đang tạm dừng	Tiếp tục ca
COMPLETED	Read-only	Xem lịch sử/tuyến
REJECTED	Read-only	Không action
EXPIRED	Read-only	Không action
CANCELLED	Read-only	Không action

Đây sẽ làm code sạch hơn rất nhiều.

5. ASSIGNED — nhiệm vụ vừa được giao

Trang chính:

┌────────────────────────────┐
│ #JOB_12345       Mới giao  │
│                            │
│ 5 điểm                     │
│ 4.2 km       18 phút       │
│                            │
│ Quận 1 → Quận 3            │
└────────────────────────────┘

Click:

JobDetailActivity

Chi tiết:

#JOB_12345


Trạng thái:
Mới được giao


5 điểm dừng
4.2 km
18 phút


Danh sách:
BIN_01
BIN_02
BIN_03
BIN_04
BIN_05


[Xem tuyến đường]


[Từ chối]        [Nhận nhiệm vụ]

Backend có API:

POST /api/mobile/jobs/:id/accept

và:

POST /api/mobile/jobs/:id/reject

6. Có một chỗ cần sửa ở Reject

Repository mày đang có:

rejectJob(
    jobId: String,
    reason: String? = null
)

nhưng thực tế:

api.rejectJob(jobId)

reason bị bỏ.

Backend tài liệu hiện cũng chưa mô tả body reason cho /reject.

Vậy production hiện tại có 2 lựa chọn:

A. Chỉ hỏi "Bạn chắc chắn muốn từ chối?"

hoặc nếu muốn:

Kẹt xe
Xe hỏng
Không thể thực hiện
Khác

thì backend phải bổ sung reason cho reject.

Không được cho user nhập reason xong app vứt đi.

7. ACCEPTED — đã nhận nhưng chưa chạy

Sau khi:

Nhận nhiệm vụ

backend:

ASSIGNED
   ↓
ACCEPTED

Chi tiết chuyển thành:

#JOB_12345
Đã nhận nhiệm vụ


5 điểm
4.2 km
18 phút


Danh sách 5 thùng


[Xem tuyến đường]


[Bắt đầu thu gom]

Khi bấm:

Bắt đầu thu gom

gọi:

POST /api/mobile/jobs/:id/start

Backend chuyển:

ACCEPTED
↓
IN_PROGRESS

Đúng theo contract hiện tại.

Sau đó mở:

JobExecutionActivity
8. JobDetailActivity KHÔNG nên có nút Mở nắp / Gom

Đây là điểm tao muốn refactor so với plan cũ.

Trang Detail chỉ để:

XEM
+
QUYẾT ĐỊNH
+
BẮT ĐẦU / TIẾP TỤC

Không nên:

JobDetail
→ mở nắp IoT
→ xác nhận đã gom

Hai chức năng đó chỉ nên xuất hiện trong:

JobExecutionActivity

Vì đó là thao tác thực địa.

9. Xem tuyến đường ở Detail

Có nút:

🗺 Xem tuyến đường

Bấm:

JobDetailActivity
       ↓
Map

Tao khuyên dùng Map hiện tại của app làm map chuẩn, không cần tạo thêm một map nghiệp vụ khác.

Backend đã có:

POST /api/map/route

trả:

distanceMeters
durationSeconds
coordinates
optimizedOrder

và route engine OSRM.

Vậy:

JOB
↓
targetBinIds
↓
lấy tọa độ các BIN
↓
/api/map/route
↓
MapFragment
↓
Polyline + waypoint
10. Phân biệt 2 loại “bản đồ”

Nên có:

Xem toàn tuyến

Trong JobDetailActivity:

Xem tuyến đường

→ Map trong app.

Ví dụ:

Xe
 ↓
BIN01
 ↓
BIN04
 ↓
BIN08
 ↓
BIN02
Chỉ đường tới thùng hiện tại

Trong JobExecutionActivity:

Chỉ đường

có thể:

Google Maps

hoặc map app hiện tại.

Như vậy không lẫn hai chức năng.

11. IN_PROGRESS — đây mới là màn làm việc thật

Khi job đã chạy:

JobExecutionActivity

Đây phải là page quan trọng nhất.

Layout logic nên kiểu:

#JOB_12345          Đang thực hiện


████████░░░░
3 / 5
60%


Điểm hiện tại


BIN_HCM_045
124 Nguyễn Thái Học


Mức đầy
92%


[ Chỉ đường ]


[ Mở nắp ]


[ Báo sự cố ]


[ Xác nhận đã thu gom ]


-----------------


Điểm tiếp theo
BIN_HCM_089


-----------------


[ Tạm dừng ca ]
12. Chọn Current Bin như thế nào?

Không fake.

Có:

targetBinIds =
01
02
03
04
05

và:

completedBinIds =
01
02

thì current bin:

03

Tức đơn giản:

val currentBinId =
    targetBinIds.firstOrNull {
        it !in completedBinIds
    }

Nếu route server trả optimized order thì ưu tiên thứ tự đã tối ưu.

13. Chỉ đường

Ở Current Bin:

BIN_HCM_03

bấm:

Chỉ đường

Có thể mở Google Maps:

current GPS
      ↓
BIN_HCM_03.latitude
BIN_HCM_03.longitude

hoặc mở Map internal.

Nhưng không cần tính lại một route giả ở client.

Backend đã cung cấp route API.

14. MỞ NẮP IOT

Đây mới là logic chuẩn.

Current bin:

BIN_HCM_03

tài xế gần tới nơi.

Bấm:

Mở nắp

App gọi:

POST /api/bins/BIN_HCM_03/command


{
  "action": "OPEN"
}

Server:

App
 ↓
HTTP
 ↓
Backend
 ↓
MQTT
 ↓
ESP32
 ↓
ACK
 ↓
Backend
 ↓
App

Backend tài liệu nói API chờ ACK thiết bị tối đa khoảng 4.5 giây; success 200 nghĩa là thiết bị đã thực thi, còn không phản hồi có thể 504.

UI chuẩn:

Mở nắp
  ↓
Đang gửi lệnh...
  ↓


200:
✓ Đã mở nắp


504:
Thiết bị không phản hồi
[Thử lại]
15. Nhưng Mở nắp KHÔNG phải điều kiện bắt buộc để Collect

Điểm quan trọng.

Không nên logic:

Chưa Open Lid
→ không cho Collect

Vì có thể:

tài xế mở bằng tay;
thùng đã mở;
ESP lỗi;
thao tác thực tế khác.

openLid chỉ là công cụ hỗ trợ.

Flow thật:

Tới thùng
↓
Mở nắp nếu cần
↓
Thu gom rác thật
↓
Xác nhận đã thu gom
16. Xác nhận thu gom là action quan trọng nhất

Tài xế bấm:

✓ Xác nhận đã thu gom

Có confirm:

Xác nhận đã thu gom BIN_HCM_03?


[Hủy]       [Xác nhận]

Sau đó gọi:

POST /api/mobile/jobs/:id/collect-bin

payload backend support:

{
  "binId": "...",
  "status": "COLLECTED",
  "note": "...",
  "photoUrl": "..."
}

Backend sau đó cập nhật mức rác, giải phóng bin và tự hoàn thành job nếu tất cả điểm đã được gom. Response còn trả allDone, idempotent và job mới.

Đây mới là nguồn sự thật.

17. Sau khi Collect

Ví dụ:

Trước:


completed:
01
02


current:
03

API success:

completed:
01
02
03

UI đổi:

3 / 5
60%

và tự chuyển:

Current Bin:
04

Không cần tài xế quay lại page danh sách.

18. Không fake % đầy sau thu gom

Server contract nói hành động collect-bin cập nhật mức rác về 0%.

Ở live execution, refresh bin sau API thì có thể thấy:

0%

Nhưng History thì khác.

Không được về sau lấy:

current level

rồi dựng:

92% → 0%

nếu backend không lưu snapshot lịch sử.

19. Ảnh khi xác nhận thu gom

Backend request có:

photoUrl

nên rõ ràng API đã dự tính hỗ trợ ảnh.

Nhưng tài liệu backend mày gửi chưa chứng minh API upload ảnh collect cụ thể nằm ở đâu.

Vậy UI nên thiết kế:

Ảnh minh chứng
[Tùy chọn]

nhưng chưa bật production cho tới khi xác nhận được URL ảnh được upload từ endpoint nào.

Plan cũ ghi:

prepareIncidentUpload
completeIncidentUpload

nhưng trong tài liệu backend hiện tại tao không tìm thấy 2 API đó.

=> Tạm bỏ khỏi spec chính.

20. Báo sự cố

Trong JobExecutionActivity:

⚠ Báo sự cố

nên lấy:

JOB_ID
BIN_ID hiện tại

truyền sang:

IncidentReportActivity

User chọn:

Thùng hỏng
Kẹt nắp
Cảm biến
Rác tràn
Khác

Báo xong:

quay lại JobExecutionActivity

Job vẫn tiếp tục.

Không tự đánh dấu bin đã collect chỉ vì báo sự cố.

21. Tạm dừng nhiệm vụ

Bấm:

Tạm dừng

Ở đây reason thật sự được backend support.

Backend API:

POST /api/mobile/jobs/:id/pause


{
  "reason": "Kẹt xe..."
}

và có Resume endpoint.

Flow:

IN_PROGRESS
↓
Tạm dừng
↓
Chọn lý do


Kẹt xe
Nghỉ giải lao
Sự cố xe
Khác


↓
PAUSED
22. PAUSED

Nếu mở card PAUSED:

JobsFragment
↓
JobDetailActivity

Detail:

#JOB_123


TẠM DỪNG


3 / 5


Lý do:
Kẹt xe


[ Xem tuyến ]


[ Tiếp tục ca ]

Bấm:

Tiếp tục

gọi:

resumeJob()

rồi:

PAUSED
↓
IN_PROGRESS
↓
JobExecutionActivity
23. Hoàn thành job

Điểm cuối:

5 / 5

collectBin() trả:

allDone = true

hoặc returned job:

status = COMPLETED

thì không cần một Activity mới.

Chỉ popup / full-state:

✓ Hoàn thành ca thu gom


5 / 5 điểm
Quãng đường: 4.8 km
Thời gian: 26 phút


[Xem chi tiết]
[Về danh sách]

Sau đó:

JobsFragment
→ tab Lịch sử
24. Lịch sử

Card History chỉ data thật:

#JOB_123
Hoàn thành


5 / 5
4.8 km
26 phút


26/05/2026

Không hiện:

~225 kg

vì backend Job hiện chưa có field weight trong contract. Job mẫu server chỉ có target/completed/progress/route distance/duration.

25. JobHistoryDetail

Read-only.

#JOB
Hoàn thành


Thời gian
5/5
4.8 km
26 phút


Điểm dừng


BIN01 ✓
BIN02 ✓
BIN03 ✓
BIN04 ✓
BIN05 ✓


[Xem tuyến đường]

Không có:

Mở nắp
Collect
Pause
Resume

vì job kết thúc rồi.

26. BỎ Weight khỏi UI hiện tại

Plan ảnh hiện có:

~210 kg
~540 kg
...

Tao khuyên xóa.

Backend job contract hiện không có:

weightKg
actualWeight
collectedWeight

Nếu chưa có cảm biến cân / backend data thì không được lấy:

số bin × 45 kg

để giả.

27. BỎ “Điều phối viên” nếu server không có

Backend Job có:

employee_id
employee_name
source

Trong đó employee_name là người thực hiện job, không phải tên điều phối viên.

Vậy UI cũ:

Nhân viên được giao
Nguyễn Thái Học

nếu chính tài xế đang đăng nhập thì cũng không cần lắm.

Tốt hơn hiển thị:

Nguồn nhiệm vụ:
Điều phối giao

nếu:

source = ADMIN_ASSIGNED

hoặc:

Tự nhận

cho self-pick.

28. Self-Pick từ Map

Backend còn support:

POST /api/mobile/jobs/self-pick

và job được tạo thẳng ở:

IN_PROGRESS

Flow:

Map
↓
Chọn các bin đầy
↓
Tạo job tự nhận
↓
Backend trả IN_PROGRESS
↓
JobsFragment xuất hiện job
↓
JobExecutionActivity

Không cần Accept lần nữa.

29. Không còn trang THÔNG BÁO

Đây là refactor đúng ý mày.

Backend Socket.IO có:

jobAssigned
jobUpdated
jobCompleted

Trong đó jobAssigned được gửi vào room riêng của employee; jobUpdated và jobCompleted dùng để đồng bộ trạng thái.

Mobile nên:

Socket jobAssigned
        ↓
refresh Jobs
        ↓
Card ASSIGNED nhảy lên đầu
        ↓
badge "Mới được giao"
        ↓
Snackbar:
"Bạn có nhiệm vụ mới"

KHÔNG:

Socket
↓
NotificationActivity
↓
Danh sách thông báo
30. Realtime chỉ báo thay đổi, REST vẫn là source of truth

Đây là kiến trúc tao khuyên:

Socket:
jobUpdated
   ↓
"có thay đổi"


REST:
getActiveJob()
getHistory()
   ↓
lấy data thật
   ↓
render

Đừng dùng Socket event rồi tự patch 10 field trong UI nếu không cần.

Như vậy app ít bị lệch server.

31. Những phần plan cũ nên bỏ

Các thứ này chưa thấy backend support rõ, nên đừng nhét vào Jobs lúc này:

❌ Trang Thông báo riêng
❌ Biển số xe
❌ Tải trọng xe
❌ Mức nhiên liệu
❌ Shift 8 tiếng
❌ Availability online/offline của tài xế
❌ Cài đặt cảnh báo >85% trong Jobs
❌ Weight tự tính
❌ Dispatcher name giả
❌ Ảnh history giả
❌ timestamp từng bin lấy từ completedAt job
❌ priority nếu backend không có field


Full CASE của NHIỆM VỤ tao chốt
CASE 03
Confirm Collect
→ Success


CASE 10
Collect Fail
→ Retry


CASE 11
Report Incident


CASE 12
Pause
→ nhập reason


CASE 13
PAUSED Detail
→ Resume


CASE 14
Current Bin tự chuyển sang bin tiếp theo


CASE 15
AllDone
→ Complete Dialog


CASE 16
Không có nhiệm vụ active


CASE 17
Active API Error
→ Retry


CASE 18
History List


CASE 19
Completed History Detail


CASE 20
Cancelled History Detail


CASE 21
Rejected History Detail


CASE 22
Expired History Detail


CASE 23
History Empty


CASE 24
History API Error


CASE 25
Self-pick Job từ Map
→ IN_PROGRESS


CASE 26
Realtime jobAssigned
→ refresh + highlight card


CASE 27
Realtime jobUpdated
→ refresh current job


CASE 28
Realtime jobCompleted
→ chuyển Active → History
33. Folder tao sẽ tổ chức như này
ui/jobs/


├── JobsFragment.kt
├── JobsViewModel.kt
│
├── ActiveJobsAdapter.kt
│
├── JobDetailActivity.kt
├── JobDetailViewModel.kt
│
├── JobExecutionActivity.kt
├── JobExecutionViewModel.kt
│
└── dialogs/
    ├── RejectJobDialog
    ├── PauseJobDialog
    ├── ConfirmCollectDialog
    └── JobCompletedDialog


ui/history/


├── HistoryAdapter.kt
└── JobHistoryDetailActivity.kt


ui/map/


├── MapFragment.kt
└── MapViewModel.kt


ui/incident/


└── IncidentReportActivity.kt

Không cần:

NotificationFragment
NotificationActivity
NotificationAdapter
Và đây là flow cuối cùng tao chọn
                 JOBS
                   │
             ┌─────┴─────┐
             │           │
           ACTIVE      HISTORY
             │           │
           CARD         CARD
             │           │
             ▼           ▼
        JOB DETAIL   HISTORY DETAIL
             │
     ┌───────┼─────────┐
     │       │         │
   Accept   Map      Reject
     │
    Start
     │
     ▼
 JOB EXECUTION
     │
 ┌───┼────────┬────────────┐
 │   │        │            │
Map Open Lid Incident    Pause
 │
 ▼
Current Bin
 │
Collect
 │
Next Bin
 │
Collect
 │
100%
 │
COMPLETED
 │
 ▼
HISTORY








Đây mới là module Nhiệm vụ gọn và đúng product: JobsFragment là hub, JobDetailActivity là cửa vào của từng job, còn toàn bộ thao tác thực địa như mở nắp / chỉ đường / xác nhận thu gom nằm trong JobExecutionActivity.

Và có 3 chỗ backend/code nên sửa trước khi đóng UI: rejectJob(reason) hiện không gửi reason, getJobDetail() đang phải dò active + 100 history thay vì có API detail riêng, và History phải bỏ toàn bộ weight/photo/time-per-stop giả nếu backend chưa trả các field đó.