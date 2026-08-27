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

# Absolute, so it stays correct wherever the script is invoked from
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

apply() {
    name="$2"
    submodule="$root/$1"
    patch="$root/patches/$2"

    if [ ! -d "$submodule" ]; then
        echo "missing submodule: $1 (run: git submodule update --init --recursive)" >&2
        exit 1
    fi

    if git -C "$submodule" apply --reverse --check "$patch" 2>/dev/null; then
        echo "already applied: $name"
        return
    fi

    if git -C "$submodule" apply "$patch" 2>/dev/null; then
        echo "applied: $name"
        return
    fi

    # Line endings differing between the patch and the checkout are the usual
    # reason a byte-exact match fails; the change itself still applies
    if git -C "$submodule" apply --ignore-whitespace "$patch" 2>/dev/null; then
        echo "applied (ignoring whitespace): $name"
        return
    fi

    echo "FAILED to apply: $name" >&2
    echo "  submodule $1 at $(git -C "$submodule" rev-parse HEAD)" >&2
    echo "  --- git apply -v ---" >&2
    git -C "$submodule" apply -v "$patch" >&2 || true
    echo "  --- target file as checked out ---" >&2
    sed -n '70,80p' "$submodule/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/common/helpers/QueryBuilder.kt" | cat -A >&2 || true
    echo "  if upstream already fixed this, delete the patch file" >&2
    exit 1
}

apply MediaServiceCore mediaservicecore-signature-timestamp-npe.patch
