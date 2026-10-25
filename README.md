# Network Analyst - Ứng dụng Phân tích & Chẩn đoán Mạng Android

Ứng dụng Android chẩn đoán mạng chuyên nghiệp, xây dựng bằng **Kotlin**, **Jetpack Compose + Material 3**, **Coroutines / Flow**, **Hilt**, và **Room Database**.

---

## 📱 Yêu cầu hệ thống & Công nghệ
- **Hệ điều hành**: Android 8.0 (API level 26 - `minSdk 26`) đến Android 15 / 16 (`compileSdk 35`, `targetSdk 35`).
- **Ngôn ngữ**: Kotlin 2.0.21.
- **Giao diện**: Jetpack Compose với Material Design 3, Dark & Light Mode, hỗ trợ Dynamic Color trên Android 12+.
- **Kiến trúc**: MVVM + Clean Architecture + Unidirectional Data Flow (UDF).
- **Dependency Injection**: Google Dagger Hilt 2.51.1 (KSP).
- **Cơ sở dữ liệu cục bộ**: AndroidX Room 2.6.1 (KSP) lưu lịch sử kiểm tra.
- **DNS Library**: `dnsjava:dnsjava:3.6.2` cho phép tra cứu đa dạng bản ghi DNS và tùy biến resolver.

---

## 🛠 Xử lý các ràng buộc đặc thù của Android

### 1. Quản lý quyền truy cập (Permissions)
- **Quyền bình thường (Manifest)**:
  - `INTERNET`: Truy vấn socket, DNS, HTTP, ICMP.
  - `ACCESS_NETWORK_STATE`: Giám sát trạng thái kết nối mạng qua `ConnectivityManager`.
  - `ACCESS_WIFI_STATE`: Truy xuất thông số Wi-Fi qua `WifiManager`.
  - `CHANGE_WIFI_MULTICAST_STATE`: Mở MulticastLock phục vụ khám phá dịch vụ mDNS / Bonjour.
- **Quyền runtime kèm màn hình giải thích (Rationale Dialog)**:
  - `ACCESS_FINE_LOCATION`: Bắt buộc từ Android 8.0+ để lấy tên mạng Wi-Fi (`SSID`) và địa chỉ `BSSID`. Ứng dụng giải thích rõ ràng cho người dùng rằng quyền này chỉ để đọc thông tin mạng, không theo dõi định vị vật lý.
  - `NEARBY_WIFI_DEVICES`: Bắt buộc trên **Android 13+ (API 33+)** với cờ `neverForLocation` để quét thiết bị lân cận mà không cần định vị vị trí.
  - `READ_PHONE_STATE`: **Chỉ yêu cầu theo ngữ cảnh (On-Demand)** khi người dùng chủ động mở mục xem chi tiết SIM / Nhà mạng di động.
    > [!NOTE]
    > **Ràng buộc bảo mật & chống fingerprinting của Android**:
    > Việc truy xuất thế hệ mạng di động chi tiết (4G LTE / 5G NR) qua `TelephonyManager.getDataNetworkType()` và danh sách thẻ SIM qua `SubscriptionManager.getActiveSubscriptionInfoList()` yêu cầu quyền `READ_PHONE_STATE` (xem tài liệu Android về TelephonyManager) để ngăn chặn việc thu thập định danh phần cứng (device fingerprinting).
    > **Cơ chế Fallback mềm dẻo**: Ứng dụng không bao giờ ép người dùng cấp quyền này khi khởi động. Nếu người dùng từ chối, ứng dụng vẫn hoạt động 100% bình thường trên mọi công cụ (LAN Scan, Port Scan, Ping, Traceroute, DNS, Whois) và hiển thị thông tin mạng di động ở mức tổng quát (`TRANSPORT_CELLULAR` - "Mobile Data") qua `NetworkCapabilities` công khai mà không gây crash hay gián đoạn trải nghiệm.

