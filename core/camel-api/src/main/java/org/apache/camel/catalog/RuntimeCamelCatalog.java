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
package org.apache.camel.catalog;

import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.spi.SendDynamicAware;

/**
 * Runtime catalog which limited API needed by components that supports
 * {@link ComponentVerifierExtension} or {@link SendDynamicAware}.
 */
public interface RuntimeCamelCatalog extends StaticService, CamelContextAware {

    /**
     * Service factory key.
     */
    String FACTORY = "runtime-camelcatalog";

    /**
     * Returns the component information as JSon format.
     * <p/>
     * This API is needed by {@link ComponentVerifierExtension}.
     *
     * @param name the component name
     * @return component details in JSon
     */
    String componentJSonSchema(String name);

    /**
     * Parses the endpoint uri and constructs a key/value properties of each option.
     * <p/>
     * This API is needed by {@link SendDynamicAware}.
     *
     * @param uri  the endpoint uri
     * @return properties as key value pairs of each endpoint option
     */
    Map<String, String> endpointProperties(String uri) throws URISyntaxException;

    /**
     * Parses the endpoint uri and constructs a key/value properties of only the lenient properties (eg custom options)
     * <p/>
     * For example using the HTTP components to provide query parameters in the endpoint uri.
     * <p/>
     * This API is needed by {@link SendDynamicAware}.
     *
     * @param uri  the endpoint uri
     * @return properties as key value pairs of each lenient properties
     */
    Map<String, String> endpointLenientProperties(String uri) throws URISyntaxException;

    /**
     * Validates the properties for the given scheme against component and endpoint
     * <p/>
     * This API is needed by {@link ComponentVerifierExtension}.
     *
     * @param scheme  the endpoint scheme
     * @param properties  the endpoint properties
     * @return validation result
     */
    EndpointValidationResult validateProperties(String scheme, Map<String, String> properties);

    /**
     * Creates an endpoint uri in Java style from the information from the properties
     * <p/>
     * This API is needed by {@link SendDynamicAware}.
     *
     * @param scheme the endpoint schema
     * @param properties the properties as key value pairs
     * @param encode whether to URL encode the returned uri or not
     * @return the constructed endpoint uri
     * @throws java.net.URISyntaxException is thrown if there is encoding error
     */
    String asEndpointUri(String scheme, Map<String, String> properties, boolean encode) throws URISyntaxException;

}
