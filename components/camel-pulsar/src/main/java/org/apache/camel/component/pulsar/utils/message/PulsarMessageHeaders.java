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
package org.apache.camel.component.pulsar.utils.message;

public interface PulsarMessageHeaders {

    String PROPERTIES = "properties";
    String PRODUCER_NAME = "producer_name";
    String SEQUENCE_ID = "sequence_id";
    String PUBLISH_TIME = "publish_time";
    String MESSAGE_ID = "message_id";
    String EVENT_TIME = "event_time";
    String KEY = "key";
    String KEY_BYTES = "key_bytes";
    String TOPIC_NAME = "topic_name";
    String MESSAGE_RECEIPT = "message_receipt";
    String KEY_OUT = "CamelPulsarProducerMessageKey";
    String PROPERTIES_OUT = "CamelPulsarProducerMessageProperties";
    String EVENT_TIME_OUT = "CamelPulsarProducerMessageEventTime";
}
