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

### 2. Dò tìm thiết bị LAN không dựa vào `/proc/net/arp`
- Trên Android 10+ (API 29+), hệ điều hành đã chặn quyền truy cập vào `/proc/net/arp`.
- **Giải pháp thay thế tích hợp trong ứng dụng**:
  1. **Thăm dò song song (Parallel TCP Probing)**: Quét toàn bộ dải IP mạng con (Subnet /24) qua Coroutine kết hợp `Semaphore(40)` trên các cổng LAN thông dụng (80, 443, 22, 445, 8080, 53, 8008, 9100).
  2. **NsdManager (mDNS / Bonjour)**: Khám phá dịch vụ mạng cục bộ (`_http._tcp.`, `_workstation._tcp.`, `_googlecast._tcp.`, `_ipp._tcp.`, `_airplay._tcp.`) và phân giải IP / tên thiết bị.
  3. **UDP NetBIOS Name Service (Port 137)**: Gửi gói tin RFC 1002 NBSTAT wildcard query tới từng IP. Khi thiết bị phản hồi, ứng dụng trích xuất được **Computer Name** (Windows / Samba / NAS) và **địa chỉ MAC card mạng thật** của thiết bị.
  4. **UDP LLMNR (Port 5355)**: Gửi truy vấn Link-Local Multicast Name Resolution phân giải hostname nội bộ.
  5. **UDP SNMP (Port 161)**: Thăm dò SNMP v1/v2c OID `1.3.6.1.2.1.1.1.0` (sysDescr) với community `public` để nhận dạng các thiết bị mạng chuyên dụng (Cisco, MikroTik, Synology NAS, máy in HP, Canon, Epson).
  6. **Nhận dạng nhà sản xuất (Vendor Heuristic)**: Phân tích OUI prefix từ MAC và đối chiếu mẫu hostname để gán nhãn thiết bị (Apple, Samsung, Google Cast, Raspberry Pi, TP-Link, Windows PC, Linux Server).

### 3. Công cụ Ping độ tin cậy cao
- Hỗ trợ cả 2 cơ chế:
  - **Lệnh ping hệ thống qua `ProcessBuilder` (Ưu tiên)**: Gọi trực tiếp `/system/bin/ping` với các tham số `-c <count> -W <timeoutSec> <target>`.
    > [!NOTE]
    > **Thực tế về lệnh ping trên Android**: Các ứng dụng Android thông thường hoàn toàn có thể thực thi `/system/bin/ping` mà không cần quyền root. Thách thức kỹ thuật thực sự nằm ở việc các nhà sản xuất thiết bị (OEM như Samsung, Xiaomi, Google Pixel) sử dụng các bản dựng binary khác nhau (toybox, iputils, bản tùy biến riêng của hãng) dẫn đến sự khác biệt về cờ tham số (ví dụ `-t` vs `-m`) và định dạng chuỗi đầu ra.
    > Ứng dụng đã giải quyết vấn đề này bằng bộ phân tích `PingOutputParser` đa biến thể trích xuất linh hoạt dữ liệu từng gói tin (`bytes`, `seq`, `ttl`, `time ms`, `time<1 ms`) và bảng thống kê truyền/nhận, tỷ lệ mất gói (`% packet loss`), độ trễ `min/avg/max/mdev`. Hỗ trợ bọc `withTimeoutOrNull` và `destroyForcibly()` chống treo process.
  - **`InetAddress.isReachable(timeout)` (Dự phòng cuối cùng)**:
    > [!IMPORTANT]
    > **Lưu ý kỹ thuật về `InetAddress.isReachable` trên Android**:
    > Trên thiết bị Android không root, ứng dụng thông thường không có quyền mở raw ICMP socket (`CAP_NET_RAW`). Vì vậy, `isReachable()` sẽ cố gắng gửi ICMP echo request, nhưng khi bị từ chối quyền sẽ tự động chuyển sang thử kết nối TCP port 7 (Echo). Do hầu hết máy chủ hiện đại chặn cổng TCP 7, phương thức này có thể trả về `false` ngay cả khi máy chủ đang online hoặc RTT không phản ánh đúng ICMP RTT thực tế. Ứng dụng chỉ dùng cơ chế này làm giải pháp dự phòng khi binary ping hệ thống không khả dụng.

