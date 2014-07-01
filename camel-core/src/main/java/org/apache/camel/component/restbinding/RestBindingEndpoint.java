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
package org.apache.camel.component.restbinding;

import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.RestBindingCapable;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(scheme = "rest-binding")
public class RestBindingEndpoint extends DefaultEndpoint {

    @UriParam
    private String verb;
    @UriParam
    private String path;
    @UriParam
    private String accept;

    private Map<String, Object> parameters;

    public RestBindingEndpoint(String endpointUri, RestBindingComponent component) {
        super(endpointUri, component);
    }

    @Override
    public RestBindingComponent getComponent() {
        return (RestBindingComponent) super.getComponent();
    }

    public String getVerb() {
        return verb;
    }

    public void setVerb(String verb) {
        this.verb = verb;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getAccept() {
        return accept;
    }

    public void setAccept(String accept) {
        this.accept = accept;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        // create the consumer directly from the component that supports rest binding
        // TODO: should we have a RestBindingConsumer class that delegates to the actual consumer?
        // TODO: what if there is 2+ RestBindingCapable components in the registry?
        RestBindingCapable component = getComponent().lookupRestBindingCapableComponent();
        if (component != null) {
            Consumer consumer = component.createConsumer(this, processor);
            configureConsumer(consumer);
            return consumer;
        } else {
            throw new IllegalStateException("There are no registered components in CamelContext that is RestBindingCapable");
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
