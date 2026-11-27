#!/usr/bin/env bash
# Generate an SPDX SBOM for a Glance disk image (qcow2 or raw) with Trivy.
# Loaded by the scanDiskImage step via libraryResource, and run on a docker
# agent, which provides qemu-img, sfdisk, blkid, jq and the sha*sum tools.
#
# qcow2 images are converted to a temporary raw file first, because
# `trivy vm` cannot read qcow2. Trivy runs in docker with the image mounted
# read-only. TRIVY_IMAGE overrides the Trivy image pulled.
#
# Trivy only scans GPT partitions whose name is on a fixed allow-list (see
# requiredDiskName in Trivy's pkg/fanal/walker/vm.go). ext4 and XFS
# partitions with any other name, such as Ubuntu 26.04's "cloudimg-rootfs",
# are renamed to "primary" in the temporary raw copy so they get scanned.
#
# Output goes to sboms/glance/<algo>/<hash>.spdx.json, named by the Glance
# os_hash_algo and os_hash_value. A .meta.json beside it records the source
# file name and the Glance details. Scratch files go to work/. Prints the
# SBOM path.

set -euo pipefail

# Only the SBOM path goes to stdout, which callers capture. Send all other
# output, including from subcommands, to stderr.
exec 3>&1 1>&2

TRIVY_IMAGE=${TRIVY_IMAGE:-aquasec/trivy:0.72.0}

# TRIVY_TIMEOUT, such as 60m, sets how long trivy vm may run. When it is
# unset, Trivy uses its default of 30 minutes.
timeout_args=()
if [ -n "${TRIVY_TIMEOUT:-}" ]; then
    timeout_args=(--timeout "$TRIVY_TIMEOUT")
fi

work=$PWD/work
out_root=$PWD/sboms/glance

die() {
    echo "error: $*" >&2
    exit 1
}

[ $# -eq 2 ] || die "usage: $(basename "$0") <disk-image> <glance-json>
  <glance-json> is the Glance details written by download-glance-image.sh"
src=$(realpath "$1")
glance=$2
[ -f "$src" ] || die "$1: no such file"
[ -f "$glance" ] || die "$glance: no such file"

algo=$(jq -r .os_hash_algo "$glance")
case $algo in
sha512 | sha384 | sha256 | sha224) ;;
*) die "$glance: unsupported os_hash_algo '$algo'" ;;
esac

name=$(basename "$src")
user="$(id -u):$(id -g)"

echo "Computing $algo of $name..." >&2
hash=$("${algo}sum" "$src" | cut -d' ' -f1)
[ "$hash" = "$(jq -r .os_hash_value "$glance")" ] ||
    die "$name does not match the os_hash_value in $glance"

out=$out_root/$algo/$hash.spdx.json
mkdir -p "$work/trivy-cache"

# partition_names <file> check|rename
# Prints "<number>\t<fstype>\t<name>" for each ext4 or XFS GPT partition
# that Trivy would skip because of its name. With "rename", also renames
# each one to "primary".
partition_names() {
    local img=$1 mode=$2 pt sector node start label num seen fstype
    pt=$(sfdisk --json "$img" 2>/dev/null) || return 0
    [ "$(jq -r .partitiontable.label <<<"$pt")" = gpt ] || return 0
    sector=$(jq -r .partitiontable.sectorsize <<<"$pt")
    jq -r '.partitiontable.partitions[] | [.node, .start, (.name // "")] | @tsv' <<<"$pt" |
        while IFS=$'\t' read -r node start label; do
            num=${node##*[!0-9]}
            # Trivy sees an unnamed partition by its zero-based index.
            seen=${label:-$((num - 1))}
            # Copied from requiredDiskName in Trivy's pkg/fanal/walker/vm.go
            # (v0.72.0). Check it again when TRIVY_IMAGE changes version.
            case $seen in Linux | p.lxroot | primary | 0 | 1 | 2 | 3) continue ;; esac
            fstype=$(blkid -p -O $((start * sector)) -s TYPE -o value "$img" || true)
            case $fstype in ext4 | xfs) ;; *) continue ;; esac
            # Name last: read merges adjacent tabs, so an empty name must not
            # sit between other fields.
            printf '%s\t%s\t%s\n' "$num" "$fstype" "$label"
            if [ "$mode" = rename ]; then
                sfdisk --part-label "$img" "$num" primary >/dev/null 2>&1
            fi
        done
}

