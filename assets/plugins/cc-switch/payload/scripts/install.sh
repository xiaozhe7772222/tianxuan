#!/bin/sh
set -e

echo "[*] Installing CC-Switch Agent Hub..."

TOOL_DIR="${TIANXUAN_TOOL_DIR:-/opt/tianxuan/tools/cc-switch}"
DATA_DIR="${TIANXUAN_TOOL_DATA:-$TOOL_DIR/data}"
PAYLOAD="${TIANXUAN_PLUGIN_PAYLOAD:-/opt/tianxuan/imports/cc-switch/payload}"

mkdir -p "$TOOL_DIR/bin"
mkdir -p "$TOOL_DIR/lib"
mkdir -p "$DATA_DIR" 2>/dev/null || true

# 1. Copy the server ELF binary to lib/cc-switch-server
if [ -f "$PAYLOAD/lib/cc-switch-server" ]; then
    cp -a "$PAYLOAD/lib/cc-switch-server" "$TOOL_DIR/lib/cc-switch-server"
    chmod 755 "$TOOL_DIR/lib/cc-switch-server"
elif [ -f "$PAYLOAD/bin/cc-switch-daemon" ] && [ "$(head -c 4 "$PAYLOAD/bin/cc-switch-daemon" 2>/dev/null)" = "$(printf '\177ELF')" ]; then
    cp -a "$PAYLOAD/bin/cc-switch-daemon" "$TOOL_DIR/lib/cc-switch-server"
    chmod 755 "$TOOL_DIR/lib/cc-switch-server"
fi

# 2. Copy wrapper script to bin/cc-switch-daemon
if [ -f "$PAYLOAD/bin/cc-switch-daemon" ] && [ "$(head -c 4 "$PAYLOAD/bin/cc-switch-daemon" 2>/dev/null)" != "$(printf '\177ELF')" ]; then
    cp -a "$PAYLOAD/bin/cc-switch-daemon" "$TOOL_DIR/bin/cc-switch-daemon"
else
    cat << 'EOF' > "$TOOL_DIR/bin/cc-switch-daemon"
#!/bin/sh
set -e

PORT="${CC_SWITCH_PORT:-19870}"
DATA_DIR="${CC_SWITCH_DATA_DIR:-/opt/tianxuan/data/cc-switch}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TOOL_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_BIN="$TOOL_DIR/lib/cc-switch-server"

# Fast path for version and help flags to guarantee immediate non-blocking exit
for arg in "$@"; do
    case "$arg" in
        --version|-v|-V)
            echo "cc-switch 1.0.0 (ARM64)"
            exit 0
            ;;
        --help|-h)
            echo "Usage: cc-switch-daemon [--port <port>] [--data-dir <dir>]"
            exit 0
            ;;
    esac
done

while [ $# -gt 0 ]; do
    case "$1" in
        --port|-p)
            if [ $# -ge 2 ]; then
                PORT="$2"
                shift 2
            else
                shift
            fi
            ;;
        --data-dir|-d)
            if [ $# -ge 2 ]; then
                DATA_DIR="$2"
                shift 2
            else
                shift
            fi
            ;;
        *)
            shift
            ;;
    esac
done

# Standardize environment for headless Rust server
export HOME="${HOME:-/root}"
export USER="${USER:-root}"
export LOGNAME="${LOGNAME:-root}"
export HOST="${CC_SWITCH_HOST:-127.0.0.1}"
export CC_SWITCH_HOST="$HOST"
export PORT="$PORT"
export CC_SWITCH_PORT="$PORT"
export CC_SWITCH_DATA_DIR="$DATA_DIR"
export XDG_DATA_HOME="$DATA_DIR"
export CC_SWITCH_LAN_CORS="${CC_SWITCH_LAN_CORS:-0}"
export ALLOW_LAN_CORS="$CC_SWITCH_LAN_CORS"
export ALLOW_HTTP_BASIC_OVER_HTTP="${ALLOW_HTTP_BASIC_OVER_HTTP:-0}"
export RUST_LOG="${RUST_LOG:-info}"

# Ensure data directory and HOME directory layout exist
mkdir -p "$DATA_DIR" 2>/dev/null || true
mkdir -p "$HOME" 2>/dev/null || true

# cc-switch-server stores database, credentials, and settings in ~/.cc-switch
# We ensure ~/.cc-switch is linked or created inside the persistent tool data directory
if [ ! -d "$HOME/.cc-switch" ]; then
    mkdir -p "$HOME" 2>/dev/null || true
    ln -sfn "$DATA_DIR" "$HOME/.cc-switch" 2>/dev/null || mkdir -p "$HOME/.cc-switch"
fi

# Ensure web auth credentials exist so load_or_generate_web_credentials never errors.
# First-time setup generates a strong random password instead of a hardcoded default.
if [ ! -f "$HOME/.cc-switch/web_username" ]; then
    printf 'admin' > "$HOME/.cc-switch/web_username" 2>/dev/null || true
fi
if [ ! -f "$HOME/.cc-switch/web_password" ]; then
    RANDOM_PW="$(head -c 16 /dev/urandom | base64 | tr -d '/+=' | cut -c1-20)"
    if [ -n "$RANDOM_PW" ]; then
        printf '%s' "$RANDOM_PW" > "$HOME/.cc-switch/web_password" 2>/dev/null || true
        echo "[+] CC-Switch web password auto-generated (username: admin). Check $HOME/.cc-switch/web_password"
    else
        # Fallback if /dev/urandom unavailable
        printf 'admin123' > "$HOME/.cc-switch/web_password" 2>/dev/null || true
        echo "[!] WARNING: /dev/urandom unavailable, using default password. Change it immediately."
    fi
fi
chmod 600 "$HOME/.cc-switch/web_username" "$HOME/.cc-switch/web_password" 2>/dev/null || true

# Binary fallback resolution
if [ ! -f "$SERVER_BIN" ]; then
    if [ -f "/opt/tianxuan/tools/cc-switch/lib/cc-switch-server" ]; then
        SERVER_BIN="/opt/tianxuan/tools/cc-switch/lib/cc-switch-server"
    elif [ -f "$TOOL_DIR/bin/cc-switch-server" ]; then
        SERVER_BIN="$TOOL_DIR/bin/cc-switch-server"
    elif [ -f "/opt/tianxuan/imports/cc-switch/payload/lib/cc-switch-server" ]; then
        SERVER_BIN="/opt/tianxuan/imports/cc-switch/payload/lib/cc-switch-server"
    fi
fi

if [ -f "$SERVER_BIN" ]; then
    chmod 755 "$SERVER_BIN" 2>/dev/null || true
    pkill -9 -f cc-switch-server 2>/dev/null || killall -9 cc-switch-server 2>/dev/null || true
    echo "[TianXuan cc-switch-daemon] Starting CC-Switch on $HOST:$PORT (DATA_DIR=$DATA_DIR, HOME=$HOME)..."
    exec "$SERVER_BIN"
else
    echo "Error: cc-switch-server ELF binary not found at $SERVER_BIN" >&2
    exit 1
fi

EOF
fi

chmod 755 "$TOOL_DIR/bin/cc-switch-daemon"

echo "[+] CC-Switch Agent Hub installed successfully!"
