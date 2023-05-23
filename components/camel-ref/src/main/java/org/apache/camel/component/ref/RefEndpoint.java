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
package org.apache.camel.component.ref;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Route messages to an endpoint looked up dynamically by name in the Camel Registry.
 */
@UriEndpoint(firstVersion = "1.2.0", scheme = "ref", title = "Ref", syntax = "ref:name",
             category = { Category.CORE })
public class RefEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private volatile Endpoint endpoint;

    @UriPath
    @Metadata(required = true)
    private String name;

    public RefEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public String getName() {
        return name;
    }

    /**
     * Name of endpoint to lookup in the registry.
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Producer createProducer() throws Exception {
        return endpoint.createProducer();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return endpoint.createConsumer(processor);
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        if (endpoint == null) {
            // endpoint is mandatory
            endpoint = CamelContextHelper.mandatoryLookup(getCamelContext(), name, Endpoint.class);
            if (getCamelContext().getEndpoint(getEndpoint().getEndpointUri()) == null
                    || getCamelContext().getEndpoint(getEndpoint().getEndpointUri()) != endpoint) {
                getCamelContext().addEndpoint(getEndpoint().getEndpointUri(), endpoint);
            }
        }
        super.doInit();
    }

}
