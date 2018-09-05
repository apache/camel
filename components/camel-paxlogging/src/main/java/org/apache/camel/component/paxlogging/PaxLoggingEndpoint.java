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
package org.apache.camel.component.paxlogging;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * The paxlogging component can be used in an OSGi environment to receive PaxLogging events and process them.
 */
@UriEndpoint(firstVersion = "2.6.0", scheme = "paxlogging", title = "OSGi PAX Logging", syntax = "paxlogging:appender",
    consumerOnly = true, consumerClass = PaxLoggingConsumer.class, label = "monitoring")
public class PaxLoggingEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private final String appender;

    public PaxLoggingEndpoint(String uri, PaxLoggingComponent component, String appender) {
        super(uri, component);
        this.appender = appender;
    }

    /**
     * Appender is the name of the pax appender that need to be configured in the PaxLogging service configuration.
     */
    public String getAppender() {
        return appender;
    }

    /**
     * @deprecated use {@link #getAppender()}
     */
    @Deprecated
    public String getName() {
        return getAppender();
    }

    public PaxLoggingComponent getComponent() {
        return (PaxLoggingComponent) super.getComponent();
    }

    public Producer createProducer() throws Exception {
        throw new RuntimeCamelException("Cannot produce to a PaxLoggingEndpoint: " + getEndpointUri());
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        PaxLoggingConsumer answer = new PaxLoggingConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }
}
