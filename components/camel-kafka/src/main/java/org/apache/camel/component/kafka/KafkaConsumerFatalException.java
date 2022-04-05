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
package org.apache.camel.component.kafka;

/**
 * A fatal exception such as the kafka consumer is not able to be created and/or subscribed to the kafka brokers within
 * a given backoff period, leading to camel-kafka giving up and terminating the kafka consumer thread, meaning that the
 * kafka consumer will not try to recover. To recover requires either to restart the Camel route, or the application.
 */
public class KafkaConsumerFatalException extends RuntimeException {

    public KafkaConsumerFatalException(String message, Throwable cause) {
        super(message, cause);
    }
}