### 2. Dò tìm thiết bị LAN không dựa vào `/proc/net/arp`
- Trên Android 10+ (API 29+), hệ điều hành đã chặn quyền truy cập vào `/proc/net/arp`.
- **Giải pháp thay thế tích hợp trong ứng dụng**:
  1. **Thăm dò song song (Parallel TCP Probing)**: Quét toàn bộ dải IP mạng con (Subnet /24) qua Coroutine kết hợp `Semaphore(40)` trên các cổng LAN thông dụng (80, 443, 22, 445, 8080, 53, 8008, 9100).
  2. **NsdManager (mDNS / Bonjour)**: Khám phá dịch vụ mạng cục bộ (`_http._tcp.`, `_workstation._tcp.`, `_googlecast._tcp.`, `_ipp._tcp.`, `_airplay._tcp.`) và phân giải IP / tên thiết bị.
  3. **UDP NetBIOS Name Service (Port 137)**: Gửi gói tin RFC 1002 NBSTAT wildcard query tới từng IP. Khi thiết bị phản hồi, ứng dụng trích xuất được **Computer Name** (Windows / Samba / NAS) và **địa chỉ MAC card mạng thật** của thiết bị.
  4. **UDP LLMNR (Port 5355)**: Gửi truy vấn Link-Local Multicast Name Resolution phân giải hostname nội bộ.
  5. **UDP SNMP (Port 161)**: Thăm dò SNMP v1/v2c OID `1.3.6.1.2.1.1.1.0` (sysDescr) với community `public` để nhận dạng các thiết bị mạng chuyên dụng (Cisco, MikroTik, Synology NAS, máy in HP, Canon, Epson).
  6. **Cơ sở dữ liệu OUI & Nhận dạng Nhà sản xuất (IEEE Standards)**:
     - **Nguồn gốc dữ liệu**: Hiện tại tệp `app/src/main/res/raw/oui_database.txt` là **DỮ LIỆU MẪU, 207 hãng** (chứa các hãng thiết bị phổ biến như Cisco, Apple, Espressif, Intel, Samsung, TP-Link, Realtek, Raspberry Pi...). Để có CSDL hoàn chỉnh 35.000+ hãng, người dùng tự tải tệp `oui.csv` từ IEEE đặt vào `scripts/data/oui.csv` và chạy script `scripts/generate_oui_data.py`. Script hỗ trợ trích xuất toàn diện cả 3 khối chuẩn IEEE: **MA-L** (24-bit OUI - 6 ký tự hex), **MA-M** (28-bit OUI - 7 ký tự hex), và **MA-S** (36-bit OUI - 9 ký tự hex), xử lý chính xác các trường CSV có dấu phẩy và dấu ngoặc kép.
     - **Phát hiện địa chỉ MAC ngẫu nhiên (Locally Administered MAC)**: Theo tiêu chuẩn IEEE 802, bit thứ 2 của byte đầu tiên (`b1` của byte 0, tương ứng `firstByte & 0x02 != 0`) đánh dấu địa chỉ MAC được quản lý cục bộ hoặc ngẫu nhiên hóa (Private/Random MAC) - tính năng mặc định trên Android 10+, iOS 14+ và Windows 10/11. Ứng dụng tự động phát hiện cờ này và hiển thị nhãn `"Private / Random MAC"`, tuyệt đối không gán nhãn sai cho bất kỳ nhà sản xuất nào.
     - **Chuẩn hóa MAC Address linh hoạt (`OuiLookupHelper`)**: Hỗ trợ chuẩn hóa mọi định dạng MAC phổ biến (`AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`, định dạng không dấu phân cách `AABBCCDDEEFF`, hoặc chữ hoa/chữ thường) trước khi tra cứu tiền tố OUI với độ ưu tiên giảm dần: MA-S (9 hex) -> MA-M (7 hex) -> MA-L (6 hex).
     - **Đối chiếu mẫu dịch vụ**: Kết hợp hostname mDNS/NetBIOS để định danh chính xác thiết bị (Apple, Samsung, Google Cast, Raspberry Pi, TP-Link, Windows PC, Linux Server).

