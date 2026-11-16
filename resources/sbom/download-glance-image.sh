#!/usr/bin/env bash
# Download a Glance image, given its ID, and check it against the hash Glance
# recorded for it. Loaded by the downloadGlanceImage step via
# libraryResource, which sets the OS_* credentials, and run on a docker
# agent, which provides the openstack client, jq and the sha*sum tools.
#
# Writes glance/<id>.<disk_format> and, beside it with the same name,
# glance/<id>.json (the Glance details, for scan-disk-image.sh), and prints
# the image path.

set -euo pipefail

# Only the image path goes to stdout, which callers capture. Send all other
# output, including from subcommands, to stderr.
exec 3>&1 1>&2

images=$PWD/glance

die() {
    echo "error: $*" >&2
    exit 1
}

[ $# -eq 1 ] || die "usage: $(basename "$0") <image-id>"
id=$1
# The ID becomes part of the download's file name, so allow only a UUID.
hex='[0-9a-fA-F]'
[[ $id =~ ^$hex{8}-$hex{4}-$hex{4}-$hex{4}-$hex{12}$ ]] ||
    die "$id: not a Glance image ID (expected a UUID)"

echo "Looking up image $id..." >&2
info=$(openstack image show -f json "$id")
name=$(jq -r .name <<<"$info")
status=$(jq -r .status <<<"$info")
disk_format=$(jq -r .disk_format <<<"$info")
# The openstack client lists os_hash_algo and os_hash_value under
# properties. Check the top level too, in case other versions differ.
algo=$(jq -r '.properties.os_hash_algo // .os_hash_algo // empty' <<<"$info")
value=$(jq -r '.properties.os_hash_value // .os_hash_value // empty' <<<"$info")

[ "$status" = active ] || die "$id: image status is '$status', not 'active'"
[ -n "$value" ] || die "$id: Glance has no os_hash_value for this image"
case $disk_format in
qcow2 | raw) ;;
*) die "$id: disk format '$disk_format' is not supported (qcow2 or raw only)" ;;
esac
case $algo in
sha512 | sha384 | sha256 | sha224) ;;
*) die "$id: hash algorithm '$algo' is not supported" ;;
esac

mkdir -p "$images"
file=$images/$id.$disk_format

echo "Downloading $name..." >&2
openstack image save --file "$file" "$id"
echo "Verifying $algo hash..." >&2
if [ "$("${algo}sum" "$file" | cut -d' ' -f1)" != "$value" ]; then
    rm -f "$file"
    die "$id: downloaded file does not match Glance's hash"
fi

# os_hash_algo and os_hash_value hold the hash the file was checked
# against, so scan-disk-image.sh names the SBOM by the same one.
jq --arg algo "$algo" --arg value "$value" '{id, name, disk_format,
    os_hash_algo: $algo, os_hash_value: $value,
    checksum, size, visibility, owner, created_at, updated_at,
    properties}' <<<"$info" >"${file%.*}.json"

echo "$file" >&3
