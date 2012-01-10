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
import org.apache.camel.Consumer;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;

/**
 * A base class for {@link org.apache.camel.Endpoint} which creates a {@link ScheduledPollConsumer}
 *
 * @version 
 */
public abstract class ScheduledPollEndpoint extends DefaultEndpoint {
    private Map<String, Object> consumerProperties;

    protected ScheduledPollEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    protected ScheduledPollEndpoint(String endpointUri, CamelContext context) {
        super(endpointUri, context);
    }

    protected ScheduledPollEndpoint(String endpointUri) {
        super(endpointUri);
    }

    protected ScheduledPollEndpoint() {
    }

    public Map<String, Object> getConsumerProperties() {
        return consumerProperties;
    }

    public void setConsumerProperties(Map<String, Object> consumerProperties) {
        this.consumerProperties = consumerProperties;
    }

    protected void configureConsumer(Consumer consumer) throws Exception {
        if (consumerProperties != null) {
            // use a defensive copy of the consumer properties as the methods below will remove the used properties
            // and in case we restart routes, we need access to the original consumer properties again
            Map<String, Object> copy = new HashMap<String, Object>(consumerProperties);

            // set reference properties first as they use # syntax that fools the regular properties setter
            EndpointHelper.setReferenceProperties(getCamelContext(), consumer, copy);
            EndpointHelper.setProperties(getCamelContext(), consumer, copy);
            if (!this.isLenientProperties() && copy.size() > 0) {
                throw new ResolveEndpointFailedException(this.getEndpointUri(), "There are " + copy.size()
                    + " parameters that couldn't be set on the endpoint consumer."
                    + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                    + " Unknown consumer parameters=[" + copy + "]");
            }
        }
    }

    public void configureProperties(Map<String, Object> options) {
        Map<String, Object> consumerProperties = IntrospectionSupport.extractProperties(options, "consumer.");
        if (consumerProperties != null) {
            setConsumerProperties(consumerProperties);
        }
        configureScheduledPollConsumerProperties(options, consumerProperties);
    }

    private void configureScheduledPollConsumerProperties(Map<String, Object> options, Map<String, Object> consumerProperties) {
        // special for scheduled poll consumers as we want to allow end users to configure its options
        // from the URI parameters without the consumer. prefix
        Object initialDelay = options.remove("initialDelay");
        Object delay = options.remove("delay");
        Object timeUnit = options.remove("timeUnit");
        Object useFixedDelay = options.remove("useFixedDelay");
        Object pollStrategy = options.remove("pollStrategy");
        Object runLoggingLevel = options.remove("runLoggingLevel");
        if (initialDelay != null || delay != null || timeUnit != null || useFixedDelay != null || pollStrategy != null || runLoggingLevel != null) {
            if (consumerProperties == null) {
                consumerProperties = new HashMap<String, Object>();
            }
            if (initialDelay != null) {
                consumerProperties.put("initialDelay", initialDelay);
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
        }
    }
}
