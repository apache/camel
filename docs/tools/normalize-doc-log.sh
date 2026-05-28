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

# Normalize a gulp or mojo doc-build log into a sorted, comparable stream.
# Strips timestamps/durations, harmonizes wording, and discards lifecycle noise.
#
# Reads from stdin, writes to stdout. Use:
#
#   tools/normalize-doc-log.sh < gulp.log  > gulp.norm
#   tools/normalize-doc-log.sh < mojo.log  > mojo.norm
#   diff gulp.norm mojo.norm

set -euo pipefail

sed -E \
    -e 's/^\[INFO\] //' \
    -e 's/^\[[0-9]{2}:[0-9]{2}:[0-9]{2}\] //' \
    -e 's/ after [0-9]+(\.[0-9]+)?[[:space:]]*m?s$//' \
    -e "s/^Starting '[^']+'\\.\\.\\.$//" \
    -e "s/^Finished '[^']+'$//" \
    -e 's/symlinked /linked /' \
    -e 's/gulp-inject ([0-9]+) files? into ([^-]+)-nav\.adoc\.template\.?/generated \2-nav with \1 entries/' \
    -e 's/^[[:space:]]*→[[:space:]]*//' \
    -e 's/^[[:space:]]+//' \
| grep -E '^(linked|generated|prepare-doc-symlinks)' \
| LC_ALL=C sort -u
