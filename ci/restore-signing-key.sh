#!/usr/bin/env bash
set -euo pipefail
base64 --decode ci/skt-common-signing.jks.b64 > ci/skt-common-signing.jks
