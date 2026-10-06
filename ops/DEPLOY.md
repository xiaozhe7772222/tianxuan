# ops · 部署速查

本目录是**基础设施的源码**。线上都跑在腾讯云 `124.222.37.253` 上，
这里存的是可复现的源文件，完整说明见 `README.md`。

## 文件与线上路径的对应

| 本目录文件 | 线上路径 |
|---|---|
| `tianxuan_dist.py` | `/usr/local/share/tianxuan/tianxuan_dist.py` |
| `apkmanifest.py` | `/usr/local/share/tianxuan/apkmanifest.py` |
| `apkmanifest_test.py` | `/usr/local/share/tianxuan/apkmanifest_test.py` |
| `gen-manifest.sh` | `/usr/local/bin/gen-tianxuan-manifest.sh` |
| `sync-tianxuan-plugins.sh` | `/usr/local/bin/sync-tianxuan-plugins.sh` |
| `tianxuan-fetch.sh` | `/usr/local/bin/tianxuan-fetch.sh` |
| `tianxuan-dist.service` | `/etc/systemd/system/tianxuan-dist.service` |
| `tianxuan-fetch.service` | `/etc/systemd/system/tianxuan-fetch.service` |

## 一键部署

```bash
SSHK=~/.ssh/tianxuan   # 或 ssh -i /root/tuzhe_key
D=124.222.37.253

for f in tianxuan_dist.py apkmanifest.py apkmanifest_test.py gen_manifest_test.py; do
  scp -i "$SSHK" "$f" root@$D:/usr/local/share/tianxuan/
done
for f in gen-manifest.sh sync-tianxuan-plugins.sh tianxuan-fetch.sh; do
  scp -i "$SSHK" "$f" root@$D:/usr/local/bin/
done
for f in tianxuan-dist.service tianxuan-fetch.service; do
  scp -i "$SSHK" "$f" root@$D:/etc/systemd/system/
done

ssh -i "$SSHK" root@$D '
  mv -f /usr/local/bin/gen-manifest.sh /usr/local/bin/gen-tianxuan-manifest.sh
  chmod +x /usr/local/bin/{gen-tianxuan-manifest,sync-tianxuan-plugins,tianxuan-fetch}.sh
  cd /usr/local/share/tianxuan
  python3 apkmanifest_test.py     # 先自测：清单解析器
  python3 gen_manifest_test.py    # 再自测：latest 选取与排序
  systemctl daemon-reload
  /usr/local/bin/sync-tianxuan-plugins                     # 补软链
  /usr/local/bin/gen-tianxuan-manifest                     # 刷新清单
  systemctl restart tianxuan-dist
'
```

`gen_manifest_test.py` 必须在部署机上与 `gen-manifest.sh` 同目录：
它用相对路径找脚本，并从同目录取 `apkmanifest.py`。

## 首次部署（空机器）

```bash
mkdir -p /opt/tianxuan-offline /opt/tianxuan-dist /usr/local/share/tianxuan

# 直链清单不入库（含 QQ 闪传的 rkey 会话凭证，14 天过期）。
# 按README.md「取链过程」重新取链后，在服务器上生成 TSV：
#   列必须是：字节数 <TAB> 文件名 <TAB> 直链
python3 -c "import json;[print(f\"{p['size']}\t{p['name']}\t{p['url']}\") \
  for p in json.load(open('/root/offline-manifest.json'))]" \
  > /tmp/tianxuan-manifest.tsv

/usr/local/bin/tianxuan-fetch.sh                # 断点续传，可反复重跑
systemctl enable --now tianxuan-fetch.service

# nginx（唯一对既有配置的改动：只新增一个 location）
# 见 README.md「nginx 改动」小节，改前先 nginx -t，改错会 502
systemctl enable --now tianxuan-dist.service
```

## 上线前必查

```bash
# 1. 既有服务不能受影响
for s in fusion-gateway logview st-rotator st-auth nginx; do
  systemctl is-active "$s" || echo "!!! $s 异常"
done
curl -s -o /dev/null -w 'v1/models=%{http_code}\n' http://127.0.0.1/v1/models  # 应为 401

# 2. 解析器自测
python3 /usr/local/share/tianxuan/apkmanifest_test.py     # 应 7/7

# 3. 公网四端点
curl -s https://124.222.37.253/tx-update/healthz
curl -s https://124.222.37.253/tx-update/latest | head -c 200
curl -s -o /dev/null -w '%{http_code}\n' -r 0-1023 \
  https://124.222.37.253/tx-update/apk/0.20.0          # 应 206
curl -s https://124.222.37.253/tx-update/plugins | head -c 120
```

## 备份位置

- `/root/tianxuan-backup/` —— 旧 APK、旧 manifest、旧脚本
- `/root/nginx-backups/` —— nginx 配置

**nginx 备份绝对不能放 `/etc/nginx/sites-enabled/`**，会被 nginx 当配置扫到，
报 `duplicate default server for 0.0.0.0:80`。