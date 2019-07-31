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

import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Service;

/**
 * A registry of all REST services running within the {@link org.apache.camel.CamelContext} which have been defined and created
 * using the <a href="http://camel.apache.org/rest-dsl">Rest DSL</a>.
 */
public interface RestRegistry extends Service {

    /**
     * Details about the REST service
     */
    interface RestService {

        /**
         * Gets the consumer of the REST service
         */
        Consumer getConsumer();

        /**
         * Gets the state of the REST service (started, stopped, etc)
         */
        String getState();

        /**
         * Gets the absolute url to the REST service (baseUrl + uriTemplate)
         */
        String getUrl();

        /**
         * Gets the base url to the REST service
         */
        String getBaseUrl();

        /**
         * Gets the base path to the REST service
         */
        String getBasePath();

        /**
         * Gets the uri template
         */
        String getUriTemplate();

        /**
         * Gets the HTTP method (GET, POST, PUT etc)
         */
        String getMethod();

        /**
         * Optional details about what media-types the REST service accepts
         */
        String getConsumes();

        /**
         * Optional details about what media-types the REST service returns
         */
        String getProduces();

        /**
         * Optional detail about input binding to a FQN class name.
         * <p/>
         * If the input accepts a list, then <tt>List&lt;class name&gt;</tt> is enclosed the name.
         */
        String getInType();

        /**
         * Optional detail about output binding to a FQN class name.
         * <p/>
         * If the output accepts a list, then <tt>List&lt;class name&gt;</tt> is enclosed the name.
         */
        String getOutType();

        /**
         * Gets the id of the route this rest service will be using.
         */
        String getRouteId();

        /**
         * Optional description about this rest service.
         */
        String getDescription();

    }

    /**
     * Adds a new REST service to the registry.
     *
     * @param consumer    the consumer
     * @param url         the absolute url of the REST service
     * @param baseUrl     the base url of the REST service
     * @param basePath    the base path
     * @param uriTemplate the uri template
     * @param method      the HTTP method
     * @param consumes    optional details about what media-types the REST service accepts
     * @param produces    optional details about what media-types the REST service returns
     * @param inType      optional detail input binding to a FQN class name
     * @param outType     optional detail output binding to a FQN class name
     * @param routeId     the id of the route this rest service will be using
     * @param description optional description about the service
     */
    void addRestService(Consumer consumer, String url, String baseUrl, String basePath, String uriTemplate, String method, String consumes, String produces,
                        String inType, String outType, String routeId, String description);

    /**
     * Removes the REST service from the registry
     *
     * @param consumer  the consumer
     */
    void removeRestService(Consumer consumer);

    /**
     * List all REST services from this registry.
     *
     * @return all the REST services
     */
    List<RestService> listAllRestServices();

    /**
     * Number of rest services in the registry.
     *
     * @return number of rest services in the registry.
     */
    int size();

    /**
     * Outputs the Rest services API documentation in JSon (requires camel-swagger-java on classpath)
     *
     * @return  the API docs in JSon, or <tt>null</tt> if camel-swagger-java is not on classpath
     */
    String apiDocAsJson();

}
