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
package org.apache.camel.component.kafka.consumer.support;

import org.apache.camel.ResumeStrategy;
import org.apache.kafka.clients.consumer.Consumer;

/**
 * Defines a strategy for handling resume operations. Implementations can define different ways to handle how to resume
 * processing records.
 *
 * The resume runs in the scope of the Kafka Consumer thread and may run concurrently with other consumer instances when
 * the component is set up to use more than one of them. As such, implementations are responsible for ensuring the
 * thread-safety of the operations within the resume method.
 */
public interface KafkaConsumerResumeStrategy extends ResumeStrategy {
    void setConsumer(Consumer<?, ?> consumer);

    default void resume(KafkaResumable resumable) {

    }

    @Override
    default void start() {

    }

    @Override
    default void stop() {

    }
}
