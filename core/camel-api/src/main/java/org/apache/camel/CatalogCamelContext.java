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

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;

/**
 * Catalog level interface for the {@link CamelContext}
 */
public interface CatalogCamelContext extends CamelContext {

    /**
     * Resolves a component's default name from its java type.
     * <p/>
     * A component may be used with a non default name such as <tt>activemq</tt>, <tt>wmq</tt> for the JMS component.
     * This method can resolve the default component name by its java type.
     *
     * @param javaType the FQN name of the java type
     * @return the default component name.
     */
    String resolveComponentDefaultName(String javaType);

    /**
     * Find information about all the Camel components available in the classpath and {@link org.apache.camel.spi.Registry}.
     *
     * @return a map with the component name, and value with component details.
     * @throws LoadPropertiesException is thrown if error during classpath discovery of the components
     * @throws IOException is thrown if error during classpath discovery of the components
     */
    Map<String, Properties> findComponents() throws LoadPropertiesException, IOException;

    /**
     * Find information about all the EIPs from camel-core.
     *
     * @return a map with node id, and value with EIP details.
     * @throws LoadPropertiesException is thrown if error during classpath discovery of the EIPs
     * @throws IOException is thrown if error during classpath discovery of the EIPs
     */
    Map<String, Properties> findEips() throws LoadPropertiesException, IOException;

    /**
     * Returns the JSON schema representation of the component and endpoint parameters for the given component name.
     *
     * @return the json or <tt>null</tt> if the component is <b>not</b> built with JSon schema support
     */
    String getComponentParameterJsonSchema(String componentName) throws IOException;

    /**
     * Returns the JSON schema representation of the {@link DataFormat} parameters for the given data format name.
     *
     * @return the json or <tt>null</tt> if the data format does not exist
     */
    String getDataFormatParameterJsonSchema(String dataFormatName) throws IOException;

    /**
     * Returns the JSON schema representation of the {@link Language} parameters for the given language name.
     *
     * @return the json or <tt>null</tt> if the language does not exist
     */
    String getLanguageParameterJsonSchema(String languageName) throws IOException;

    /**
     * Returns the JSON schema representation of the EIP parameters for the given EIP name.
     *
     * @return the json or <tt>null</tt> if the EIP does not exist
     */
    String getEipParameterJsonSchema(String eipName) throws IOException;

    /**
     * Returns a JSON schema representation of the EIP parameters for the given EIP by its id.
     *
     * @param nameOrId the name of the EIP ({@link NamedNode#getShortName()} or a node id to refer to a specific node from the routes.
     * @param includeAllOptions whether to include non configured options also (eg default options)
     * @return the json or <tt>null</tt> if the eipName or the id was not found
     */
    String explainEipJson(String nameOrId, boolean includeAllOptions);

    /**
     * Returns a JSON schema representation of the component parameters (not endpoint parameters) for the given component by its id.
     *
     * @param componentName the name of the component.
     * @param includeAllOptions whether to include non configured options also (eg default options)
     * @return the json or <tt>null</tt> if the component was not found
     */
    String explainComponentJson(String componentName, boolean includeAllOptions);

    /**
     * Returns a JSON schema representation of the component parameters (not endpoint parameters) for the given component by its id.
     *
     * @param dataFormat the data format instance.
     * @param includeAllOptions whether to include non configured options also (eg default options)
     * @return the json
     */
    String explainDataFormatJson(String dataFormatName, DataFormat dataFormat, boolean includeAllOptions);

    /**
     * Returns a JSON schema representation of the endpoint parameters for the given endpoint uri.
     *
     * @param uri the endpoint uri
     * @param includeAllOptions whether to include non configured options also (eg default options)
     * @return the json or <tt>null</tt> if uri parameters is invalid, or the component is <b>not</b> built with JSon schema support
     */
    String explainEndpointJson(String uri, boolean includeAllOptions);

    /**
     * Creates a JSON representation of all the <b>static</b> and <b>dynamic</b> configured endpoints defined in the given route(s).
     *
     * @param routeId for a particular route, or <tt>null</tt> for all routes
     * @return a JSON string
     */
    String createRouteStaticEndpointJson(String routeId);

    /**
     * Creates a JSON representation of all the <b>static</b> (and possible <b>dynamic</b>) configured endpoints defined in the given route(s).
     *
     * @param routeId for a particular route, or <tt>null</tt> for all routes
     * @param includeDynamic whether to include dynamic endpoints
     * @return a JSON string
     */
    String createRouteStaticEndpointJson(String routeId, boolean includeDynamic);

}
