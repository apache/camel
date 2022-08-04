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

package org.apache.camel.processor.resume.kafka;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.resume.Resumable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resume strategy that publishes offsets to a Kafka topic. This resume strategy is suitable for multi node
 * integrations. This is suitable, for instance, when using clusters with the master component.
 *
 * @param <K> the type of key
 */
@Deprecated
public class MultiNodeKafkaResumeStrategy<K extends Resumable> extends SingleNodeKafkaResumeStrategy<K> {
    private static final Logger LOG = LoggerFactory.getLogger(MultiNodeKafkaResumeStrategy.class);

    /**
     * Create a new instance of this class
     * 
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     */
    public MultiNodeKafkaResumeStrategy(KafkaResumeStrategyConfiguration resumeStrategyConfiguration) {
        // just in case users don't want to provide their own worker thread pool
        this(resumeStrategyConfiguration, Executors.newSingleThreadExecutor());
    }

    /**
     * Builds an instance of this class
     *
     * @param resumeStrategyConfiguration the configuration to use for this strategy instance
     * @param executorService             an executor service that will run a separate thread for periodically
     *                                    refreshing the offsets
     */

    public MultiNodeKafkaResumeStrategy(KafkaResumeStrategyConfiguration resumeStrategyConfiguration,
                                        ExecutorService executorService) {
        super(resumeStrategyConfiguration, executorService);
    }

}
