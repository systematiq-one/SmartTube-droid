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

# Absolute, so it stays correct after git apply resolves it against the
# submodule toplevel rather than the working directory
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

apply() {
    submodule="$root/$1"
    patch="$root/patches/$2"

    if [ ! -d "$submodule" ]; then
        echo "missing submodule: $1 (run: git submodule update --init --recursive)" >&2
        exit 1
    fi

    if git -C "$submodule" apply --reverse --check "$patch" 2>/dev/null; then
        echo "already applied: $2"
        return
    fi

    if ! git -C "$submodule" apply -v "$patch"; then
        echo "FAILED to apply: $2" >&2
        echo "  submodule $1 is at $(git -C "$submodule" rev-parse HEAD)" >&2
        echo "  if upstream already fixed this, delete the patch file" >&2
        exit 1
    fi

    echo "applied: $2"
}

apply MediaServiceCore mediaservicecore-signature-timestamp-npe.patch
