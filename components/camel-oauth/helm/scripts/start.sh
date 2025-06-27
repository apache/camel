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

set -e

# Get the directory where the start.sh script is located
SCRIPT_DIR=$(dirname "$(realpath "$0")")

# source ${SCRIPT_DIR}/functions.sh

if [ "$SERVER_PROPERTIES_FILE" == "" ]; then
  echo "Generating a new strimzi.properties file using ENV vars"
  ${SCRIPT_DIR}/simple_kafka_config.sh $1 | tee /tmp/strimzi.properties
else
  echo "Using provided server.properties file: $SERVER_PROPERTIES_FILE"
  cp $SERVER_PROPERTIES_FILE /tmp/strimzi.properties
fi

if [[ "$1" == "--kraft" ]]; then
  KAFKA_CLUSTER_ID="$(/opt/kafka/bin/kafka-storage.sh random-uuid)"
  /opt/kafka/bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c /tmp/strimzi.properties
fi

# add Strimzi kafka-oauth-* jars and their dependencies to classpath
# export CLASSPATH="/opt/kafka/libs/strimzi/*:$CLASSPATH"

exec /opt/kafka/bin/kafka-server-start.sh /tmp/strimzi.properties
