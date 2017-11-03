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
package org.apache.camel.component.connector;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.catalog.CamelCatalog;

/**
 * A component which is based from a Camel Connector.
 */
public interface ConnectorComponent extends Component {
    /**
     * Adds a new option to the connector's options.
     *
     * @param name     the name of the option
     * @param value    the value of the option
     */
    void addOption(String name, Object value);

    /**
     * Adds options to the connector's options.
     *
     * @param options  the options
     */
    void addOptions(Map<String, Object> options);

    /**
     * Creates the endpoint uri based on the options from the connector.
     *
     * @param scheme  the component name
     * @param options the options to use for creating the endpoint
     * @return the endpoint uri
     * @throws URISyntaxException is thrown if error creating the endpoint uri.
     */
    String createEndpointUri(String scheme, Map<String, String> options) throws URISyntaxException;

    /**
     * Gets the {@link CamelCatalog} which can be used by the connector to help create the component.
     */
    CamelCatalog getCamelCatalog();

    /**
     * Gets the connector name (title)
     */
    String getConnectorName();

    /**
     * Gets the connector component name
     */
    String getComponentName();

    /**
     * Gets the connector component scheme
     */
    String getComponentScheme();

    /**
     * Gets the camel-connector JSon file.
     */
    String getCamelConnectorJSon();

    /**
     * A set of additional component/endpoint options to use for the base component when creating connector endpoints.
     *
     * @deprecated use {@link #getOptions()} instead
     */
    @Deprecated
    default Map<String, Object> getComponentOptions() {
        return getOptions();
    }

    /**
     * A set of additional component/endpoint options to use for the base component when creating connector endpoints.
     */
    Map<String, Object> getOptions();

    /**
     * A set of additional component/endpoint options to use for the base component when creating connector endpoints.
     *
     * @deprecated use {@link #setOptions(Map)} instead
     */
    default void setComponentOptions(Map<String, Object> options) {
        setOptions(options);
    }

    /**
     * A set of additional component/endpoint options to use for the base component when creating connector endpoints.
     */
    void setOptions(Map<String, Object> options);

    /**
     * To perform custom processing before the producer is sending the message.
     */
    void setBeforeProducer(Processor processor);

    /**
     * Gets the processor used to perform custom processing before the producer is sending the message.
     */
    Processor getBeforeProducer();

    /**
     * To perform custom processing after the producer has sent the message and received any reply (if InOut).
     */
    void setAfterProducer(Processor processor);

    /**
     * Gets the processor used to perform custom processing after the producer has sent the message and received any reply (if InOut).
     */
    Processor getAfterProducer();

    /**
     * To perform custom processing when the consumer has just received a new incoming message.
     */
    void setBeforeConsumer(Processor processor);

    /**
     * Gets the processor used to perform custom processing when the consumer has just received a new incoming message.
     */
    Processor getBeforeConsumer();

    /**
     * To perform custom processing when the consumer is about to send back a reply message to the caller (if InOut).
     */
    void setAfterConsumer(Processor processor);

    /**
     * Gets the processor used to perform custom processing when the consumer is about to send back a reply message to the caller (if InOut).
     */
    Processor getAfterConsumer();
}
