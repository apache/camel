/**
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
package org.apache.camel.component.nats;

public interface NatsPropertiesConstants {
    String NATS_PROPERTY_URL = "io.nats.client.url";
    String NATS_PROPERTY_VERBOSE = "io.nats.client.verbose";
    String NATS_PROPERTY_PEDANTIC = "io.nats.client.pedantic";
    String NATS_PROPERTY_RECONNECT = "io.nats.client.reconnect.allowed";
    String NATS_PROPERTY_SSL = "io.nats.client.secure";
    String NATS_PROPERTY_MAX_RECONNECT_ATTEMPTS = "io.nats.client.reconnect.max";
    String NATS_PROPERTY_RECONNECT_TIME_WAIT = "io.nats.client.reconnect.wait";
    String NATS_PROPERTY_PING_INTERVAL = "io.nats.client.pinginterval";
    String NATS_PROPERTY_DONT_RANDOMIZE_SERVERS = "io.nats.client.norandomize";
    String NATS_PROPERTY_QUEUE = "queue";
    String NATS_PROPERTY_MAX_MESSAGES = "max";
}
