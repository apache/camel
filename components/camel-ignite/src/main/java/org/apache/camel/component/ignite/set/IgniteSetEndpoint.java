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
package org.apache.camel.component.ignite.set;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ignite.AbstractIgniteEndpoint;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;
import org.apache.ignite.configuration.CollectionConfiguration;

import static org.apache.camel.component.ignite.IgniteConstants.SCHEME_SET;

/**
 * Interact with <a href="https://apacheignite.readme.io/docs/queue-and-set">Ignite Set data structures</a>.
 *
 * This endpoint only supports producers.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = SCHEME_SET, title = "Ignite Sets", syntax = "ignite-set:name",
             category = { Category.CACHE, Category.CLUSTERING }, producerOnly = true, headersClass = IgniteConstants.class)
public class IgniteSetEndpoint extends AbstractIgniteEndpoint {

    @UriPath
    @Metadata(required = true)
    private String name;

    @UriParam(label = "producer")
    private CollectionConfiguration configuration = new CollectionConfiguration();

    @UriParam(label = "producer")
    private IgniteSetOperation operation;

    public IgniteSetEndpoint(String endpointUri, String remaining, Map<String, Object> parameters,
                             IgniteSetComponent igniteComponent) {
        super(endpointUri, igniteComponent);
        name = remaining;

        // Set the configuration values.
        if (!parameters.containsKey("configuration")) {
            Map<String, Object> configProps = PropertiesHelper.extractProperties(parameters, "config.");
            PropertyBindingSupport.bindProperties(this.getCamelContext(), parameters, configProps);
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
     */
    public String getName() {
        return name;
    }

    /**
     * The set name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the collection configuration. Default: empty configuration.
     */
    public CollectionConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The collection configuration. Default: empty configuration.
     * <p>
     * You can also conveniently set inner properties by using <tt>configuration.xyz=123</tt> options.
     */
    public void setConfiguration(CollectionConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Gets the set operation to perform.
     */
    public IgniteSetOperation getOperation() {
        return operation;
    }

    /**
     * The operation to invoke on the Ignite Set. Superseded by the IgniteConstants.IGNITE_SETS_OPERATION header in the
     * IN message. Possible values: CONTAINS, ADD, SIZE, REMOVE, ITERATOR, CLEAR, RETAIN_ALL, ARRAY.The set operation to
     * perform.
     */
    public void setOperation(IgniteSetOperation operation) {
        this.operation = operation;
    }

}
