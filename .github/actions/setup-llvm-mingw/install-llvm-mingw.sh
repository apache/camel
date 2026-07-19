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

#
# Installs a pinned, SHA256-verified release of llvm-mingw and prints the
# absolute path to its bin/ directory on stdout. Single source of truth for the
# pinned version and hash, shared by the GitHub Actions composite action
# (.github/actions/setup-llvm-mingw/action.yml) and Jenkinsfile.deploy.
#
# Usage: install-llvm-mingw.sh <install-dir>
#
# If the toolchain is already present under <install-dir> the download is
# skipped, so a persistent cache directory makes repeated runs cheap.
#

set -eo pipefail

VERSION='20260616'
SHA256='534b92e067b22a6b4441f48ae9240a3341b17825d04d577eab0cf85c44b4deda'
TARBALL="llvm-mingw-${VERSION}-ucrt-ubuntu-22.04-x86_64.tar.xz"

INSTALL_DIR="${1:?usage: install-llvm-mingw.sh <install-dir>}"
DEST="${INSTALL_DIR}/llvm-mingw-${VERSION}-ucrt-ubuntu-22.04-x86_64"

if [ ! -x "${DEST}/bin/x86_64-w64-mingw32-clang" ]; then
    mkdir -p "$INSTALL_DIR"
    curl -fsSL -o "${INSTALL_DIR}/${TARBALL}" \
        "https://github.com/mstorsjo/llvm-mingw/releases/download/${VERSION}/${TARBALL}"
    echo "${SHA256}  ${INSTALL_DIR}/${TARBALL}" | sha256sum -c -
    tar -xf "${INSTALL_DIR}/${TARBALL}" -C "$INSTALL_DIR"
    rm -f "${INSTALL_DIR}/${TARBALL}"
fi

printf '%s\n' "${DEST}/bin"
