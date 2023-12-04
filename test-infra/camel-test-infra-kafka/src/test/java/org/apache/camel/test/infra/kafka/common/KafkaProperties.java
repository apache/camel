/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.test.infra.kafka.common;

public final class KafkaProperties {
    public static final String KAFKA_BOOTSTRAP_SERVERS = "kafka.bootstrap.servers";
    public static final String KAFKA_ZOOKEEPER_ADDRESS = "kafka.zookeeper.address";
    public static final String KAFKA_CONTAINER = "kafka.container";
    public static final String KAFKA3_CONTAINER = "kafka3.container";
    public static final String KAFKA2_CONTAINER = "kafka2.container";
    public static final String REDPANDA_CONTAINER = "itest.redpanda.container.image";
    public static final String STRIMZI_CONTAINER = "itest.strimzi.container.image";

    private KafkaProperties() {

    }
}
