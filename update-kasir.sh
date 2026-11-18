#!/data/data/com.termux/files/usr/bin/bash
# Tarik APK Salasatu Kasir terbaru dari GitHub Releases lalu pasang di Android (jalankan di Termux).
# Pemakaian:  bash update-kasir.sh USERNAME/salasatu-kasir
# Repo privat: export GITHUB_TOKEN=ghp_xxx dulu (token dengan izin baca Contents).
set -e
REPO="${1:-GANTI-USERNAME/salasatu-kasir}"
case "$REPO" in GANTI-*) echo "Isi nama repo: bash update-kasir.sh USERNAME/salasatu-kasir"; exit 1;; esac

command -v curl >/dev/null && command -v jq >/dev/null || pkg install -y curl jq termux-tools
[ -d "$HOME/storage/downloads" ] || { termux-setup-storage; sleep 5; }
OUT="$HOME/storage/downloads/salasatu-kasir.apk"

AUTH=(); [ -n "$GITHUB_TOKEN" ] && AUTH=(-H "Authorization: Bearer $GITHUB_TOKEN")
echo "Mencari rilis terbaru di $REPO ..."
JSON=$(curl -fsSL "${AUTH[@]}" "https://api.github.com/repos/$REPO/releases/latest") || { echo "Gagal. Cek nama repo, internet, atau token (repo privat)."; exit 1; }
TAG=$(echo "$JSON" | jq -r '.tag_name')
URL=$(echo "$JSON" | jq -r '[.assets[] | select(.name|endswith(".apk"))][0].url')
[ "$URL" = "null" ] && { echo "Rilis $TAG tidak berisi APK."; exit 1; }

echo "Mengunduh $TAG ..."
curl -fL "${AUTH[@]}" -H "Accept: application/octet-stream" "$URL" -o "$OUT"
echo "Tersimpan: $OUT"
echo "Membuka pemasang. Ketuk Pasang/Update, lalu izinkan 'sumber tidak dikenal' bila diminta."
termux-open "$OUT" || echo "Buka manual: aplikasi File > Download > salasatu-kasir.apk"
