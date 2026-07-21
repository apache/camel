#!/bin/sh
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -eu

# Test seams only: production installs never set these, so the defaults below are always used.
manifest_base_url="${CAMEL_INSTALL_MANIFEST_BASE_URL:-https://camel.apache.org/camel-cli/releases}"
maven_base_url="${CAMEL_INSTALL_MAVEN_BASE_URL:-https://repo1.maven.org/maven2/org/apache/camel/camel-launcher}"
ca_cert="${CAMEL_INSTALL_CA_CERT:-}"

data_root="${XDG_DATA_HOME:-$HOME/.local/share}/camel-cli/versions"
camel_cli_root="${XDG_DATA_HOME:-$HOME/.local/share}/camel-cli"
bin_dir="$HOME/.local/bin"

fail() {
    echo "install.sh: $1" 1>&2
    exit 1
}

is_valid_version() {
    v="$1"
    case "$v" in
        '' | .* | *. | *..* | *[!0-9.]*) return 1 ;;
    esac
    # The case above guarantees digits and single dots only, with no leading, trailing, or doubled
    # dot, so a valid X.Y.Z is exactly the value containing two dots (three non-empty components).
    rest="$v"
    dots=0
    while :; do
        case "$rest" in
            *.*)
                dots=$((dots + 1))
                rest="${rest#*.}"
                ;;
            *)
                break
                ;;
        esac
    done
    [ "$dots" -eq 2 ]
}

validate_sha256() {
    value="$1"
    label="$2"
    [ ${#value} -eq 64 ] || fail "$label is not a 64-character lowercase hex value"
    case "$value" in
        *[!0-9a-f]*) fail "$label is not a 64-character lowercase hex value" ;;
    esac
}

fetch() {
    url="$1"
    outfile="$2"
    if command -v curl >/dev/null 2>&1; then
        if [ -n "$ca_cert" ]; then
            curl -fsSL --cacert "$ca_cert" -o "$outfile" "$url"
        else
            curl -fsSL -o "$outfile" "$url"
        fi
    elif command -v wget >/dev/null 2>&1; then
        if [ -n "$ca_cert" ]; then
            wget -q --ca-certificate="$ca_cert" -O "$outfile" "$url"
        else
            wget -q -O "$outfile" "$url"
        fi
    else
        fail "curl or wget is required to install the Camel CLI"
    fi
}

sha256() {
    file="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha256 "$file" | awk '{print $NF}'
    else
        fail "sha256sum, shasum, or openssl is required to verify the download"
    fi
}

# Reads $manifest_file line by line without ever sourcing, dot-invoking, or eval-ing its content.
parse_manifest() {
    file="$1"
    cr=$(printf '\r')
    p_format=""
    p_version=""
    p_tar=""
    p_zip=""
    seen_format=0
    seen_version=0
    seen_tar=0
    seen_zip=0
    line_count=0
    while IFS='=' read -r key value || [ -n "$key" ]; do
        # Tolerate a manifest served with CRLF line endings, matching the PowerShell parser.
        key="${key%"$cr"}"
        value="${value%"$cr"}"
        # Skip comment lines (properties-style '#', and the '##' ASF license header the website's
        # manifest generator prepends). They carry no data, so they are neither counted toward the
        # required line total nor validated as key=value pairs. Blank lines are still rejected below.
        case "$key" in
            \#*) continue ;;
        esac
        line_count=$((line_count + 1))
        [ -n "$key" ] || fail "manifest contains a blank line"
        [ -n "$value" ] || fail "manifest key '$key' has an empty value"
        case "$key" in
            format)
                [ "$seen_format" -eq 0 ] || fail "manifest has duplicate key: format"
                seen_format=1
                p_format="$value"
                ;;
            version)
                [ "$seen_version" -eq 0 ] || fail "manifest has duplicate key: version"
                seen_version=1
                p_version="$value"
                ;;
            tar_sha256)
                [ "$seen_tar" -eq 0 ] || fail "manifest has duplicate key: tar_sha256"
                seen_tar=1
                p_tar="$value"
                ;;
            zip_sha256)
                [ "$seen_zip" -eq 0 ] || fail "manifest has duplicate key: zip_sha256"
                seen_zip=1
                p_zip="$value"
                ;;
            *)
                fail "manifest has unknown key: $key"
                ;;
        esac
    done < "$file"

    if [ "$seen_format" -ne 1 ] || [ "$seen_version" -ne 1 ] || [ "$seen_tar" -ne 1 ] || [ "$seen_zip" -ne 1 ]; then
        fail "manifest is missing a required key"
    fi
    [ "$line_count" -eq 4 ] || fail "manifest must contain exactly four lines"

    [ "$p_format" = "1" ] || fail "unsupported manifest format: $p_format"
    is_valid_version "$p_version" || fail "manifest version is not a valid X.Y.Z value"
    validate_sha256 "$p_tar" "manifest tar_sha256"
    validate_sha256 "$p_zip" "manifest zip_sha256"
}

