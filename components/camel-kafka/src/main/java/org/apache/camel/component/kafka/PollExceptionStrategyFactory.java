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

import org.apache.kafka.clients.consumer.Consumer;

/**
 * Factory to create a {@link PollExceptionStrategy} for a Kafka consumer fetch thread.
 * <p/>
 * Unlike setting a single {@link PollExceptionStrategy} on the component, which shares one instance across all consumer
 * threads, this factory is invoked each time a consumer fetch thread creates its Kafka consumer (on start and on every
 * reconnect), so each thread gets its own strategy bound to its current consumer. This allows a custom strategy to be
 * bound to its fetch records task, which is required by the built-in reconnect, stop, and bridge strategies to drive
 * the consumer (for example to request a reconnect). It therefore also works correctly when a single endpoint uses
 * multiple consumer threads (the <tt>consumersCount</tt> option).
 * <p/>
 * The factory may be called concurrently by several fetch threads and must be thread-safe. A strategy that keeps state,
 * such as an error counter, is recreated on every reconnect and does not retain that state across reconnects.
 */
@FunctionalInterface
public interface PollExceptionStrategyFactory {

    /**
     * Creates a new {@link PollExceptionStrategy} for the given Kafka consumer fetch thread.
     *
     * @param  recordFetcher the fetch records task that owns the created strategy
     * @param  endpoint      the endpoint the consumer belongs to
     * @param  consumer      the Kafka consumer used by the fetch thread
     * @return               a strategy to handle exceptions thrown while polling from Kafka
     */
    PollExceptionStrategy create(KafkaFetchRecords recordFetcher, KafkaEndpoint endpoint, Consumer<?, ?> consumer);
}
