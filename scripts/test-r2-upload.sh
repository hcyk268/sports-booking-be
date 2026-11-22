#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
UAT_STORAGE_PROVIDER=r2 exec sh "$SCRIPT_DIR/test-storage-upload.sh" "$@"
