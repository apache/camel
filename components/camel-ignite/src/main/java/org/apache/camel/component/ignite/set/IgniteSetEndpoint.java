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
package org.apache.camel.component.ignite.set;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.IgniteComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.ignite.configuration.CollectionConfiguration;

/**
 * Ignite Cache endpoint.
 */
@UriEndpoint(scheme = "ignite:set", title = "Ignite Sets", syntax = "ignite:set:[name]", label = "nosql,cache", producerOnly = true)
public class IgniteSetEndpoint extends AbstractIgniteEndpoint {

    @UriParam @Metadata(required = "true")
    private String name;

    @UriParam
    private CollectionConfiguration configuration = new CollectionConfiguration();

    @UriParam
    private IgniteSetOperation operation;

    public IgniteSetEndpoint(String endpointUri, URI remainingUri, Map<String, Object> parameters, IgniteComponent igniteComponent) throws Exception {
        super(endpointUri, igniteComponent);
        name = remainingUri.getHost();

        ObjectHelper.notNull(name, "Set name");

        // Set the configuration values.
        if (!parameters.containsKey("configuration")) {
            Map<String, Object> configProps = IntrospectionSupport.extractProperties(parameters, "config.");
            EndpointHelper.setReferenceProperties(this.getCamelContext(), configProps, parameters);
            EndpointHelper.setProperties(this.getCamelContext(), configProps, parameters);
        }

    }

    @Override
    public Producer createProducer() throws Exception {
        return new IgniteSetProducer(this, ignite().set(name, configuration));
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The Ignite Sets endpoint doesn't support consumers.");
    }

    /**
     * Gets the set name.
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the set name.
     * 
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the collection configuration. Default: empty configuration.
     * 
     * @return
     */
    public CollectionConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the collection configuration. Default: empty configuration.
     * <p>
     * You can also conveniently set inner properties by using <tt>configuration.xyz=123</tt> options.
     * 
     * @param configuration
     */
    public void setConfiguration(CollectionConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Gets the set operation to perform.
     * 
     * @return
     */
    public IgniteSetOperation getOperation() {
        return operation;
    }

    /**
     * Sets the set operation to perform.
     * 
     * @param operation
     */
    public void setOperation(IgniteSetOperation operation) {
        this.operation = operation;
    }

}
