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
 * Strategy to decide when a Kafka exception was thrown during polling, how to handle this. For example by re-connecting
 * and polling the same message again, by stopping the consumer (allows to re-balance and let another consumer try), or
 * to let Camel route the message as an exception which allows Camel error handling to handle the exception, or to
 * discard this message and poll the next message.
 */
public interface PollExceptionStrategy {

    /**
     * Controls how to handle the exception while polling from Kafka.
     *
     * @param  exception the caused exception which typically would be a {@link org.apache.kafka.common.KafkaException}
     * @return           how to handle the exception
     */
    PollOnError handleException(Exception exception);
}
