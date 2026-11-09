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
snapshot_base_url="${CAMEL_INSTALL_SNAPSHOT_BASE_URL:-https://repository.apache.org/content/repositories/snapshots/org/apache/camel/camel-launcher}"
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
    # dot, so a valid X.Y.Z is exactly the value with three non-empty components (two dots).
    case "$v" in
        *.*.*.*) return 1 ;;
        *.*.*) return 0 ;;
        *) return 1 ;;
    esac
}

# A release line selector: X.Y, digits and exactly one dot.
is_valid_line() {
    v="$1"
    case "$v" in
        '' | .* | *. | *..* | *[!0-9.]*) return 1 ;;
    esac
    case "$v" in
        *.*.*) return 1 ;;
        *.*) return 0 ;;
        *) return 1 ;;
    esac
}

# A snapshot version: X.Y.Z-SNAPSHOT.
is_valid_snapshot() {
    case "$1" in
        *-SNAPSHOT) is_valid_version "${1%-SNAPSHOT}" ;;
        *) return 1 ;;
    esac
}

# One nightly build, as the snapshot repository names it: X.Y.Z-YYYYMMDD.HHMMSS-N. The value ends up in a directory
# name and a download URL, so it is checked character by character.
is_valid_nightly() {
    nb_value="$1"
    nb_base="${nb_value%%-*}"
    nb_rest="${nb_value#*-}"
    [ "$nb_rest" != "$nb_value" ] || return 1
    is_valid_version "$nb_base" || return 1
    nb_number="${nb_rest##*-}"
    nb_stamp="${nb_rest%-*}"
    case "$nb_stamp" in
        [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9].[0-9][0-9][0-9][0-9][0-9][0-9]) ;;
        *) return 1 ;;
    esac
    case "$nb_number" in
        '' | *[!0-9]*) return 1 ;;
    esac
    [ ${#nb_number} -le 9 ]
}

# Classifies a --version value as an exact release (X.Y.Z), a release line (X.Y), the newest LTS release (LTS,
# case-insensitive), the nightly builds of main (SNAPSHOT, case-insensitive) or of a snapshot version
# (X.Y.Z-SNAPSHOT), or one nightly build (X.Y.Z-YYYYMMDD.HHMMSS-N). Sets $selector_kind and normalises
# $requested_version ('lts' becomes 'LTS', 'snapshot' becomes 'SNAPSHOT').
is_valid_selector() {
    case "$requested_version" in
        [Ll][Tt][Ss])
            requested_version="LTS"
            selector_kind="lts"
            return 0
            ;;
        [Ss][Nn][Aa][Pp][Ss][Hh][Oo][Tt])
            requested_version="SNAPSHOT"
            selector_kind="snapshot"
            return 0
            ;;
    esac
    if is_valid_snapshot "$requested_version"; then
        selector_kind="snapshot"
        return 0
    fi
    if is_valid_nightly "$requested_version"; then
        selector_kind="nightly"
        return 0
    fi
    if is_valid_version "$requested_version"; then
        selector_kind="exact"
        return 0
    fi
    if is_valid_line "$requested_version"; then
        selector_kind="line"
        return 0
    fi
    return 1
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
            wget -nv --ca-certificate="$ca_cert" -O "$outfile" "$url"
        else
            wget -nv -O "$outfile" "$url"
        fi
    else
        fail "curl or wget is required to install the Camel CLI"
    fi
}

