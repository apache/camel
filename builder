#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
set -e

# This script gives you a reproducible build enviroment, since the enviroment is containerizedL
# Example usage: ./builder mvn clean install -Dquickly

docker build . -t apache-camel-builder

# Run the builder container as the current user, and mount his home dirs
# so that host .m2 repos can be reused and provide access to release gpg keys and such.
docker run --rm -it \
    --user "`id -u`:`id -g`" \
    -v "`pwd`:/src" \
    -v "${HOME}:/home/default" \
    -v "${HOME}:${HOME}" \
    apache-camel-builder $*