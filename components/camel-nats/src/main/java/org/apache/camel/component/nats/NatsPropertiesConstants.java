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
    String NATS_PROPERTY_URI = "uri";
    String NATS_PROPERTY_VERBOSE = "verbose";
    String NATS_PROPERTY_PEDANTIC = "pedantic";
    String NATS_PROPERTY_RECONNECT = "reconnect";
    String NATS_PROPERTY_SSL = "ssl";
    String NATS_PROPERTY_MAX_RECONNECT_ATTEMPTS = "max_reconnect_attempts";
    String NATS_PROPERTY_RECONNECT_TIME_WAIT = "reconnect_time_wait";
    String NATS_PROPERTY_PING_INTERVAL = "ping_interval";
    String NATS_PROPERTY_DONT_RANDOMIZE_SERVERS = "dont_randomize_servers";
    String NATS_PROPERTY_QUEUE = "queue";
    String NATS_PROPERTY_MAX_MESSAGES = "max";
}
