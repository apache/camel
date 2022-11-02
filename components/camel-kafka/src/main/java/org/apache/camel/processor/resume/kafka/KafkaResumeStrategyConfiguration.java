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

import java.time.Duration;
import java.util.Properties;

import org.apache.camel.resume.ResumeStrategyConfiguration;

/**
 * A configuration suitable for using with the {@link KafkaResumeStrategy} and any of its implementations
 */
public class KafkaResumeStrategyConfiguration extends ResumeStrategyConfiguration {
    private Properties producerProperties;
    private Properties consumerProperties;
    private String topic;
    private Duration maxInitializationDuration;
    private int maxInitializationRetries;

    public Properties getProducerProperties() {
        return producerProperties;
    }

    void setProducerProperties(Properties producerProperties) {
        assert producerProperties != null;

        this.producerProperties = producerProperties;
    }

    public Properties getConsumerProperties() {
        return consumerProperties;
    }

    void setConsumerProperties(Properties consumerProperties) {
        assert consumerProperties != null;

        this.consumerProperties = consumerProperties;
    }

    public String getTopic() {
        return topic;
    }

    void setTopic(String topic) {
        assert topic != null;

        this.topic = topic;
    }

    public Duration getMaxInitializationDuration() {
        return maxInitializationDuration;
    }

    public void setMaxInitializationDuration(Duration maxInitializationDuration) {
        this.maxInitializationDuration = maxInitializationDuration;
    }

    public int getMaxInitializationRetries() {
        return maxInitializationRetries;
    }

    public void setMaxInitializationRetries(int maxInitializationRetries) {
        if (maxInitializationRetries < 1) {
            throw new IllegalArgumentException("The maximum number of initialization retries must be equal or bigger than 1");
        }

        this.maxInitializationRetries = maxInitializationRetries;
    }

    @Override
    public String resumeStrategyService() {
        return "kafka-resume-strategy";
    }
}
