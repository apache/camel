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
package org.apache.camel.spi;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;

/**
 * Factory to create {@link org.apache.camel.Endpoint} URI string from a {@link Map} of parameters.
 *
 * Notice that this factory is only for creating an URL string and not {@link org.apache.camel.Endpoint} instances.
 */
public interface EndpointUriFactory extends CamelContextAware {

    /**
     * Checks whether this factory supports the given component name
     */
    boolean isEnabled(String scheme);

    /**
     * Assembles an endpoint uri for the given component name with the given parameters.
     *
     * @param  scheme     the component name
     * @param  properties endpoint options
     * @return            the constructed endpoint uri
     */
    default String buildUri(String scheme, Map<String, Object> properties) throws URISyntaxException {
        return buildUri(scheme, properties, true);
    }

    /**
     * Assembles an endpoint uri for the given component name with the given parameters.
     *
     * @param  scheme     the component name
     * @param  properties endpoint options
     * @param  encode     whether to URL encode the returned uri or not
     * @return            the constructed endpoint uri
     */
    String buildUri(String scheme, Map<String, Object> properties, boolean encode) throws URISyntaxException;

    /**
     * Returns all the names this endpoint supports.
     */
    Set<String> propertyNames();

    /**
     * Returns the names of the secret properties this endpoint supports.
     */
    Set<String> secretPropertyNames();

    /**
     * Returns the prefixes of the multi-valued properties this endpoint supports.
     */
    Set<String> multiValuePrefixes();

    /**
     * Whether the endpoint is lenient or not.
     *
     * @see Endpoint#isLenientProperties()
     */
    boolean isLenientProperties();

}
