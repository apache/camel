/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import org.apache.camel.catalog.CamelCatalog;

/**
 * A component which is based from a Camel Connector.
 */
public interface ConnectorComponent extends Component {

    /**
     * Adds a new option to the existing map of options
     *
     * @param options  the existing options
     * @param name     the name of the option
     * @param value    the value of the option
     */
    void addConnectorOption(Map<String, String> options, String name, String value);

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
     * Gets the connector component name (component scheme)
     */
    String getComponentName();

    /**
     * Gets the camel-connector JSon file.
     */
    String getCamelConnectorJSon();

}
