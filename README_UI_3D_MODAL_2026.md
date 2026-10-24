# Aujunpeak Android - 3D UI + Modal Refresh

## Mục tiêu bản này
- Đổi mạnh phong cách giao diện sang kiểu 3D / depth / cyber-dark.
- Thay toàn bộ toast/snackbar chính sang popup modal đẹp mắt kiểu Swal.
- Giữ nguyên package, API, Firebase, logic key, dữ liệu và build GitHub Actions.

## Những gì đã thay đổi
- Bộ màu chủ đạo đổi từ đỏ cũ sang xanh-tím cyber hiện đại hơn.
- Cập nhật lại các drawable dùng chung:
  - bg_neo_card
  - bg_neo_button_primary
  - bg_neo_button_secondary
  - bg_neo_screen
  - bg_neo_header
  - bg_neo_bottom_nav
  - bg_neo_icon_plate
  - bg_neo_modal
  - bg_neo_card_gold
- Thêm modal dùng chung:
  - app/src/main/java/com/aujunpeak/app/utils/UiModal.kt
  - app/src/main/res/layout/dialog_swal_modal.xml
  - nhóm drawable bg_swal_*
- Toast/Snackbar được thay bằng modal ở các luồng chính:
  - MainActivity
  - FunctionFragment
  - InfoFragment
  - FeedbackActivity
  - OptimizerSuperActivity
  - CrosshairActivity
  - ForceUpdateActivity
  - ChatDialog
  - DynamicActionEngine

## Lưu ý
- Chưa thể build trực tiếp trong môi trường hiện tại do Gradle wrapper cần tải từ services.gradle.org nhưng môi trường đóng không có Internet.
- Cấu trúc source và GitHub Actions vẫn được giữ nguyên để bạn push lên GitHub và build APK như các bản trước.

## Cách cập nhật
1. Giải nén file ZIP này.
2. Sao chép toàn bộ file/thư mục vào repository GitHub local của bạn.
3. Commit và Push.
4. Vào Actions để build APK.
