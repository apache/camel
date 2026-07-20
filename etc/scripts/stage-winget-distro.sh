#!/usr/bin/env bash
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

set -euo pipefail

usage() {
  echo "Usage: stage-winget-distro.sh <camel-version> <candidate-number> <winget-zip> [work-directory]" 1>&2
  exit 2
}

VERSION=${1:-}
CANDIDATE=${2:-}
ZIP=${3:-}
WORK_DIR=${4:-/tmp/camel-winget-release}
DIST_DEV_REPO="https://dist.apache.org/repos/dist/dev/camel/apache-camel"

[ -n "$VERSION" ] && [ -n "$CANDIDATE" ] && [ -n "$ZIP" ] || usage
case "$VERSION" in
  *-SNAPSHOT) echo "Error: refusing to stage snapshot version '$VERSION'." 1>&2; exit 2 ;;
esac
case "$CANDIDATE" in
  ''|*[!0-9]*) echo "Error: candidate number must be a positive integer." 1>&2; exit 2 ;;
esac
if [ "$CANDIDATE" -lt 1 ]; then
  echo "Error: candidate number must be a positive integer." 1>&2
  exit 2
fi

FILE_NAME="camel-launcher-$VERSION-winget-bin.zip"
if [ "$(basename -- "$ZIP")" != "$FILE_NAME" ]; then
  echo "Error: expected WinGet ZIP filename '$FILE_NAME'." 1>&2
  exit 2
fi
if [ ! -f "$ZIP" ]; then
  echo "Error: WinGet ZIP not found: $ZIP" 1>&2
  exit 1
fi

command -v svn >/dev/null 2>&1 || { echo "Error: svn is required." 1>&2; exit 1; }
command -v gpg >/dev/null 2>&1 || { echo "Error: gpg is required." 1>&2; exit 1; }
command -v sha512sum >/dev/null 2>&1 || { echo "Error: sha512sum is required." 1>&2; exit 1; }

SVN_DIR="$WORK_DIR/dist-dev"
CANDIDATE_NAME="$VERSION-rc$CANDIDATE"
CANDIDATE_DIR="$SVN_DIR/$CANDIDATE_NAME"
if [ -e "$CANDIDATE_DIR" ]; then
  echo "Error: candidate working directory already exists: $CANDIDATE_DIR" 1>&2
  exit 1
fi

mkdir -p "$WORK_DIR"
svn checkout --depth immediates "$DIST_DEV_REPO" "$SVN_DIR"
mkdir "$CANDIDATE_DIR"
cp -p "$ZIP" "$CANDIDATE_DIR/$FILE_NAME"
# Signs with gpg's default key, matching maven-gpg-plugin's behaviour for the rest of the
# release. Release managers holding more than one key select theirs with CAMEL_GPG_KEY, the
# equivalent of maven-gpg-plugin's gpg.keyname.
GPG_KEY_ARGS=()
if [ -n "${CAMEL_GPG_KEY:-}" ]; then
  GPG_KEY_ARGS=(--local-user "$CAMEL_GPG_KEY")
fi
gpg --batch --verbose --armor --detach-sign "${GPG_KEY_ARGS[@]}" \
  --output "$CANDIDATE_DIR/$FILE_NAME.asc" "$CANDIDATE_DIR/$FILE_NAME"
(
  cd "$CANDIDATE_DIR"
  sha512sum "$FILE_NAME" > "$FILE_NAME.sha512"
)
(
  cd "$SVN_DIR"
  svn add "$CANDIDATE_NAME"
)

echo "WinGet candidate prepared, but not committed. Review it before upload:"
echo "cd $SVN_DIR"
echo "svn status"
echo "svn commit -m \"Apache Camel $VERSION WinGet RC$CANDIDATE\""
echo "Candidate URL after commit: $DIST_DEV_REPO/$CANDIDATE_NAME/"
echo
# release-distro.sh requires this digest to promote the candidate, so it has to travel with the
# vote: the RC number alone cannot distinguish an approved candidate from a superseded one.
echo "Include this digest in the vote email; release-distro.sh requires it to promote the candidate:"
echo "  SHA-512 ($FILE_NAME): $(awk '{print $1}' "$CANDIDATE_DIR/$FILE_NAME.sha512")"
