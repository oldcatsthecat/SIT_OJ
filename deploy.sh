#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [ ! -f .env ]; then
    echo "Missing private .env. Copy your generated .env securely; see SECURITY-DEPLOY.md."
    exit 1
fi
exec python3 ops/secure_deploy.py
