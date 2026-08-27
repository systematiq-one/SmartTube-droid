#!/bin/sh
# Applies local fixes to the git submodules.
#
# The submodules track upstream repos, so our changes cannot be committed
# there. They live here as patches instead and get applied after checkout.
# Run from the repo root: sh patches/apply.sh
#
# A patch that no longer applies usually means upstream fixed it themselves.
# Check, then delete the patch file.
set -e

apply() {
    submodule="$1"
    patch="$2"

    if git -C "$submodule" apply --reverse --check "../patches/$patch" 2>/dev/null; then
        echo "already applied: $patch"
        return
    fi

    git -C "$submodule" apply "../patches/$patch"
    echo "applied: $patch"
}

apply MediaServiceCore mediaservicecore-signature-timestamp-npe.patch