### 4. Công cụ Traceroute với TTL tăng dần & Tùy chọn GeoIP
- Sử dụng tiện ích ping hệ điều hành với cờ `-t <ttl>`, tăng dần TTL từ `1` đến `30`.
- Bắt gói tin ICMP `Time to live exceeded` qua `TracerouteOutputParser` để xác định địa chỉ IP và độ trễ tại từng router trung gian.
- **Tương thích nhà sản xuất (OEM)**: Tự động phát hiện nếu binary ping trên thiết bị (Samsung, Xiaomi, Pixel, toybox) không hỗ trợ cờ `-t` và báo lỗi rõ ràng thay vì hiện toàn bộ hop timeout.
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

## 📋 Lộ trình các tính năng tiếp theo (Mốc 3)
Các tính năng sau đã được quy hoạch hoàn thiện trong Mốc 3:
1. **Hỗ trợ IPv6 toàn diện**: Hiển thị địa chỉ IPv6 trong Quét LAN (qua mDNS/NsdManager); hỗ trợ đích đến IPv6 cho Ping và Traceroute; hiển thị cả IPv4 và IPv6 cho Internet công cộng.
2. **Reverse DNS (PTR) cho LAN Scanner**: Bổ sung cơ chế tra cứu PTR record phân giải hostname cho các thiết bị LAN sau mDNS và NetBIOS.
3. **Chi tiết kết nối mạng nâng cao**:
   - Nhận diện chuẩn bảo mật Wi-Fi: WPA, WPA2, WPA3, Open (WPA3-SAE, WPA2-PSK).
   - Loại mạng di động thế hệ mới (4G LTE / 5G SA / 5G NSA) và thông tin cấu hình SIM qua `SubscriptionManager` (kèm xin quyền `READ_PHONE_STATE` với màn hình giải thích minh bạch).
   - Nhận diện kết nối mạng qua cổng chuyển đổi USB Ethernet (`NetworkCapabilities.TRANSPORT_ETHERNET`).
4. **DNS Đa truy vấn**: Tùy chọn truy vấn đồng thời tất cả các loại bản ghi DNS (`ANY` / batch query).
5. **Rà soát quyền riêng tư (Privacy Audit)**: Liệt kê chi tiết mọi endpoint bên ngoài (ipify, ipwho.is, IANA, rdap.org, DNS Resolvers), đảm bảo 100% sử dụng HTTPS và rà soát triệt để mọi chuỗi ký tự trên UI qua `strings.xml`.

---

## 📢 Hướng dẫn tích hợp Google AdMob (Thay thế MockAdService)

Hiện tại, ứng dụng sử dụng `MockAdServiceImpl` để mô phỏng hiển thị banner và interstitial mà không phụ thuộc vào Internet ID của Google.

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
│   │       ├── NetworkDiagnosticsService.kt  # ConnectivityManager, Wi-Fi, IP, Gateway, DNS, VPN
│   │       ├── LanScannerService.kt     # Quét LAN: TCP probe + mDNS + NetBIOS UDP + SNMP
│   │       ├── PingService.kt           # Ping: InetAddress.isReachable + ProcessBuilder
│   │       ├── TracerouteService.kt     # Traceroute tăng dần TTL
│   │       ├── PortScannerService.kt    # Quét cổng với Semaphore + Banner Grabbing
│   │       └── DnsService.kt            # DNS lookup dùng dnsjava + resolver tùy chọn
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
│       │   └── history/                 # Màn hình Lịch sử Room
│       └── theme/                       # Material 3 Theme, Typography, Colors
└── res/
    ├── drawable/
    ├── mipmap-anydpi-v26/
    ├── values/
    └── xml/
```
