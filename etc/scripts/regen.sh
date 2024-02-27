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

if [ "${BASH_VERSINFO[0]}" -lt 4 ]; then
    echo "Error: Bash 4 or higher is required to run this script, but found ${BASH_VERSINFO}"
    exit 1
fi

shopt -s globstar

CAMEL_DIR=$(cd `dirname "$0"`/../..; pwd)
cd $CAMEL_DIR

# Force clean
git clean -fdx
rm -Rf **/src/generated/

# Regenerate everything
./mvnw -Pfull,update-camel-releases -DskipTests package
# One additional pass to get the info for the 'others' jars
./mvnw -Pfull,update-camel-releases -DskipTests package -f catalog/camel-catalog

# Update links
find docs/components/modules/ROOT/examples/json -type l -delete
for json_file in components/**/src/generated/resources/META-INF/**/*.json ; do
    # Get relative path of json file
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        rel_path=$(realpath --relative-to=docs/components/modules/ROOT/examples/json $json_file)
    else 
        rel_path=$(grealpath --relative-to=docs/components/modules/ROOT/examples/json $json_file)
    fi
    # Create symbolic link in dir-b
    ln -sf $rel_path docs/components/modules/ROOT/examples/json/$(basename $json_file)
done

find core/camel-core-engine/src/main/docs/modules/eips/examples/json -type l -delete
for json_file in core/camel-core-model/src/generated/resources/META-INF/org/apache/camel/model/*.json ; do
    # Get relative path of json file
    if [[ "$OSTYPE" == "linux-gnu"* ]]; then
        rel_path=$(realpath --relative-to=core/camel-core-engine/src/main/docs/modules/eips/examples/json $json_file)
    else
        rel_path=$(grealpath --relative-to=core/camel-core-engine/src/main/docs/modules/eips/examples/json $json_file)
    fi
    # Create symbolic link in dir-b
    ln -sf $rel_path core/camel-core-engine/src/main/docs/modules/eips/examples/json/$(basename $json_file)
done
