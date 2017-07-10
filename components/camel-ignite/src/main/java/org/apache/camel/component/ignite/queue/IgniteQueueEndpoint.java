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
package org.apache.camel.component.ignite.queue;

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
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.ignite.IgniteQueue;
import org.apache.ignite.configuration.CollectionConfiguration;

/**
 * The Ignite Queue endpoint is one of camel-ignite endpoints which allows you to interact with
 * <a href="https://apacheignite.readme.io/docs/queue-and-set">Ignite Queue data structures</a>.
 * This endpoint only supports producers.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "ignite-queue", title = "Ignite Queues", syntax = "ignite-queue:name", label = "nosql,cache", producerOnly = true)
public class IgniteQueueEndpoint extends AbstractIgniteEndpoint {

    @UriPath @Metadata(required = "true")
    private String name;

    @UriParam(label = "producer")
    private int capacity;

    @UriParam(label = "producer")
    private CollectionConfiguration configuration = new CollectionConfiguration();

    @UriParam(label = "producer")
    private Long timeoutMillis;

    @UriParam(label = "producer")
    private IgniteQueueOperation operation;

    @Deprecated
    public IgniteQueueEndpoint(String endpointUri, URI remainingUri, Map<String, Object> parameters, IgniteComponent igniteComponent) throws Exception {
        super(endpointUri, igniteComponent);
        name = remainingUri.getHost();

        ObjectHelper.notNull(name, "Queue name");

        // Set the configuration values.
        if (!parameters.containsKey("configuration")) {
            Map<String, Object> configProps = IntrospectionSupport.extractProperties(parameters, "config.");
            EndpointHelper.setReferenceProperties(this.getCamelContext(), configProps, parameters);
            EndpointHelper.setProperties(this.getCamelContext(), configProps, parameters);
        }
    }

    public IgniteQueueEndpoint(String endpointUri, String remaining, Map<String, Object> parameters, IgniteQueueComponent igniteComponent) throws Exception {
        super(endpointUri, igniteComponent);
        name = remaining;

        ObjectHelper.notNull(name, "Queue name");

        // Set the configuration values.
        if (!parameters.containsKey("configuration")) {
            Map<String, Object> configProps = IntrospectionSupport.extractProperties(parameters, "config.");
            EndpointHelper.setReferenceProperties(this.getCamelContext(), configProps, parameters);
            EndpointHelper.setProperties(this.getCamelContext(), configProps, parameters);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        IgniteQueue<Object> queue = ignite().queue(name, capacity, configuration);

        return new IgniteQueueProducer(this, queue);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The Ignite Queue endpoint doesn't support consumers.");
    }

    /**
     * Gets the queue name.
     * 
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * The queue name.
     * 
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the queue operation to perform.
     * 
     * @return
     */
    public IgniteQueueOperation getOperation() {
        return operation;
    }

    /**
     * The operation to invoke on the Ignite Queue.
     * Superseded by the IgniteConstants.IGNITE_QUEUE_OPERATION header in the IN message.
     * Possible values: CONTAINS, ADD, SIZE, REMOVE, ITERATOR, CLEAR, RETAIN_ALL, ARRAY, DRAIN, ELEMENT, PEEK, OFFER, POLL, TAKE, PUT.
     * 
     * @param operation
     */
    public void setOperation(IgniteQueueOperation operation) {
        this.operation = operation;
    }

    /**
     * Gets the queue capacity. Default: non-bounded.
     * 
     * @return
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * The queue capacity. Default: non-bounded.
     * 
     * @param capacity
     */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
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
     * The collection configuration. Default: empty configuration.
     * <p>
     * You can also conveniently set inner properties by using <tt>configuration.xyz=123</tt> options.
     * 
     * @param configuration
     */
    public void setConfiguration(CollectionConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Gets the queue timeout in milliseconds. Default: no timeout.
     * 
     * @return
     */
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * The queue timeout in milliseconds. Default: no timeout.
     * 
     * @param timeoutMillis
     */
    public void setTimeoutMillis(Long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

}
