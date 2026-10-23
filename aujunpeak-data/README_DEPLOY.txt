AUJUNPEAK SERVER DATA V2

VPS hiện tại:
  /var/www/html/aujunpeak-data
Endpoint:
  http://103.140.249.74/aujunpeak-data/manifest.json

Mỗi lần sửa ui.json / assets:
  cd /var/www/html/aujunpeak-data
  sudo -u www-data python3 build_manifest.py --bump

KHÔNG cần sửa hash hoặc version bằng tay khi dùng --bump.

Ví dụ:
  examples/HOME_ADD_SWITCH.json
  examples/FULL_SCREEN_REPLACE.json
  examples/ACTION_EXAMPLES.json