format=$(qemu-img info --output json "$src" | jq -r .format)

raw=$work/$hash.raw
trap 'rm -f "$raw"' EXIT

case $format in
qcow2)
    echo "Converting qcow2 to raw..." >&2
    qemu-img convert -f qcow2 -O raw "$src" "$raw"
    scan=$raw
    ;;
raw)
    # Scan in place unless partitions need renaming, which needs a copy.
    if [ -n "$(partition_names "$src" check)" ]; then
        echo "Copying raw image so partitions can be renamed..." >&2
        cp --sparse=always "$src" "$raw"
        scan=$raw
    else
        scan=$src
    fi
    ;;
*)
    die "$name: unsupported image format '$format'"
    ;;
esac

renamed=
if [ "$scan" = "$raw" ]; then
    renamed=$(partition_names "$raw" rename)
    while IFS=$'\t' read -r num fstype label; do
        [ -n "$num" ] || continue
        was="'$label'"
        [ -n "$label" ] || was="no name"
        echo "Renamed $fstype partition $num ($was) to 'primary' so Trivy scans it" >&2
    done <<<"$renamed"
fi

# TODO(jake): Trivy downloads its 1.5 GB Java DB from mirror.gcr.io or
# ghcr.io on the first scan on each Jenkins agent, and about once a day
# after that, so scans can fail when both are down. For resilience and speed,
# mirror trivy-java-db in a private registry.rc.nectar.org.au project, give
# Jenkins a read-only robot account for it, and pass that to Trivy
# (TRIVY_USERNAME, TRIVY_PASSWORD) with --java-db-repository pointing at the
# mirror.
echo "Scanning with $TRIVY_IMAGE..." >&2
docker run --rm --user "$user" -v "$(dirname "$scan"):/scan:ro" \
    -v "$work:/work" -v "$work/trivy-cache:/cache" \
    "$TRIVY_IMAGE" vm "${timeout_args[@]}" --cache-dir /cache --scanners '' --format spdx-json \
    --no-progress --skip-version-check --disable-telemetry \
    -o "/work/$hash.spdx.json" "/scan/$(basename "$scan")"

# Check before moving the SBOM into place, so a likely empty one is never
# kept or uploaded.
os=$(jq -r '[.packages[]? | select(.primaryPackagePurpose == "OPERATING-SYSTEM")
    | "\(.name) \(.versionInfo)"] | first // empty' "$work/$hash.spdx.json")
[ -n "$os" ] || die "Trivy did not detect an OS in $name, so the SBOM is" \
    "likely empty. Run trivy vm with --debug and look for 'Found partition'"
echo "Detected OS: $os" >&2

mkdir -p "$out_root/$algo"
mv "$work/$hash.spdx.json" "$out"

jq -n \
    --arg source_file "$name" \
    --arg format "$format" \
    --arg hash_algo "$algo" \
    --arg hash_value "$hash" \
    --arg scanner "$TRIVY_IMAGE" \
    --arg scanned_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg renamed "$renamed" \
    --slurpfile glance "$glance" \
    '{source_file: $source_file, format: $format, hash_algo: $hash_algo,
      hash_value: $hash_value, scanner: $scanner,
      scanned_at: $scanned_at,
      renamed_partitions: [$renamed | splits("\n") | select(. != "")
        | split("\t") | {number: (.[0] | tonumber), fstype: .[1], name: (.[2] // "")}],
      glance: $glance[0]}' \
    >"${out%.spdx.json}.meta.json"

echo "$out" >&3
