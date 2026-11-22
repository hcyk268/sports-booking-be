#!/bin/sh
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
UAT_STORAGE_PROVIDER=s3 exec sh "$SCRIPT_DIR/test-storage-upload.sh" "$@"