### 3. Công cụ Ping độ tin cậy cao
- Hỗ trợ cả 2 cơ chế:
  - **Lệnh ping hệ thống qua `ProcessBuilder` (Ưu tiên)**: Gọi trực tiếp `/system/bin/ping` với các tham số `-c <count> -W <timeoutSec> <target>`.
    > [!NOTE]
    > **Thực tế về lệnh ping trên Android**: Các ứng dụng Android thông thường hoàn toàn có thể thực thi `/system/bin/ping` mà không cần quyền root. Thách thức kỹ thuật thực sự nằm ở việc các nhà sản xuất thiết bị (OEM như Samsung, Xiaomi, Google Pixel) sử dụng các bản dựng binary khác nhau (toybox, iputils, bản tùy biến riêng của hãng) dẫn đến sự khác biệt về cờ tham số (ví dụ `-t` vs `-m`) và định dạng chuỗi đầu ra.
    > Ứng dụng xử lý bằng bộ phân tích `PingOutputParser` đa biến thể trích xuất linh hoạt dữ liệu từng gói tin (`bytes`, `seq`, `ttl`, `time ms`, `time<1 ms`) và bảng thống kê truyền/nhận, tỷ lệ mất gói (`% packet loss`), độ trễ `min/avg/max/mdev`. Hỗ trợ bọc `withTimeoutOrNull` và `destroyForcibly()` chống treo process. *(Lưu ý: Các mẫu kiểm thử trong bộ unit test là fixture tự soạn theo định dạng chuẩn của iputils và toybox, chưa được kiểm chứng thực tế trên từng thiết bị phần cứng của các hãng máy).*
  - **`InetAddress.isReachable(timeout)` (Dự phòng cuối cùng)**:
    > [!IMPORTANT]
    > **Lưu ý kỹ thuật về `InetAddress.isReachable` trên Android**:
    > Trên thiết bị Android không root, ứng dụng thông thường không có quyền mở raw ICMP socket (`CAP_NET_RAW`). Vì vậy, `isReachable()` sẽ cố gắng gửi ICMP echo request, nhưng khi bị từ chối quyền sẽ tự động chuyển sang thử kết nối TCP port 7 (Echo). Do hầu hết máy chủ hiện đại chặn cổng TCP 7, phương thức này có thể trả về `false` ngay cả khi máy chủ đang online hoặc RTT không phản ánh đúng ICMP RTT thực tế. Ứng dụng chỉ dùng cơ chế này làm giải pháp dự phòng khi binary ping hệ thống không khả dụng.

### 4. Công cụ Traceroute với TTL tăng dần & Tùy chọn GeoIP
- Sử dụng tiện ích ping hệ điều hành với cờ `-t <ttl>`, tăng dần TTL từ `1` đến `30`.
- Bắt gói tin ICMP `Time to live exceeded` qua `TracerouteOutputParser` để xác định địa chỉ IP và độ trễ tại từng router trung gian.
- **Tương thích định dạng binary ping**: Tự động phát hiện nếu binary ping trên thiết bị không hỗ trợ cờ `-t` và báo lỗi rõ ràng thay vì hiện toàn bộ hop timeout. *(Các bộ test fixture được soạn theo quy cách iputils/toybox chuẩn)*.
- Bọc timeout cho từng hop và gọi `process.destroyForcibly()` khi dừng hoặc timeout, tránh rò rỉ tiến trình chạy ngầm.
- **Tùy chọn hiển thị quốc gia từng hop (GeoIP)**:
  - Công tắc "Hiện quốc gia của từng hop" (Mặc định: **TẮT**), kèm thông báo giải thích IP sẽ được gửi tới dịch vụ bên thứ ba.
  - Tự động bỏ qua các IP nội bộ / Private / CGNAT (100.64.0.0/10) / Loopback, hiển thị nhãn "Private / LAN" mà **không gửi bất kỳ yêu cầu nào ra Internet**.
  - Dữ liệu cung cấp qua HTTPS bởi `ipwho.is`, có bộ nhớ đệm (cache), giới hạn tốc độ và xử lý lỗi mềm dẻo. Tra cứu bất đồng bộ không làm chậm tiến trình traceroute.