sha1() {
    file="$1"
    if command -v sha1sum >/dev/null 2>&1; then
        sha1sum "$file" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 1 "$file" | awk '{print $1}'
    elif command -v openssl >/dev/null 2>&1; then
        openssl dgst -sha1 "$file" | awk '{print $NF}'
    else
        fail "sha1sum, shasum, or openssl is required to verify the download"
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
    tar_err=$(tar -tzf "$archive" 2>&1 1>"$listing") || fail "archive is not a valid tar.gz${tar_err:+: $tar_err}"

    verbose_listing="$staging_dir/listing-verbose.txt"
    # LC_ALL=C keeps tar's hard-link annotation as the literal 'link to' string; GNU tar localizes it.
    tar_err=$(LC_ALL=C tar -tvzf "$archive" 2>&1 1>"$verbose_listing") \
        || fail "archive is not a valid tar.gz${tar_err:+: $tar_err}"
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

# Runs the freshly staged upstream launcher; a nonzero exit (e.g. no Java 21+ available) aborts
# the install and leaves the previously active installation untouched.
verify_staged() {
    staged_sh="$1"
    chmod +x "$staged_sh" 2>/dev/null || true
    "$staged_sh" version >/dev/null 2>&1 || fail "staged launcher failed verification (Java 21+ required)"
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

# The snapshot version of main: the highest X.Y.Z-SNAPSHOT listed in the snapshot repository metadata (the
# maintenance branches publish nightly builds too, under lower versions). Maven writes one element per line.
latest_snapshot_version() {
    sed -n 's|.*<version>\([^<]*\)</version>.*|\1|p' "$1" | while IFS= read -r lsv; do
        is_valid_snapshot "$lsv" && printf '%s\n' "${lsv%-SNAPSHOT}"
    done | sort -t. -n -k1,1 -k2,2 -k3,3 | tail -1
}

# The newest nightly build of a snapshot version ($2, X.Y.Z) from its metadata ($1): the <value> of the bin tar.gz
# <snapshotVersion> (Maven 3 metadata), or else the <timestamp>-<buildNumber> of the <snapshot> (Maven 2 metadata,
# which Nexus 2 serves to a client it does not take for Maven 3), as all the files of one deploy share them.
newest_nightly() {
    awk -v base="$2" '
        /<snapshotVersion>/ { inside = 1; c = ""; e = ""; v = "" }
        inside && /<classifier>/ { c = $0; gsub(/.*<classifier>|<\/classifier>.*/, "", c) }
        inside && /<extension>/ { e = $0; gsub(/.*<extension>|<\/extension>.*/, "", e) }
        inside && /<value>/ { v = $0; gsub(/.*<value>|<\/value>.*/, "", v) }
        /<\/snapshotVersion>/ { if (c == "bin" && e == "tar.gz") { found = v } inside = 0 }
        /<timestamp>/ && ts == "" { ts = $0; gsub(/.*<timestamp>|<\/timestamp>.*/, "", ts) }
        /<buildNumber>/ && bn == "" { bn = $0; gsub(/.*<buildNumber>|<\/buildNumber>.*/, "", bn) }
        END {
            if (found != "") { print found } else if (ts != "" && bn != "") { print base "-" ts "-" bn }
        }
    ' "$1"
}

# Removes all but the newest three nightly builds, never the one just installed: a command that still runs from one
# of them keeps its files. Releases are never removed. The key is <timestamp>-<buildNumber>, with the build number
# compared as a number. Pruning is best effort: the new build is already active, so a failed removal only warns.
prune_nightlies() {
    keep_build="$1"
    ls "$data_root" | while IFS= read -r pn_dir; do
        is_valid_nightly "$pn_dir" && printf '%s %s\n' "${pn_dir#*-}" "$pn_dir"
    done | sort -t- -k1,1r -k2,2nr | awk 'NR > 3 { print $2 }' | while IFS= read -r pn_old; do
        [ "$pn_old" = "$keep_build" ] || rm -rf "${data_root:?}/$pn_old" \
            || echo "Warning: could not remove the old nightly build $data_root/$pn_old" >&2
    done
}

# Installs a nightly build from the snapshot repository, verified against the SHA-1 published next to it there: a
# nightly build is no Apache release, so there is no manifest on camel.apache.org for it.
install_nightly() {
    case "$requested_version" in
        SNAPSHOT)
            metadata_file="$staging_dir/metadata.xml"
            fetch "$snapshot_base_url/maven-metadata.xml" "$metadata_file" \
                || fail "failed to download the snapshot metadata from $snapshot_base_url/maven-metadata.xml"
            snapshot_base="$(latest_snapshot_version "$metadata_file")"
            [ -n "$snapshot_base" ] || fail "the snapshot repository lists no X.Y.Z-SNAPSHOT version"
            snapshot_version="$snapshot_base-SNAPSHOT"
            ;;
        *-SNAPSHOT)
            snapshot_version="$requested_version"
            ;;
        *)
            snapshot_version="${requested_version%%-*}-SNAPSHOT"
            nightly="$requested_version"
            ;;
    esac
    if [ -z "${nightly:-}" ]; then
        metadata_file="$staging_dir/$snapshot_version-metadata.xml"
        fetch "$snapshot_base_url/$snapshot_version/maven-metadata.xml" "$metadata_file" \
            || fail "no nightly builds of $snapshot_version at $snapshot_base_url/$snapshot_version/maven-metadata.xml"
        nightly="$(newest_nightly "$metadata_file" "${snapshot_version%-SNAPSHOT}")"
    fi
    is_valid_nightly "$nightly" && [ "${nightly%%-*}-SNAPSHOT" = "$snapshot_version" ] \
        || fail "the snapshot metadata names an invalid nightly build of $snapshot_version: $nightly"

    # camel self-update printed the note already
    if [ "${CAMEL_INSTALL_SELF_UPDATE:-}" != "true" ]; then
        echo "Note: $nightly is a nightly development build of Apache Camel, for testing only. It is not an Apache" \
             "release, and is verified only against the SHA-1 published next to it in the snapshot repository."
    fi

    archive_url="$snapshot_base_url/$snapshot_version/camel-launcher-$nightly-bin.tar.gz"
    archive_file="$staging_dir/camel-launcher-$nightly-bin.tar.gz"
    fetch "$archive_url" "$archive_file" || fail "failed to download archive from $archive_url"
    sha1_file="$staging_dir/archive.sha1"
    fetch "$archive_url.sha1" "$sha1_file" || fail "failed to download checksum from $archive_url.sha1"
    expected_sha1="$(awk '{ print $1; exit }' "$sha1_file")"
    [ ${#expected_sha1} -eq 40 ] || fail "published SHA-1 is not a 40-character lowercase hex value"
    case "$expected_sha1" in
        *[!0-9a-f]*) fail "published SHA-1 is not a 40-character lowercase hex value" ;;
    esac
    [ "$(sha1 "$archive_file")" = "$expected_sha1" ] || fail "checksum mismatch for downloaded archive"

    # The archive of a nightly build unpacks to camel-launcher-X.Y.Z-SNAPSHOT.
    validate_tar "$archive_file" "$snapshot_version"
    extract_dir="$staging_dir/extract"
    mkdir -p "$extract_dir"
    tar -xzf "$archive_file" -C "$extract_dir"
    staged_root_dir="$extract_dir/camel-launcher-$snapshot_version"
    verify_staged "$staged_root_dir/bin/camel.sh"

    activate "$nightly" "$staged_root_dir"
    update_pin_state
    prune_nightlies "$nightly"
    print_path_hint
    echo "Installed Camel CLI $nightly (a nightly build of $snapshot_version) to $data_root/$nightly"
}

