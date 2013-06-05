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
package org.apache.camel.component.controlbus;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.CamelLogger;

/**
 * The control bus endpoint.
 */
public class ControlBusEndpoint extends DefaultEndpoint {

    private Language language;
    @UriParam
    private String routeId;
    @UriParam
    private String action;
    @UriParam
    private boolean async;
    @UriParam
    private LoggingLevel loggingLevel = LoggingLevel.INFO;

    public ControlBusEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        CamelLogger logger = new CamelLogger(ControlBusProducer.class.getName(), loggingLevel);
        return new ControlBusProducer(this, logger);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume from a ControlBusEndpoint: " + getEndpointUri());
    }

    @Override
    public boolean isSingleton() {
        // we dont want to be enlisted in JMX, so lets just be non-singleton
        return false;
    }

    @Override
    public ControlBusComponent getComponent() {
        return (ControlBusComponent) super.getComponent();
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public LoggingLevel getLoggingLevel() {
        return loggingLevel;
    }

    public void setLoggingLevel(LoggingLevel loggingLevel) {
        this.loggingLevel = loggingLevel;
    }
}