### 5. Quét cổng mạng (Port Scanner)
- Chạy hoàn toàn trên `Dispatchers.IO` thông qua Coroutines.
- **Quản lý đồng thời an toàn**: Sử dụng `Semaphore(200)` mặc định (có thể điều chỉnh, giới hạn trần 500) để tối ưu tốc độ quét mà không cạn kiệt File Descriptors (`EMFILE`).
- **Triệt tiêu rò rỉ File Descriptors**: Khi coroutine bị hủy hoặc tạm dừng, ứng dụng đăng ký `invokeOnCancellation { socket.close() }` giúp ngắt ngay lập tức lệnh kết nối `Socket.connect()` chặn và đóng socket tức thì.
- **Timeout thích ứng (Adaptive Timeout)**: Tự động phân giải IP trước khi quét; đối với địa chỉ Private IP / LAN (RFC 1918, link-local, CGNAT 100.64.0.0/10) timeout ngắn (~200ms); đối với IP công cộng WAN timeout dài hơn (~500ms).
- **Phân loại trạng thái cổng chuẩn**:
  - `OPEN`: Kết nối socket thành công.
  - `CLOSED`: Nhận gói tin RST (`ConnectException`).
  - `FILTERED`: Hết thời gian chờ (`SocketTimeoutException`).
  - `ERROR`: Lỗi hệ thống mạng khác (`ENETUNREACH`, `EPERM`...).
- **Tạm dừng và Tiếp tục (Pause/Resume)**: Cho phép tạm dừng và tiếp tục quét các cổng còn lại mà không làm mất kết quả đã quét.
- **3 chế độ quét**: Cổng phổ biến (28 cổng), Dải cổng tùy chọn linh hoạt (hỗ trợ phân tích `80, 443, 8000-8080`), và Toàn dải 1 - 65535 (kèm tính toán ước tính thời gian trước khi quét và hộp thoại xác nhận).
- **Banner Grabbing an toàn**: Tùy chọn bật/tắt (mặc định tắt), giới hạn đọc tối đa 1024 bytes và timeout đọc 500ms.

### 6. Tra cứu bản ghi DNS với tùy chọn Resolver
- Thư viện `dnsjava 3.6.2` hỗ trợ đầy đủ các loại bản ghi: `A`, `AAAA`, `CNAME`, `MX`, `TXT`, `NS`, `SOA`, `PTR`, `SRV`, `CAA`, `HTTPS` (Type 65), `SVCB` (Type 64).
- Hiển thị TTL của từng bản ghi và thời gian phản hồi truy vấn tính bằng mili-giây (ms).
- Bảo vệ R8/ProGuard với các quy tắc keep và dontwarn cho `org.xbill.DNS.**`.

### 7. Cảnh báo an toàn quét mạng (Authorization Disclaimer)
- Hiển thị hộp thoại cảnh báo bắt buộc: "Chỉ quét mạng do bạn sở hữu hoặc được ủy quyền" trước khi người dùng thực hiện tác vụ Quét LAN hoặc Quét cổng.
- Người dùng phải xác nhận đồng ý mới có thể tiếp tục. Trạng thái xác nhận được lưu trữ an toàn trong SharedPreferences.
- Cho phép người dùng linh hoạt lựa chọn máy chủ phân giải:
  - **Hệ thống (Mặc định)**: Tự động phát hiện DNS từ Wi-Fi / Mạng di động hiện hành.
  - **Cloudflare DNS**: `1.1.1.1` / `1.0.0.1`
  - **Google Public DNS**: `8.8.8.8` / `8.8.4.4`
  - **Quad9 DNS**: `9.9.9.9` (Bảo mật, chặn độc hại)
  - **OpenDNS**: `208.67.222.222`
  - **AdGuard DNS**: `94.140.14.14` (Chặn quảng cáo)
  - **Tùy chỉnh**: Nhập bất kỳ địa chỉ IP máy chủ DNS riêng.
- Hiển thị thời gian phản hồi truy vấn tính bằng mili-giây (`ms`).

