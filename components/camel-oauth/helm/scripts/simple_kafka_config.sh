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


declare -A props

to_property_name() {
  key=$1
  echo ${key:6} | tr _ . | tr [:upper:] [:lower:]
}

pop_value() {
  key=$1
  fallback=$2

  if [ -z ${props[$key]+x} ] ; then
    echo $fallback
  else
    echo ${props[$key]}
  fi
  unset props[$key]
}

#
# This function allows you to encode as KAFKA_* env vars property names that contain characters invalid for env var names
# You can use:
#   KAFKA_LISTENER_NAME_CLIENT_SCRAM__2DSHA__2D256_SASL_JAAS_CONFIG=something
#
# Which will first be converted to:
#   KAFKA_LISTENER_NAME_CLIENT_SCRAM%2DSHA%2D256_SASL_JAAS_CONFIG=something
#
# And then to:
#   KAFKA_LISTENER_NAME_CLIENT_SCRAM-SHA-256_SASL_JAAS_CONFIG=something
#
unescape() {
  if [[ "$1" != "" ]]; then
    echo "$1" | sed -e "s@__@\%@g" -e "s@+@ @g;s@%@\\\\x@g" | xargs -0 printf "%b"
  fi
}

unset IFS
for var in $(compgen -e); do
  if [[ $var == KAFKA_* ]]; then

    case $var in
      KAFKA_DEBUG|KAFKA_OPTS|KAFKA_VERSION|KAFKA_HOME|KAFKA_CHECKSUM|KAFKA_LOG4J_OPTS|KAFKA_HEAP_OPTS|KAFKA_JVM_PERFORMANCE_OPTS|KAFKA_GC_LOG_OPTS|KAFKA_JMX_OPTS) ;;
      *)
        props[$(to_property_name $(unescape $var))]=${!var}
      ;;
    esac
  fi
done

#
# Generate output
#

if [[ "$1" == "--kraft" ]]; then
  #
  # Output kraft version of server.properties
  #
  echo "#"
  echo "# strimzi.properties (kraft)"
  echo "#"

  echo process.roles=`pop_value process.roles broker,controller`
  echo node.id=`pop_value node.id 1`
  echo log.dirs=`pop_value log.dirs /tmp/kraft-combined-logs`

elif [[ "$1" == "" ]]; then
  echo "#"
  echo "# strimzi.properties"
  echo "#"

  echo broker.id=`pop_value broker.id 0`
  echo log.dirs=`pop_value log.dirs /tmp/kafka-logs`
  echo group.initial.rebalance.delay.ms=`pop_value group.initial.rebalance.delay.ms 0`
else
  echo "Unsupported argument: $1"
  exit 1
fi

echo num.network.threads=`pop_value num.network.threads 3`
echo num.io.threads=`pop_value num.io.threads 8`
echo socket.send.buffer.bytes=`pop_value socket.send.buffer.bytes 102400`
echo socket.receive.buffer.bytes=`pop_value socket.receive.buffer.bytes 102400`
echo socket.request.max.bytes=`pop_value socket.request.max.bytes 104857600`
echo num.partitions=`pop_value num.partitions 1`
echo num.recovery.threads.per.data.dir=`pop_value num.recovery.threads.per.data.dir 1`
echo offsets.topic.replication.factor=`pop_value offsets.topic.replication.factor 1`
echo transaction.state.log.replication.factor=`pop_value transaction.state.log.replication.factor 1`
echo transaction.state.log.min.isr=`pop_value transaction.state.log.min.isr 1`
echo log.retention.hours=`pop_value log.retention.hours 168`
echo log.segment.bytes=`pop_value log.segment.bytes 1073741824`
echo log.retention.check.interval.ms=`pop_value log.retention.check.interval.ms 300000`

#
# Add what remains of KAFKA_* env vars
#
for K in "${!props[@]}"
do
  echo $K=`pop_value $K`
done

echo
