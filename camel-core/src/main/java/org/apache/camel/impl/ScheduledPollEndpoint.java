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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;

/**
 * A base class for {@link org.apache.camel.Endpoint} which creates a {@link ScheduledPollConsumer}
 *
 * @version 
 */
public abstract class ScheduledPollEndpoint extends DefaultEndpoint {

    protected ScheduledPollEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Deprecated
    protected ScheduledPollEndpoint(String endpointUri, CamelContext context) {
        super(endpointUri, context);
    }

    @Deprecated
    protected ScheduledPollEndpoint(String endpointUri) {
        super(endpointUri);
    }

    protected ScheduledPollEndpoint() {
    }

    public void configureProperties(Map<String, Object> options) {
        super.configureProperties(options);
        configureScheduledPollConsumerProperties(options, getConsumerProperties());
    }

    private void configureScheduledPollConsumerProperties(Map<String, Object> options, Map<String, Object> consumerProperties) {
        // special for scheduled poll consumers as we want to allow end users to configure its options
        // from the URI parameters without the consumer. prefix
        Object startScheduler = options.remove("startScheduler");
        Object initialDelay = options.remove("initialDelay");
        Object delay = options.remove("delay");
        Object timeUnit = options.remove("timeUnit");
        Object useFixedDelay = options.remove("useFixedDelay");
        Object pollStrategy = options.remove("pollStrategy");
        Object runLoggingLevel = options.remove("runLoggingLevel");
        Object sendEmptyMessageWhenIdle = options.remove("sendEmptyMessageWhenIdle");
        Object greedy = options.remove("greedy");
        Object scheduledExecutorService  = options.remove("scheduledExecutorService");
        boolean setConsumerProperties = false;
        
        // the following is split into two if statements to satisfy the checkstyle max complexity constraint
        if (initialDelay != null || delay != null || timeUnit != null || useFixedDelay != null || pollStrategy != null) {
            setConsumerProperties = true;
        }
        if (runLoggingLevel != null || startScheduler != null || sendEmptyMessageWhenIdle != null || greedy != null || scheduledExecutorService != null) {
            setConsumerProperties = true;
        }
        
        if (setConsumerProperties) {
        
            if (consumerProperties == null) {
                consumerProperties = new HashMap<String, Object>();
            }
            if (initialDelay != null) {
                consumerProperties.put("initialDelay", initialDelay);
            }
            if (startScheduler != null) {
                consumerProperties.put("startScheduler", startScheduler);
            }
            if (delay != null) {
                consumerProperties.put("delay", delay);
            }
            if (timeUnit != null) {
                consumerProperties.put("timeUnit", timeUnit);
            }
            if (useFixedDelay != null) {
                consumerProperties.put("useFixedDelay", useFixedDelay);
            }
            if (pollStrategy != null) {
                consumerProperties.put("pollStrategy", pollStrategy);
            }
            if (runLoggingLevel != null) {
                consumerProperties.put("runLoggingLevel", runLoggingLevel);
            }
            if (sendEmptyMessageWhenIdle != null) {
                consumerProperties.put("sendEmptyMessageWhenIdle", sendEmptyMessageWhenIdle);
            }
            if (greedy != null) {
                consumerProperties.put("greedy", greedy);
            }
            if (scheduledExecutorService != null) {
                consumerProperties.put("scheduledExecutorService", scheduledExecutorService);
            }
        }
    }
    
}
