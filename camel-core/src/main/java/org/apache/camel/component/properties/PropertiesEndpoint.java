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
package org.apache.camel.component.properties;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The properties component is used for using property placeholders in endpoint uris.
 */
@UriEndpoint(firstVersion = "2.3.0", scheme = "properties", title = "Properties", syntax = "properties:key", label = "core,endpoint")
public class PropertiesEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private volatile Endpoint endpoint;

    @UriPath
    @Metadata(required = "true")
    private String key;
    @UriParam
    private String locations;
    @UriParam
    private boolean ignoreMissingLocation;

    public PropertiesEndpoint(String endpointUri, Endpoint delegate, Component component) {
        super(endpointUri, component);
        this.endpoint = delegate;
    }

    public String getKey() {
        return key;
    }

    /**
     * Property key to use as placeholder
     */
    public void setKey(String key) {
        this.key = key;
    }

    public String getLocations() {
        return locations;
    }

    /**
     * A list of locations to load properties. You can use comma to separate multiple locations.
     * This option will override any default locations and only use the locations from this option.
     */
    public void setLocations(String locations) {
        this.locations = locations;
    }

    public boolean isIgnoreMissingLocation() {
        return ignoreMissingLocation;
    }

    /**
     * Whether to silently ignore if a location cannot be located, such as a properties file not found.
     */
    public void setIgnoreMissingLocation(boolean ignoreMissingLocation) {
        this.ignoreMissingLocation = ignoreMissingLocation;
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
    public boolean isSingleton() {
        return true;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        // add the endpoint as a service so Camel can manage the endpoint and enlist the endpoint in JMX etc.
        getCamelContext().addService(endpoint);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
    }

}
