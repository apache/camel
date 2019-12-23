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
package org.apache.camel;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.extension.ComponentExtension;
import org.apache.camel.spi.PropertyConfigurer;

/**
 * A <a href="http://camel.apache.org/component.html">component</a> is
 * a factory of {@link Endpoint} objects.
 */
public interface Component extends CamelContextAware, Service {

    /**
     * Attempt to resolve an endpoint for the given URI if the component is
     * capable of handling the URI.
     * <p/>
     * See {@link #useRawUri()} for controlling whether the passed in uri
     * should be as-is (raw), or encoded (default).
     * 
     * @param uri the URI to create; either raw or encoded (default)
     * @return a newly created {@link Endpoint} or null if this component cannot create
     *         {@link Endpoint} instances using the given uri
     * @throws Exception is thrown if error creating the endpoint
     * @see #useRawUri()
     */
    Endpoint createEndpoint(String uri) throws Exception;

    /**
     * Attempt to resolve an endpoint for the given URI if the component is
     * capable of handling the URI.
     * <p/>
     * See {@link #useRawUri()} for controlling whether the passed in uri
     * should be as-is (raw), or encoded (default).
     *
     * @param uri the URI to create; either raw or encoded (default)
     * @param parameters the parameters for the endpoint
     * @return a newly created {@link Endpoint} or null if this component cannot create
     *         {@link Endpoint} instances using the given uri
     * @throws Exception is thrown if error creating the endpoint
     * @see #useRawUri()
     */
    Endpoint createEndpoint(String uri, Map<String, Object> parameters) throws Exception;

    /**
     * Whether to use raw or encoded uri, when creating endpoints.
     * <p/>
     * <b>Notice:</b> When using raw uris, then the parameter values is raw as well.
     *
     * @return <tt>true</tt> to use raw uris, <tt>false</tt> to use encoded uris (default).
     *
     * @since Camel 2.11.0
     */
    boolean useRawUri();

    /**
     * Gets the component {@link PropertyConfigurer}.
     *
     * @return the configurer, or <tt>null</tt> if the component does not support using property configurer.
     */
    default PropertyConfigurer getComponentPropertyConfigurer() {
        return null;
    }

    /**
     * Gets the endpoint {@link PropertyConfigurer}.
     *
     * @return the configurer, or <tt>null</tt> if the endpoint does not support using property configurer.
     */
    default PropertyConfigurer getEndpointPropertyConfigurer() {
        return null;
    }

    /**
     * Gets a list of supported extensions.
     *
     * @return the list of extensions.
     */
    default Collection<Class<? extends ComponentExtension>> getSupportedExtensions() {
        return Collections.emptyList();
    }

    /**
     * Gets the extension of the given type.
     *
     * @param extensionType tye type of the extensions
     * @return an optional extension
     */
    default <T extends ComponentExtension> Optional<T> getExtension(Class<T> extensionType) {
        return Optional.empty();
    }

    /**
     * Set the {@link Component} context if the component is an instance of {@link ComponentAware}.
     */
    static <T> T trySetComponent(T object, Component component) {
        if (object instanceof ComponentAware) {
            ((ComponentAware) object).setComponent(component);
        }

        return object;
    }

}
