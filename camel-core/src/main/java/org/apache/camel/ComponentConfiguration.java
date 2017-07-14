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
package org.apache.camel;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.apache.camel.impl.ParameterConfiguration;

/**
 * Represents a set of configuration values for an endpoint URI which can be created from a URI string
 * or a base URI string and a set of parameter names and values.
 *
 * The configuration values can then be introspected, modified and converted back into a URI string
 * or Endpoint.
 *
 * For {@link org.apache.camel.impl.UriEndpointComponent} implementations created for Endpoints annotated with {@link org.apache.camel.spi.UriEndpoint} and the
 * associated annotations then all the parameter values can be introspected and the parameter values are converted to their
 * correct type.
 *
 * Other implementations keep all the types as String and there is no validation until you try to create
 * an Endpoint from the values.
 */
@Deprecated
public interface ComponentConfiguration {

    /**
     * Returns the base URI without any query parameters added
     */
    String getBaseUri();

    /**
     * Sets the base URI without any query parameters added
     */
    void setBaseUri(String baseUri);

    /**
     * Returns the current parameters of the configuration (usually encoded as <tt>?foo=bar&whatnot=something</tt> URI query parameters)
     */
    Map<String, Object> getParameters();

    /**
     * Sets the parameter values of this configuration
     */
    void setParameters(Map<String, Object> propertyValues);

    /**
     * Returns the parameter value for the given name
     *
     * @param name the name of the URI query parameter to get
     * @return the value of the parameter
     */
    Object getParameter(String name);

    /**
     * Sets the parameter value of the given name
     *
     * @param name  the name of the URI query parameter
     * @param value the new value of the parameter
     */
    void setParameter(String name, Object value);

    /**
     * Returns the URI string (without schema) with query parameters for the current
     * configuration which can then be used to create an {@link org.apache.camel.Endpoint}
     */
    String getUriString();

    /**
     * Sets the URI string (without schema but with optional query parameters)
     * which will update the {@link #getBaseUri()} and the {@link #getParameters()} values
     *
     * @param newValue the new URI string with query arguments
     */
    void setUriString(String newValue) throws URISyntaxException;

    /**
     * Returns the URI query parameter configuration for the given parameter name or null if it does not exist
     */
    ParameterConfiguration getParameterConfiguration(String name);

    /**
     * Returns the sorted map of all the parameter names to their {@link ParameterConfiguration} objects
     */
    SortedMap<String, ParameterConfiguration> getParameterConfigurationMap();

    /**
     * Converts the configuration into a URI and then looks up the endpoint in the {@link CamelContext}
     * which typically results in a new {@link Endpoint} instance being created.
     */
    Endpoint createEndpoint() throws Exception;

    /**
     * Applies the current set of parameters to the given endpoint instance.
     * <p/>
     * Note that typically parts of the URI are not injected into the Endpoint; this method purely
     *
     * @param endpoint the endpoint instance
     */
    void configureEndpoint(Endpoint endpoint);

    /**
     * Gets the named URI parameter value on the given endpoint
     *
     * @param endpoint the endpoint instance
     * @param name     the name of the URI query parameter
     * @return the value of the parameter
     * @throws RuntimeCamelException if the parameter name does not exist on the endpoint
     */
    Object getEndpointParameter(Endpoint endpoint, String name) throws RuntimeCamelException;

    /**
     * Sets the named URI query parameter value on the given endpoint
     *
     * @param endpoint the endpoint instance
     * @param name     the name of the URI query parameter
     * @param value    the new value of the URI query parameter
     * @throws RuntimeCamelException
     */
    void setEndpointParameter(Endpoint endpoint, String name, Object value) throws RuntimeCamelException;

    /**
     * A helper method for tools such as CLIs, IDEs or web tools that provides a completion list for Endpoint Paths
     * rather like bash tab completion or Karaf attribute or option completion handlers.
     *
     * So given the current configuration data, return a list of completions given the specified text.
     * e.g. return the files in a directory, the matching queues in a message broker, the database tables in a database component etc
     *
     * @param completionText the prefix text used to complete on (usually a matching bit of text)
     * @return a list of matches
     */
    List<String> completeEndpointPath(String completionText);

    /**
     * Creates a <a href="http://json-schema.org/">JSON schema</a> representation of the
     * configuration parameters for this endpoint and the types and validation rules.
     *
     * @return a JSON string which represents the JSON schema for this endpoints configuration parameters.
     */
    String createParameterJsonSchema();
}

