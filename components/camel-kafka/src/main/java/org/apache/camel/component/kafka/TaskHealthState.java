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

import java.util.Properties;

import org.apache.camel.util.TimeUtils;
import org.apache.kafka.clients.consumer.ConsumerConfig;

/*
 * This one is used to avoid exposing methods from this class to the health checker. Fields and methods used
 * used to build an instance of this class, must be made thread-safe (i.e.: most importantly, read fields
 * should be marked as volatile).
 */
public class TaskHealthState {
    private final boolean ready;
    private final boolean isTerminated;
    private final boolean isRecoverable;
    private final Exception lastError;
    private final String clientId;

    private final String bootstrapServers;

    private final long currentBackoffInterval;

    private final Properties clientProperties;

    public TaskHealthState(boolean ready, boolean isTerminated, boolean isRecoverable, Exception lastError, String clientId,
                           long currentBackoffInterval, Properties clientProperties) {
        this.ready = ready;
        this.isTerminated = isTerminated;
        this.isRecoverable = isRecoverable;
        this.lastError = lastError;
        this.clientId = clientId;
        this.currentBackoffInterval = currentBackoffInterval;
        this.clientProperties = clientProperties;
        this.bootstrapServers = clientProperties.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isTerminated() {
        return isTerminated;
    }

    public boolean isRecoverable() {
        return isRecoverable;
    }

    public Exception getLastError() {
        return lastError;
    }

    public String getClientId() {
        return clientId;
    }

    public long getCurrentBackoffInterval() {
        return currentBackoffInterval;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getGroupId() {
        return clientProperties.getProperty(ConsumerConfig.GROUP_ID_CONFIG);
    }

    public String buildStateMessage() {
        String msg = "KafkaConsumer is not ready";
        if (isTerminated()) {
            msg += " (gave up recovering and terminated the kafka consumer; restart route or application to recover).";
        } else if (isRecoverable()) {
            String time = TimeUtils.printDuration(getCurrentBackoffInterval(), true);
            msg += " (recovery in progress using " + time + " intervals).";
        }

        if (lastError != null) {
            msg += " - Error: " + extractRootCause(lastError).getMessage();
        }

        return msg;
    }

    private Throwable extractRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

}