### 7. Chẩn đoán thông tin mạng chi tiết
- Sử dụng `ConnectivityManager`, `LinkProperties` và `NetworkCapabilities`:
  - **Nhận diện loại kết nối**: Wi-Fi, Mạng di động (LTE / 5G), Ethernet.
  - **Phát hiện VPN**: Cảnh báo khi có kết nối VPN đang hoạt động trên giao diện (ví dụ `tun0`).
  - **Phát hiện Điểm phát sóng (Hotspot / Tethering)**: Kiểm tra trạng thái hotspot đang chia sẻ mạng (`ap0`, `tether`).
  - **Định tuyến & Cấu hình IP**: Default Gateway, danh sách máy chủ DNS, máy chủ DHCP, IPv4 nội bộ, IPv6, Subnet Mask, Broadcast Address, tên giao diện card mạng (`interfaceName`).
  - **Thông số Wi-Fi**: Tên mạng (SSID), BSSID, RSSI (dBm), % tín hiệu, tần số (MHz), kênh sóng (Channel), tốc độ liên kết (Link Speed Mbps), chuẩn Wi-Fi (2.4 GHz, 5 GHz, 6 GHz Wi-Fi 6E/7).
  - **Thông tin WAN / Internet công cộng**: Tự động tra cứu địa chỉ IP công cộng (Public IP), nhà cung cấp Internet (ISP), và vị trí địa lý.

### 8. Tra cứu Whois / RDAP (Tên miền, IP, ASN)
- **Ưu tiên chuẩn RDAP hiện đại**:
  - Tra cứu cấu trúc thông qua IANA Bootstrap Registry (`data.iana.org/rdap/`): `dns.json` cho tên miền/TLD, `ipv4.json` & `ipv6.json` cho dải IP, và `asn.json` cho Autonomous System Numbers (ASN).
  - Có cơ chế lưu đệm (cache) file bootstrap để giảm thiểu truy vấn mạng.
  - Dự phòng qua `rdap.org` chỉ khi IANA bootstrap không tìm thấy máy chủ phân giải ủy quyền.
  - Xử lý đầy đủ HTTP Redirect (301, 302, 307, 308), 404 (Không tìm thấy), 429 (Bị giới hạn tốc độ), và timeout kết nối.
- **Dự phòng Whois giao thức TCP Cổng 43**:
  - Truy vấn `whois.iana.org` trên cổng 43 để tìm máy chủ quản lý ủy quyền (`refer: <server>`).
  - Tự động truy vấn máy chủ referral tiếp theo (tối đa 3 lần chuyển tiếp).
  - Cơ chế bảo vệ SSRF: Xác thực địa chỉ referral, nghiêm cấm chuyển tiếp tới các IP nội bộ / localhost (`127.0.0.1`, RFC 1918).
  - Giới hạn dung lượng nhận dữ liệu tối đa 64 KB và thời gian chờ 5 giây.
- **Chế độ hiển thị linh hoạt**:
  - **Chế độ Tóm tắt (Summary)**: Chủ sở hữu, đơn vị đăng ký (Registrar), ngày tạo, ngày hết hạn, ngày cập nhật, trạng thái, dải CIDR, máy chủ tên miền (Nameservers), liên hệ lạm dụng/kỹ thuật.
  - **Chế độ Dữ liệu thô (Raw)**: Hiển thị đầy đủ JSON (RDAP) hoặc Text (Whois) với font chữ Monospace và nút sao chép nhanh vào bộ nhớ tạm.

### 9. Lịch sử kiểm tra (Room Database)
- Lưu trữ mọi kết quả quét mạng, Ping, Traceroute, Quét cổng, DNS, Whois/RDAP.
- Hỗ trợ lọc theo từng công cụ, tìm kiếm nhanh theo từ khóa mục tiêu, sao chép kết quả vào bộ nhớ tạm, và xóa lịch sử.

---

## 🔒 Bảng rà soát Quyền riêng tư & Các điểm cuối bên ngoài (Privacy Audit)

Ứng dụng tuân thủ nghiêm ngặt chính sách bảo mật: Toàn bộ kết nối gọi ra bên ngoài đều được mã hóa HTTPS (ngoại trừ Whois cổng 43 chuẩn TCP và DNS UDP/TCP theo thiết kế giao thức). Các truy vấn cục bộ nội bộ LAN (mDNS, NetBIOS, SNMP, ICMP) **hoàn toàn không gửi bất kỳ dữ liệu nào ra Internet**.