print_path_hint() {
    case ":$PATH:" in
        *":$bin_dir:"*) ;;
        *) echo "Add $bin_dir to your PATH to use the 'camel' command." ;;
    esac
}

# Records whether this install is pinned, so `camel self-update` can honour it: an exact X.Y.Z pin refuses to
# move, an X.Y or LTS pin only moves within that selector. The selector the user asked for is written, not the
# version it resolved to, so an exact pin is unchanged and a selector pin keeps tracking its alias. Skipped when
# CAMEL_INSTALL_SELF_UPDATE=true: self-update always passes its own resolved --version for TOCTOU-safety (see
# SelfUpdateCommand), which is not a human pinning a version and must not create or clear a pin either way.
update_pin_state() {
    [ "${CAMEL_INSTALL_SELF_UPDATE:-}" = "true" ] && return 0
    pinned_version_file="$camel_cli_root/pinned-version"
    if [ -n "$requested_version" ]; then
        printf '%s\n' "$requested_version" > "$pinned_version_file"
    else
        rm -f "$pinned_version_file"
    fi
}

usage="Usage: install.sh [--version X.Y.Z|X.Y|LTS|X.Y.Z-SNAPSHOT|SNAPSHOT]"
requested_version=""
selector_kind=""
while [ $# -gt 0 ]; do
    case "$1" in
        --version)
            [ $# -ge 2 ] || fail "$usage"
            requested_version="$2"
            shift 2
            ;;
        *)
            fail "$usage"
            ;;
    esac
done

if [ -n "$requested_version" ]; then
    is_valid_selector \
        || fail "invalid --version value: $requested_version (expected X.Y.Z, X.Y, LTS, X.Y.Z-SNAPSHOT or SNAPSHOT)"
fi

staging_dir="$camel_cli_root/.staging.$$"
cleanup() {
    rm -rf "$staging_dir"
}
trap cleanup EXIT INT TERM

mkdir -p "$camel_cli_root"
rm -rf "$staging_dir"
mkdir -m 700 "$staging_dir"

case "$selector_kind" in
    snapshot | nightly)
        install_nightly
        exit 0
        ;;
esac

case "$selector_kind" in
    lts) manifest_url="$manifest_base_url/lts.properties" ;;
    exact | line) manifest_url="$manifest_base_url/$requested_version.properties" ;;
    *) manifest_url="$manifest_base_url/latest.properties" ;;
esac

manifest_file="$staging_dir/manifest.properties"
case "$selector_kind" in
    lts | line)
        fetch "$manifest_url" "$manifest_file" \
            || fail "no manifest for $requested_version at $manifest_url; the website may not publish aliases yet"
        ;;
    *)
        fetch "$manifest_url" "$manifest_file" || fail "failed to download manifest from $manifest_url"
        ;;
esac

parse_manifest "$manifest_file"
version="$p_version"

# Resolve the selector once: everything below works on the exact resolved $version.
case "$selector_kind" in
    exact)
        [ "$requested_version" = "$version" ] \
            || fail "manifest version ($version) does not match requested version ($requested_version)"
        ;;
    line)
        case "$version" in
            "$requested_version".*) ;;
            *) fail "manifest version ($version) is not in the requested release line ($requested_version)" ;;
        esac
        ;;
esac

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
update_pin_state
print_path_hint

echo "Installed Camel CLI $version to $data_root/$version"
