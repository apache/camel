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
package org.apache.camel.component.kafka.consumer.errorhandler;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.kafka.KafkaEndpoint;
import org.apache.camel.component.kafka.KafkaFetchRecords;
import org.apache.camel.component.kafka.PollExceptionStrategy;
import org.apache.camel.component.kafka.PollExceptionStrategyFactory;
import org.apache.camel.component.kafka.PollOnError;
import org.apache.kafka.clients.consumer.Consumer;

/**
 * Default {@link PollExceptionStrategyFactory} that creates the built-in strategy selected by the endpoint's
 * {@link PollOnError} configuration.
 * <p/>
 * A subclass can override {@link #create(KafkaFetchRecords, KafkaEndpoint, Consumer)} and call
 * {@code super.create(...)} to wrap or decorate the built-in strategy.
 */
public class DefaultPollExceptionStrategyFactory implements PollExceptionStrategyFactory {

    @Override
    public PollExceptionStrategy create(KafkaFetchRecords recordFetcher, KafkaEndpoint endpoint, Consumer<?, ?> consumer) {
        PollOnError onError = endpoint.getConfiguration().getPollOnError();

        switch (onError) {
            case RETRY:
                return new RetryErrorStrategy();
            case RECONNECT:
                return new ReconnectErrorStrategy(recordFetcher);
            case ERROR_HANDLER:
                return new BridgeErrorStrategy(recordFetcher, consumer);
            case DISCARD:
                return new DiscardErrorStrategy(consumer);
            case STOP:
                return new StopErrorStrategy(recordFetcher);
            default:
                throw new RuntimeCamelException("The provided pollOnError strategy is invalid");
        }
    }
}