| Điểm cuối (Endpoint) | Giao thức | Mục đích | Kích hoạt khi nào | Gửi IP người dùng? |
| :--- | :--- | :--- | :--- | :--- |
| `https://api4.ipify.org` | HTTPS | Lấy địa chỉ Public IPv4 của thiết bị | Tự động khi mở tab Thông tin mạng hoặc nhấn Làm mới | Chỉ IP nguồn kết nối TCP tự nhiên, không kèm dữ liệu |
| `https://api6.ipify.org` | HTTPS | Lấy địa chỉ Public IPv6 của thiết bị | Tự động khi mở tab Thông tin mạng hoặc nhấn Làm mới | Chỉ IP nguồn kết nối TCP tự nhiên, không kèm dữ liệu |
| `https://ipv4.icanhazip.com` | HTTPS | Dự phòng lấy Public IPv4 khi ipify lỗi | Tự động khi ipify không phản hồi | Chỉ IP nguồn kết nối TCP tự nhiên |
| `https://ipwho.is/{ip}` | HTTPS | 1. Tra cứu ISP và Quốc gia cho Public IP<br>2. Tra cứu quốc gia từng hop Traceroute | 1. Khi mở tab Thông tin mạng<br>2. **Chỉ khi người dùng BẬT công tắc "Hiện quốc gia từng hop"** | Có gửi địa chỉ IP hop cần tra cứu (tuyệt đối không gửi IP Private/LAN) |
| `https://data.iana.org/rdap/*` | HTTPS | Tải bảng Bootstrap RDAP tĩnh (dns, ipv4, ipv6, asn) | Khi người dùng nhấn tra cứu Whois/RDAP (có cache 24h) | Không (chỉ tải tệp tĩnh, không truyền dữ liệu người dùng) |
| `https://rdap.org/*` | HTTPS | Cổng dự phòng truy vấn RDAP khi IANA không có URL | Khi người dùng nhấn tra cứu Whois/RDAP | Gửi domain/IP/ASN mà người dùng chủ động tìm kiếm |
| `whois.iana.org:43` & referral servers | TCP 43 | Fallback truy vấn Whois truyền thống | Khi người dùng tra cứu Whois và RDAP thất bại | Gửi chuỗi query domain/IP mà người dùng nhập |
| Các máy chủ DNS (1.1.1.1, 8.8.8.8, 9.9.9.9...) | UDP / TCP 53 | Phân giải bản ghi DNS theo yêu cầu | Chỉ khi người dùng bấm nút "Tra cứu DNS" | Gửi tên miền cần tra cứu tới resolver được chọn |

> [!NOTE]
> **Quy định bảo mật mạng (Network Security Config)**:
> Tệp `app/src/main/res/xml/network_security_config.xml` áp đặt cấm toàn bộ lưu lượng văn bản thuần (`cleartextTrafficPermitted="false"`), bảo vệ ứng dụng khỏi nguy cơ vô tình gửi dữ liệu qua HTTP không mã hóa.

---

## 📢 Hướng dẫn tích hợp Google AdMob (Thay thế MockAdService)

Hiện tại, ứng dụng sử dụng `MockAdServiceImpl` để mô phỏng hiển thị banner và interstitial mà không phụ thuộc vào AdMob ID thật.

> [!IMPORTANT]
> **Yêu cầu tuân thủ khi gắn AdMob thật**:
> 1. **Màn hình đồng ý người dùng (User Messaging Platform - UMP SDK)**: Bắt buộc tích hợp Google UMP SDK (`com.google.android.ump:user-messaging-platform`) để thu thập sự đồng ý (Consent) của người dùng tại các khu vực châu Âu (GDPR) và Vương quốc Anh trước khi nạp quảng cáo.
> 2. **Khai báo An toàn dữ liệu (Google Play Data Safety)**: Khi gắn AdMob SDK thật, nhà phát triển bắt buộc phải khai báo trong Google Play Console về việc thu thập Advertising ID (AAID), Device ID và tương tác ứng dụng cho mục đích quảng cáo/phân tích.

Để kết nối Google AdMob thật:

### Bước 1: Thêm dependency vào `app/build.gradle.kts`
```kotlin
dependencies {
    // Google Mobile Ads SDK
    implementation("com.google.android.gms:play-services-ads:23.4.0")
}
```

### Bước 2: Khai báo Application ID trong `app/src/main/AndroidManifest.xml`
Thêm thẻ `<meta-data>` bên trong thẻ `<application>`:
```xml
<application ...>
    <!-- AdMob App ID (Mã demo kiểm thử của Google) -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-3940256099942544~3347511713" />
</application>
```

