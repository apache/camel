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
package org.apache.camel.component.pulsar.utils.message;

public class PulsarMessageHeaders {

    static final String PRODUCER_NAME = "producer_name";
    static final String SEQUENCE_ID = "sequence_id";
    static final String PUBLISH_TIME = "publish_time";
    public static final String PROPERTIES = "properties";
    static final String MESSAGE_ID = "message_id";
    static final String EVENT_TIME = "event_time";
    static final String KEY = "key";
    static final String KEY_BYTES = "key_bytes";
    static final String TOPIC_NAME = "topic_name";
}
