#!/bin/bash
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

# Records Camel TUI demos using TamboUI's built-in recording system.
#
# Usage:
#   ./record.sh <tape-name> <camel-run-args...>
#
# Examples:
#   ./record.sh camel-tui-hello --example=timer-log

set -e

TAPE_NAME="${1:?Usage: $0 <tape-name> <camel-run-args...>}"
shift
CAMEL_RUN_ARGS="$@"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TAPE_FILE="${SCRIPT_DIR}/${TAPE_NAME}.tape"

if [ ! -f "$TAPE_FILE" ]; then
    echo "Error: Tape file not found: $TAPE_FILE"
    exit 1
fi

echo "Starting Camel integration in background..."
camel run --background --background-wait=false $CAMEL_RUN_ARGS

echo "Recording ${TAPE_NAME} ..."
camel tui monitor --record="${TAPE_FILE}"

# Stop the background integration
echo "Stopping Camel integration..."
camel stop "*"

echo "Done. Output: ${TAPE_NAME}.cast"
echo "Play with: asciinema play ${TAPE_NAME}.cast"
echo "Convert to gif: agg ${TAPE_NAME}.cast ${TAPE_NAME}.gif"