### Bước 3: Thay thế `MockAdServiceImpl` bằng `AdMobAdServiceImpl`
Tạo file `app/src/main/java/com/networkanalyst/app/data/service/AdMobAdServiceImpl.kt`:
```kotlin
package com.networkanalyst.app.data.service

import android.app.Activity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdMobAdServiceImpl @Inject constructor() : AdService {
    private var interstitialAd: InterstitialAd? = null
    private val adUnitId = "ca-app-pub-3940256099942544/1033173712" // Test Interstitial ID

    private val _isBannerEnabled = MutableStateFlow(true)
    override val isBannerEnabled: StateFlow<Boolean> = _isBannerEnabled.asStateFlow()

    private val _interstitialSimulatedCount = MutableStateFlow(0)
    override val interstitialSimulatedCount: StateFlow<Int> = _interstitialSimulatedCount.asStateFlow()

    private var actionCount = 0

    override fun showInterstitial(activity: Activity?, onDismissed: () -> Unit) {
        if (activity != null && interstitialAd != null) {
            interstitialAd?.show(activity)
            interstitialAd = null
            loadAd(activity)
            onDismissed()
        } else {
            onDismissed()
        }
    }

    override fun recordToolAction(activity: Activity?, onDismissed: (() -> Unit)?) {
        actionCount++
        if (actionCount >= 3) {
            actionCount = 0
            showInterstitial(activity) { onDismissed?.invoke() }
        } else {
            onDismissed?.invoke()
        }
    }

    private fun loadAd(activity: Activity) {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(activity, adUnitId, request, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialAd = null
            }
        })
    }
}
```

### Bước 4: Cập nhật ràng buộc trong `app/src/main/java/com/networkanalyst/app/di/AppModule.kt`
Đổi binding từ `MockAdServiceImpl` sang `AdMobAdServiceImpl`:
```kotlin
@Binds
@Singleton
abstract fun bindAdService(impl: AdMobAdServiceImpl): AdService
```

---

## 🚀 Hướng dẫn Build & Kiểm tra

### 1. Build Debug APK
Chạy lệnh sau tại thư mục gốc của project:
```bash
./gradlew assembleDebug
```
File APK hoàn chỉnh sẽ được tạo tại:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Kiểm tra Lint
```bash
./gradlew lintDebug
```
Kết quả kiểm tra lint: **Hoàn toàn sạch sẽ, không có bất kỳ lỗi nào**.

---

