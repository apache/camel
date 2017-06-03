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
package org.apache.camel.commands;

import java.util.List;
import java.util.Map;

/**
 * CamelController interface defines the expected behaviors to manipulate Camel resources (context, route, etc).
 */
public interface CamelController {

    /**
     * Gets information about a given Camel context by the given name.
     *
     * @param name the Camel context name.
     * @return a list of key/value pairs with CamelContext information
     * @throws java.lang.Exception can be thrown
     */
    Map<String, Object> getCamelContextInformation(String name) throws Exception;

    /**
     * Get the list of Camel context.
     *
     * @return a list of key/value pairs with CamelContext information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getCamelContexts() throws Exception;

    /**
     * Get the list of Camel context filter by reg ex.
     *
     * @param filter           the filter which supports * and ? as wildcards
     * @return a list of key/value pairs with CamelContext information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getCamelContexts(String filter) throws Exception;

    /**
     * Returns detailed CamelContext and route statistics as XML identified by a ID and a Camel context.
     *
     * @param camelContextName  the Camel context.
     * @param fullStats         whether to include verbose stats
     * @param includeProcessors whether to embed per processor stats from the route
     * @return the CamelContext statistics as XML
     * @throws java.lang.Exception can be thrown
     */
    String getCamelContextStatsAsXml(String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception;

    /**
     * Browses the inflight exchanges
     *
     * @param camelContextName        the Camel context.
     * @param route                   the Camel route ID
     * @param limit                   maximum number of exchanges to return
     * @param sortByLongestDuration   <tt>true</tt> to sort by longest duration, <tt>false</tt> to sort by exchange id
     * @return a list of key/value pairs with inflight exchange information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, Object>> browseInflightExchanges(String camelContextName, String route, int limit, boolean sortByLongestDuration) throws Exception;

    /**
     * Starts the given Camel context.
     *
     * @param camelContextName the Camel context.
     * @throws java.lang.Exception can be thrown
     */
    void startContext(String camelContextName) throws Exception;

    /**
     * Stops the given Camel context.
     *
     * @param camelContextName the Camel context.
     * @throws java.lang.Exception can be thrown
     */
    void stopContext(String camelContextName) throws Exception;

    /**
     * Suspends the given Camel context.
     *
     * @param camelContextName the Camel context.
     * @throws java.lang.Exception can be thrown
     */
    void suspendContext(String camelContextName) throws Exception;

    /**
     * Resumes the given Camel context.
     *
     * @param camelContextName the Camel context.
     * @throws java.lang.Exception can be thrown
     */
    void resumeContext(String camelContextName) throws Exception;

    /**
     * Get all routes. If Camel context name is null, all routes from all contexts are listed.
     *
     * @param camelContextName the Camel context name. If null, all contexts are considered.
     * @return a list of key/value pairs with routes information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getRoutes(String camelContextName) throws Exception;

    /**
     * Get all routes filtered by the regex.
     *
     * @param camelContextName the Camel context name. If null, all contexts are considered.
     * @param filter           the filter which supports * and ? as wildcards
     * @return a list of key/value pairs with routes information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getRoutes(String camelContextName, String filter) throws Exception;

    /**
     * Reset all the route stats for the given Camel context
     *
     * @param camelContextName the Camel context.
     * @throws java.lang.Exception can be thrown
     */
    void resetRouteStats(String camelContextName) throws Exception;

    /**
     * Starts the given route
     *
     * @param camelContextName the Camel context.
     * @param routeId          the route ID.
     * @throws java.lang.Exception can be thrown
     */
    void startRoute(String camelContextName, String routeId) throws Exception;

    /**
     * Stops the given route
     *
     * @param camelContextName the Camel context.
     * @param routeId          the route ID.
     * @throws java.lang.Exception can be thrown
     */
    void stopRoute(String camelContextName, String routeId) throws Exception;

    /**
     * Suspends the given route
     *
     * @param camelContextName the Camel context.
     * @param routeId          the route ID.
     * @throws java.lang.Exception can be thrown
     */
    void suspendRoute(String camelContextName, String routeId) throws Exception;

    /**
     * Resumes the given route
     *
     * @param camelContextName the Camel context.
     * @param routeId          the route ID.
     * @throws java.lang.Exception can be thrown
     */
    void resumeRoute(String camelContextName, String routeId) throws Exception;

    /**
     * Return the definition of a route as XML identified by a ID and a Camel context.
     *
     * @param routeId          the route ID.
     * @param camelContextName the Camel context.
     * @return the route model as XML
     * @throws java.lang.Exception can be thrown
     */
    String getRouteModelAsXml(String routeId, String camelContextName) throws Exception;

    /**
     * Returns detailed route statistics as XML identified by a ID and a Camel context.
     *
     * @param routeId           the route ID.
     * @param camelContextName  the Camel context.
     * @param fullStats         whether to include verbose stats
     * @param includeProcessors whether to embed per processor stats from the route
     * @return the route statistics as XML
     * @throws java.lang.Exception can be thrown
     */
    String getRouteStatsAsXml(String routeId, String camelContextName, boolean fullStats, boolean includeProcessors) throws Exception;

    /**
     * Return the endpoints
     *
     * @param camelContextName the Camel context.
     * @return a list of key/value pairs with endpoint information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getEndpoints(String camelContextName) throws Exception;

    /**
     * Return endpoint runtime statistics
     *
     * @param camelContextName the Camel context
     * @return a list of key/value pairs with endpoint runtime statistics
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getEndpointRuntimeStatistics(String camelContextName) throws Exception;

    /**
     * Return the definition of the REST services as XML for the given Camel context.
     *
     * @param camelContextName the Camel context.
     * @return the REST model as xml
     * @throws java.lang.Exception can be thrown
     */
    String getRestModelAsXml(String camelContextName) throws Exception;

    /**
     * Return the REST services API documentation as JSon (requires camel-swagger-java on classpath)
     *
     * @param camelContextName the Camel context.
     * @return the REST API documentation as JSon
     * @throws java.lang.Exception can be thrown
     */
    String getRestApiDocAsJson(String camelContextName) throws Exception;

    /**
     * Return the REST services for the given Camel context.
     *
     * @param camelContextName the Camel context.
     * @return a list of key/value pairs with REST information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getRestServices(String camelContextName) throws Exception;

    /**
     * Explains an endpoint uri
     *
     * @param camelContextName the Camel context.
     * @param uri              the endpoint uri
     * @param allOptions       whether to explain all options, or only the explicit configured options from the uri
     * @return a JSON schema with explanation of the options
     * @throws java.lang.Exception can be thrown
     */
    String explainEndpointAsJSon(String camelContextName, String uri, boolean allOptions) throws Exception;

    /**
     * Explains an EIP
     *
     * @param camelContextName the Camel context.
     * @param nameOrId         the name of the EIP ({@link org.apache.camel.NamedNode#getShortName()} or a node id to refer to a specific node from the routes.
     * @param allOptions       whether to explain all options, or only the explicit configured options from the uri
     * @return a JSON schema with explanation of the options
     * @throws java.lang.Exception can be thrown
     */
    String explainEipAsJSon(String camelContextName, String nameOrId, boolean allOptions) throws Exception;

    /**
     * Lists Components which are in use or available on the classpath and include information
     *
     * @param camelContextName the Camel context.
     * @return a list of key/value pairs with component information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> listComponents(String camelContextName) throws Exception;

    /**
     * Return the transformers
     *
     * @param camelContextName the Camel context.
     * @return a list of key/value pairs with transformer information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getTransformers(String camelContextName) throws Exception;

    /**
     * Return the validators
     *
     * @param camelContextName the Camel context.
     * @return a list of key/value pairs with validator information
     * @throws java.lang.Exception can be thrown
     */
    List<Map<String, String>> getValidators(String camelContextName) throws Exception;
}
