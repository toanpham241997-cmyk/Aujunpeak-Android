#!/usr/bin/env bash
set -euo pipefail
if [ "${EUID:-$(id -u)}" -ne 0 ]; then echo "sudo NEW_PASSWORD='matkhau' bash reset_admin_password.sh"; exit 1; fi
APP_DIR=/opt/aujunpeak-admin
ENV_FILE=/etc/aujunpeak-admin.env
if [ -z "${NEW_PASSWORD:-}" ]; then read -rsp 'Mật khẩu mới: ' NEW_PASSWORD; echo; fi
HASH="$(P="$NEW_PASSWORD" PYTHONPATH="$APP_DIR" "$APP_DIR/venv/bin/python" -c "from security import hash_password;import os;print(hash_password(os.environ['P']))")"
sed -i "s|^AUJUNPEAK_ADMIN_PASSWORD_HASH=.*|AUJUNPEAK_ADMIN_PASSWORD_HASH=$HASH|" "$ENV_FILE"
systemctl restart aujunpeak-admin
echo "Đã đổi mật khẩu admin."