## 📂 Cấu trúc thư mục mã nguồn
```
scripts/
└── generate_oui_data.py                 # Script trích xuất OUI MA-L/M/S từ oui.csv của IEEE
app/src/main/
├── AndroidManifest.xml
├── java/com/networkanalyst/app/
│   ├── NetworkAnalystApp.kt             # Application class (@HiltAndroidApp)
│   ├── MainActivity.kt                  # Activity chính & Compose Bottom Navigation
│   ├── data/
│   │   ├── local/
│   │   │   ├── AppDatabase.kt           # Room Database
│   │   │   ├── HistoryDao.kt            # DAO cho bảng scan_history
│   │   │   └── entity/HistoryEntity.kt
│   │   ├── model/NetworkModels.kt       # Các model dữ liệu domain
│   │   └── service/
│   │       ├── AdService.kt             # Interface quảng cáo & MockAdServiceImpl
│   │       ├── DiagnosticsHelper.kt
│   │       ├── GeoIpService.kt          # Dịch vụ tra cứu GeoIP cho hop traceroute / public IP
│   │       ├── HostnameResolutionHelper.kt # Thứ tự ưu tiên phân giải hostname: mDNS > NetBIOS > LLMNR > PTR
│   │       ├── NetworkClassifierHelper.kt  # Phân loại chuẩn Wi-Fi (WPA/WPA2/WPA3) & thế hệ di động (4G/5G)
│   │       ├── NetworkDiagnosticsService.kt  # ConnectivityManager, Wi-Fi, IP, Gateway, DNS, VPN, Ethernet
│   │       ├── OuiLookupHelper.kt       # Tra cứu OUI chuẩn hóa, MA-L/M/S, phát hiện MAC ngẫu nhiên
│   │       ├── LanScannerService.kt     # Quét LAN: TCP probe + mDNS + NetBIOS UDP + SNMP + OUI lookup
│   │       ├── PingService.kt           # Ping: InetAddress.isReachable + ProcessBuilder + PingOutputParser
│   │       ├── TracerouteService.kt     # Traceroute tăng dần TTL + TracerouteOutputParser
│   │       ├── PortScannerService.kt    # Quét cổng với Semaphore + Banner Grabbing
│   │       ├── DnsService.kt            # DNS lookup dùng dnsjava + resolver tùy chọn
│   │       └── WhoisRdapService.kt      # Tra cứu RDAP (IANA Bootstrap) & Whois TCP 43 fallback
│   ├── di/
│   │   └── AppModule.kt                 # Cấu hình Dagger Hilt
│   └── ui/
│       ├── components/                  # Rationale Dialog, Banner Ad placeholder
│       ├── navigation/                  # Bottom Navigation routes
│       ├── screens/
│       │   ├── networkinfo/             # Màn hình thông tin mạng
│       │   ├── lanscan/                 # Màn hình quét LAN
│       │   ├── ping/                    # Màn hình Ping ICMP
│       │   ├── traceroute/              # Màn hình Traceroute
│       │   ├── portscan/                # Màn hình Quét cổng
│       │   ├── dns/                     # Màn hình Tra cứu DNS
│       │   ├── whois/                   # Màn hình Tra cứu Whois/RDAP
│       │   └── history/                 # Màn hình Lịch sử Room
│       └── theme/                       # Material 3 Theme, Typography, Colors
└── res/
    ├── raw/
    │   └── oui_database.txt             # Cơ sở dữ liệu OUI (Dữ liệu mẫu 207 hãng)
    ├── values/
    │   └── strings.xml                  # Ngôn ngữ mặc định (English)
    ├── values-vi/
    │   └── strings.xml                  # Bản địa hóa Tiếng Việt (100% khớp khóa)
    └── xml/
```

---

## 📋 Danh sách các việc cần thực hiện thủ công trước khi phát hành (Manual Checklist)

1. **Cập nhật Cơ sở dữ liệu OUI IEEE đầy đủ**:
   - Tải tệp `oui.csv` chính thức mới nhất từ IEEE Standards Association (hơn 35.000+ hãng).
   - Đặt tệp vào thư mục `scripts/data/oui.csv`.
   - Chạy lệnh: `python scripts/generate_oui_data.py` để sinh ra `app/src/main/res/raw/oui_database.txt` hoàn chỉnh (thay thế tệp dữ liệu mẫu 207 hãng).
2. **Tùy biến Nhận diện Ứng dụng (Branding & Assets)**:
   - Thay đổi tên ứng dụng trong `app/src/main/res/values/strings.xml` (`app_name`) nếu cần.
   - Thay thế biểu tượng launcher (`ic_launcher`, `ic_launcher_round`, adaptive icon vector) trong thư mục `app/src/main/res/mipmap-*`.
3. **Ký số bản dựng Release (Signing Config)**:
   - Tạo Java Keystore (`.jks` / `.keystore`) bằng `keytool` hoặc Android Studio.
   - Cấu hình biến môi trường hoặc `signingConfigs.release` trong `app/build.gradle.kts` (không lưu mật khẩu keystore trực tiếp vào git).
   - Chạy lệnh `./gradlew assembleRelease` hoặc `./gradlew bundleRelease` (AAB) để ký release.
4. **Khai báo trên Google Play Console**:
   - **Khai báo An toàn dữ liệu (Data Safety Form)**: Khai báo không thu thập hoặc chia sẻ dữ liệu vị trí vật lý; giải thích các kết nối HTTP tới các điểm cuối tra cứu địa chỉ IP công cộng (ipify, icanhazip, ipwho.is) và WHOIS/RDAP.
   - **Khai báo Quyền nhạy cảm (Permissions Declaration)**: Khai báo lý do sử dụng quyền `READ_PHONE_STATE` (chỉ dùng để hiển thị thế hệ mạng 4G/5G và nhà mạng SIM khi người dùng chủ động xem trong mục SIM Info; ứng dụng vẫn hoạt động đầy đủ khi người dùng từ chối cấp quyền).
