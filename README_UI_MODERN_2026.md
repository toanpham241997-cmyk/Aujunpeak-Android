# Aujunpeak Android — Modern UI Refresh

## Bản cải tiến giao diện, build trực tiếp với GitHub Actions

Source này **giữ nguyên project Android gốc**, package `com.aujunpeak.app`, Gradle, Firebase, cấu hình API, luồng đăng nhập/key, phân quyền VIP, chat, thông báo, quản trị và toàn bộ các tài nguyên đi kèm.

### Điểm mới

- **39 layout Android**: áp dụng typography dễ đọc hơn, khoảng trắng thống nhất, thẻ bo mềm, căn chỉnh lại màn hình và các thành phần nhấn/chạm.
- **5 tab chính (Home, Game, Function, Settings, Profile)**: header cao 64dp, bottom navigation 74dp, icon 23dp, nhãn 10sp, trạng thái tab đang chọn rõ ràng.
- **Hệ màu thiết kế mới**: nền graphite/navy cực tối, thẻ slate, viền mờ, điểm nhấn ruby-coral cùng màu semantic xanh lá/vàng/tím nhẹ nhàng.
- **Nút bấm, input, chip, card, modal**: nền mới thống nhất, bo góc 14–24dp, selected/pressed states, hiệu ứng ripple có sẵn trên Android.
- **Animation chuyển trang**: fade/slide 160–260ms, giảm cảm giác giật hoặc kéo dài.
- **Hiệu năng**: tắt mưa/ảnh GIF nền toàn màn hình liên tục, chỉ giữ chuyển động nhẹ khi chuyển tab; tiêu đề giữ ổn định thay vì chạy/xóa liên tục.
- **Các màn hình phụ**: login, crosshair, booster, optimizer, about, feedback, policy, update và các dialog dùng chung phong cách giao diện mới.

### Không sửa phần nghiệp vụ

- Không đổi package name, tên class/hàm API, URL API hay Firebase config.
- Không đổi key/licensing, chữ ký APK, dữ liệu người dùng, cơ chế VIP, intent launch, service hay luồng chat.
- **Không** thay đổi `app/build.gradle.kts`, Gradle wrapper hoặc `.github/workflows/android-build.yml`.
- Giữ toàn bộ file trong `aujunpeak-data/` và `server-web-admin-v4/`.

### Build bằng GitHub, không cần Android Studio

1. Trên máy tính, mở repository `Aujunpeak-Android` trong **GitHub Desktop** và nhấn **Show in Explorer**.
2. Giải nén ZIP này và sao chép **toàn bộ nội dung** vào thư mục repository (không đặt cả ZIP vào bên trong). Giữ nguyên thư mục `.git` của repository.
3. Quay lại GitHub Desktop → điền Summary `Modern UI refresh` → **Commit to main** → **Push origin**.
4. Mở `https://github.com/toanpham241997-cmyk/Aujunpeak-Android/actions`.
5. Chọn workflow **Aujunpeak Android - Build APK** và chạy **debug**, hoặc đợi workflow kích hoạt tự động sau Push.
6. Khi build thành công, tải APK tại mục **Artifacts**. Bản Release cần keystore gốc và GitHub Secrets theo `README_GITHUB_BUILD.md`.

### Kiểm thử nên thực hiện khi có APK

- Đăng nhập key FREE/VIP, quyền hạn truy cập tab, các thông báo, logout.
- Mở ứng dụng/game, chức năng tối ưu, các cài đặt nhạy cảm và modal xác nhận.
- Kiểm tra bố cục trên màn hình nhỏ, kích thước font hệ thống lớn, xoay màn hình (nếu được hỗ trợ).
- Kiểm tra Chat API, hình ảnh, Firebase notification, kiểm tra key hết hạn và lịch sử đăng nhập.

### Trạng thái xác minh

Đã kiểm tra cú pháp tất cả XML resource (449 file), so sánh toàn bộ ID trong layout với bản gốc (không mất ID), và xác minh các file xử lý API/Firebase/key/Gradle không bị sửa. Chưa thực hiện build Android trên môi trường này vì không có Android SDK/Gradle cache; build thật cần chạy GitHub Actions để xác nhận.