# Lists archive entries with 'tar -tzf' and rejects absolute paths, traversal, symlinks/hardlinks,
# multiple top-level roots, and a missing expected launcher, before anything is extracted.
validate_tar() {
    archive="$1"
    version="$2"
    expected_root="camel-launcher-$version"

    listing="$staging_dir/listing.txt"
    tar -tzf "$archive" > "$listing" 2>/dev/null || fail "archive is not a valid tar.gz"

    verbose_listing="$staging_dir/listing-verbose.txt"
    # LC_ALL=C keeps tar's hard-link annotation as the literal 'link to' string; GNU tar localizes it.
    LC_ALL=C tar -tvzf "$archive" > "$verbose_listing" 2>/dev/null || fail "archive is not a valid tar.gz"
    # tar -tv renders symlinks as 'name -> target' and hard links as 'name link to target'. Matching
    # those substrings could in theory over-reject a regular file whose name contains ' -> ' or
    # ' link to '; that fail-closed bias is deliberate, the release archive never carries such names.
    grep -E -- ' -> | link to ' "$verbose_listing" >/dev/null 2>&1 \
        && fail "archive contains a symbolic or hard link entry, which is not allowed"

    saw_entry=0
    while IFS= read -r entry; do
        [ -n "$entry" ] || continue
        case "$entry" in
            /*) fail "archive contains an absolute path entry: $entry" ;;
        esac
        case "$entry" in
            *"../"* | ".." | *"/..") fail "archive contains a path traversal entry: $entry" ;;
        esac
        # Every entry must sit under the single expected top-level directory; a differing root means a
        # stray directory or the wrong version, so reject it directly rather than collecting roots.
        root="${entry%%/*}"
        [ "$root" = "$expected_root" ] \
            || fail "archive top-level directory does not match expected version: $root"
        saw_entry=1
    done < "$listing"

    [ "$saw_entry" -eq 1 ] || fail "archive must contain exactly one top-level directory"

    grep -Fqx "$expected_root/bin/camel.sh" "$listing" || fail "archive is missing bin/camel.sh"
}

# Runs the freshly staged upstream launcher; a nonzero exit (e.g. no Java 17+ available) aborts
# the install and leaves the previously active installation untouched.
verify_staged() {
    staged_sh="$1"
    chmod +x "$staged_sh" 2>/dev/null || true
    "$staged_sh" version >/dev/null 2>&1 || fail "staged launcher failed verification (Java 17+ required)"
}

activate() {
    version="$1"
    staged_root_dir="$2"
    target_dir="$data_root/$version"

    mkdir -p "$data_root"
    rm -rf "$target_dir"
    mv "$staged_root_dir" "$target_dir"

    mkdir -p "$bin_dir"
    tmp_link="$bin_dir/.camel.tmp.$$"
    rm -f "$tmp_link"
    ln -s "$target_dir/bin/camel.sh" "$tmp_link"
    mv -f "$tmp_link" "$bin_dir/camel"
}

requested_version=""
while [ $# -gt 0 ]; do
    case "$1" in
        --version)
            [ $# -ge 2 ] || fail "Usage: install.sh [--version X.Y.Z]"
            requested_version="$2"
            shift 2
            ;;
        *)
            fail "Usage: install.sh [--version X.Y.Z]"
            ;;
    esac
done

if [ -n "$requested_version" ]; then
    is_valid_version "$requested_version" || fail "invalid --version value: $requested_version (expected X.Y.Z)"
fi

staging_dir="$camel_cli_root/.staging.$$"
cleanup() {
    rm -rf "$staging_dir"
}
trap cleanup EXIT INT TERM

mkdir -p "$camel_cli_root"
rm -rf "$staging_dir"
mkdir -m 700 "$staging_dir"

if [ -n "$requested_version" ]; then
    manifest_url="$manifest_base_url/$requested_version.properties"
else
    manifest_url="$manifest_base_url/latest.properties"
fi

manifest_file="$staging_dir/manifest.properties"
fetch "$manifest_url" "$manifest_file" || fail "failed to download manifest from $manifest_url"

parse_manifest "$manifest_file"
version="$p_version"

if [ -n "$requested_version" ] && [ "$requested_version" != "$version" ]; then
    fail "manifest version ($version) does not match requested version ($requested_version)"
fi

archive_url="$maven_base_url/$version/camel-launcher-$version-bin.tar.gz"
archive_file="$staging_dir/camel-launcher-$version-bin.tar.gz"
fetch "$archive_url" "$archive_file" || fail "failed to download archive from $archive_url"

actual_tar_sha256="$(sha256 "$archive_file")"
[ "$actual_tar_sha256" = "$p_tar" ] || fail "checksum mismatch for downloaded archive"

validate_tar "$archive_file" "$version"

extract_dir="$staging_dir/extract"
mkdir -p "$extract_dir"
tar -xzf "$archive_file" -C "$extract_dir"

staged_root_dir="$extract_dir/camel-launcher-$version"
verify_staged "$staged_root_dir/bin/camel.sh"

activate "$version" "$staged_root_dir"

case ":$PATH:" in
    *":$bin_dir:"*) ;;
    *) echo "Add $bin_dir to your PATH to use the 'camel' command." ;;
esac

echo "Installed Camel CLI $version to $data_root/$version"
