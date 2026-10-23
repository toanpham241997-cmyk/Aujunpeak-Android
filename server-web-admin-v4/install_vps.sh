#!/usr/bin/env bash
set -euo pipefail
if [ "${EUID:-$(id -u)}" -ne 0 ]; then echo "Hãy chạy: sudo bash install_vps.sh"; exit 1; fi
ROOT="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR=/var/www/html/aujunpeak-data
APP_DIR=/opt/aujunpeak-admin
STATE_DIR=/var/lib/aujunpeak-admin
ENV_FILE=/etc/aujunpeak-admin.env

mkdir -p "$DATA_DIR" "$STATE_DIR/backups"
if [ ! -f "$DATA_DIR/ui.json" ]; then
  cp -a "$ROOT/aujunpeak-data/." "$DATA_DIR/"
  echo "Đã cài data mẫu."
else
  echo "Giữ nguyên ui.json/manifest.json hiện tại trên VPS."
fi

rm -rf "$APP_DIR"
mkdir -p "$APP_DIR"
cp -a "$ROOT/admin/." "$APP_DIR/"
chown -R www-data:www-data "$DATA_DIR" "$STATE_DIR"
find "$DATA_DIR" -type d -exec chmod 755 {} \;
find "$DATA_DIR" -type f -exec chmod 644 {} \;

apt-get update -y
apt-get install -y python3 python3-venv python3-pip nginx openssl
python3 -m venv "$APP_DIR/venv"
"$APP_DIR/venv/bin/pip" install --upgrade pip
"$APP_DIR/venv/bin/pip" install -r "$APP_DIR/requirements.txt"

ADMIN_USER="${ADMIN_USER:-admin}"
if [ -z "${ADMIN_PASSWORD:-}" ]; then
  ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9@#_' | head -c 18)"
fi
PASSWORD_HASH="$(P="$ADMIN_PASSWORD" PYTHONPATH="$APP_DIR" "$APP_DIR/venv/bin/python" -c "from security import hash_password;import os;print(hash_password(os.environ['P']))")"
SECRET="$(openssl rand -hex 32)"
cat > "$ENV_FILE" <<EOF
AUJUNPEAK_DATA_DIR=$DATA_DIR
AUJUNPEAK_STATE_DIR=$STATE_DIR
AUJUNPEAK_ADMIN_USER=$ADMIN_USER
AUJUNPEAK_ADMIN_PASSWORD_HASH=$PASSWORD_HASH
AUJUNPEAK_SECRET_KEY=$SECRET
EOF
chmod 640 "$ENV_FILE"
chown root:www-data "$ENV_FILE"

cp "$APP_DIR/systemd/aujunpeak-admin.service" /etc/systemd/system/aujunpeak-admin.service
mkdir -p /etc/nginx/snippets
cp "$APP_DIR/nginx/aujunpeak-admin-location.conf" /etc/nginx/snippets/aujunpeak-admin.conf

python3 - <<'NGINX_PY'
from pathlib import Path
import glob, shutil, time
include = "    include /etc/nginx/snippets/aujunpeak-admin.conf;\n"
updated = False
for raw in glob.glob('/etc/nginx/sites-enabled/*'):
    p = Path(raw).resolve()
    try:
        txt = p.read_text()
    except Exception:
        continue
    if 'root /var/www/html' not in txt:
        continue
    if 'aujunpeak-admin.conf' in txt:
        print('Nginx admin location đã tồn tại trong', p)
        updated = True
        break
    rootpos = txt.find('root /var/www/html')
    start = txt.rfind('server', 0, rootpos)
    brace = txt.find('{', start)
    depth = 0
    end = None
    for i in range(brace, len(txt)):
        if txt[i] == '{': depth += 1
        elif txt[i] == '}':
            depth -= 1
            if depth == 0:
                end = i
                break
    if end is None:
        continue
    backup = p.with_name(p.name + '.aujunpeak.bak.' + time.strftime('%Y%m%d%H%M%S'))
    shutil.copy2(p, backup)
    p.write_text(txt[:end] + include + txt[end:])
    print('Đã thêm Web Admin vào Nginx:', p, 'backup:', backup)
    updated = True
    break
if not updated:
    print('WARN: Không tự tìm được server block root /var/www/html.')
    print('Hãy thêm dòng này bên trong server {} rồi nginx -t:')
    print('include /etc/nginx/snippets/aujunpeak-admin.conf;')
NGINX_PY

systemctl daemon-reload
systemctl enable --now aujunpeak-admin
nginx -t
systemctl reload nginx
IP="$(hostname -I | awk '{print $1}')"
echo
echo "================ AUJUNPEAK ADMIN V3 ================"
echo "URL: http://$IP/aujunpeak-admin/"
echo "User: $ADMIN_USER"
echo "Password: $ADMIN_PASSWORD"
echo "HÃY LƯU MẬT KHẨU NÀY"
echo "====================================================="
