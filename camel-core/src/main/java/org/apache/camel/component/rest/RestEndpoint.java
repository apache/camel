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
package org.apache.camel.component.rest;

import java.util.Map;
import java.util.Set;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

@UriEndpoint(scheme = "rest")
public class RestEndpoint extends DefaultEndpoint {

    @UriParam
    private String verb;
    @UriParam
    private String path;
    @UriParam
    private String consumes;
    @UriParam
    private String produces;
    @UriParam
    private String componentName;

    private Map<String, Object> parameters;

    public RestEndpoint(String endpointUri, RestComponent component) {
        super(endpointUri, component);
    }

    @Override
    public RestComponent getComponent() {
        return (RestComponent) super.getComponent();
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

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
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
        RestConsumerFactory factory = null;

        if (getComponentName() != null) {
            Object comp = getCamelContext().getRegistry().lookupByName(getComponentName());
            if (comp != null && comp instanceof RestConsumerFactory) {
                factory = (RestConsumerFactory) comp;
            } else {
                comp = getCamelContext().getComponent(getComponentName());
                if (comp != null && comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                }
            }

            if (factory == null) {
                if (comp != null) {
                    throw new IllegalArgumentException("Component " + getComponentName() + " is not a RestConsumerFactory");
                } else {
                    throw new NoSuchBeanException(getComponentName(), RestConsumerFactory.class.getName());
                }
            }
        }

        // try all components
        if (factory == null) {
            for (String name : getCamelContext().getComponentNames()) {
                Component comp = getCamelContext().getComponent(name);
                if (comp != null && comp instanceof RestConsumerFactory) {
                    factory = (RestConsumerFactory) comp;
                    break;
                }
            }
        }

        // lookup in registry
        if (factory == null) {
            Set<RestConsumerFactory> factories = getCamelContext().getRegistry().findByType(RestConsumerFactory.class);
            if (factories != null && factories.size() == 1) {
                factory = factories.iterator().next();
            }
        }

        if (factory != null) {
            Consumer consumer = factory.createConsumer(getCamelContext(), processor, getVerb(), getPath(), getConsumes(), getProduces(), getParameters());
            configureConsumer(consumer);
            return consumer;
        } else {
            throw new IllegalStateException("Cannot find RestConsumerFactory in Registry or as a Component to use");
        }
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
