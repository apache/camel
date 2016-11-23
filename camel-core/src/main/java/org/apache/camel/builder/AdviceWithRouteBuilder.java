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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.InterceptSendToMockEndpointStrategy;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link RouteBuilder} which has extended capabilities when using
 * the <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
 * <p/>
 * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
 * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is involved.
 * The Camel team plan for Camel 3.0 to support this as internal refactorings in the routing engine is needed to support this properly.
 *
 * @see org.apache.camel.model.RouteDefinition#adviceWith(org.apache.camel.CamelContext, RouteBuilder)
 */
public abstract class AdviceWithRouteBuilder extends RouteBuilder {

    private RouteDefinition originalRoute;
    private final List<AdviceWithTask> adviceWithTasks = new ArrayList<AdviceWithTask>();

    /**
     * Sets the original route which we advice.
     *
     * @param originalRoute the original route we advice.
     */
    public void setOriginalRoute(RouteDefinition originalRoute) {
        this.originalRoute = originalRoute;
    }

    /**
     * Gets the original route we advice.
     *
     * @return the original route.
     */
    public RouteDefinition getOriginalRoute() {
        return originalRoute;
    }

    /**
     * Gets a list of additional tasks to execute after the {@link #configure()} method has been executed
     * during the advice process.
     *
     * @return a list of additional {@link AdviceWithTask} tasks to be executed during the advice process.
     */
    public List<AdviceWithTask> getAdviceWithTasks() {
        return adviceWithTasks;
    }

    /**
     * Mock all endpoints in the route.
     *
     * @throws Exception can be thrown if error occurred
     */
    public void mockEndpoints() throws Exception {
        getContext().addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(null));
    }

    /**
     * Mock all endpoints matching the given pattern.
     *
     * @param pattern the pattern(s).
     * @throws Exception can be thrown if error occurred
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(org.apache.camel.CamelContext, String, String)
     */
    public void mockEndpoints(String... pattern) throws Exception {
        for (String s : pattern) {
            getContext().addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(s));
        }
    }

    /**
     * Mock all endpoints matching the given pattern, and <b>skips</b> sending to the original endpoint (detour messages).
     *
     * @param pattern the pattern(s).
     * @throws Exception can be thrown if error occurred
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(org.apache.camel.CamelContext, String, String)
     */
    public void mockEndpointsAndSkip(String... pattern) throws Exception {
        for (String s : pattern) {
            getContext().addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(s, true));
        }
    }

    /**
     * Replaces the route from endpoint with a new uri
     *
     * @param uri uri of the new endpoint
     */
    public void replaceFromWith(String uri) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        getAdviceWithTasks().add(AdviceWithTasks.replaceFromWith(originalRoute, uri));
    }

    /**
     * Replaces the route from endpoint with a new endpoint
     *
     * @param endpoint the new endpoint
     */
    public void replaceFromWith(Endpoint endpoint) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        getAdviceWithTasks().add(AdviceWithTasks.replaceFrom(originalRoute, endpoint));
    }

    /**
     * Weaves by matching id of the nodes in the route.
     * <p/>
     * Uses the {@link org.apache.camel.util.EndpointHelper#matchPattern(String, String)} matching algorithm.
     *
     * @param pattern the pattern
     * @return the builder
     * @see org.apache.camel.util.EndpointHelper#matchPattern(String, String)
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveById(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, pattern, null, null, null);
    }

    /**
     * Weaves by matching the to string representation of the nodes in the route.
     * <p/>
     * Uses the {@link org.apache.camel.util.EndpointHelper#matchPattern(String, String)} matching algorithm.
     *
     * @param pattern the pattern
     * @return the builder
     * @see org.apache.camel.util.EndpointHelper#matchPattern(String, String)
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveByToString(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, null, pattern, null, null);
    }

    /**
     * Weaves by matching sending to endpoints with the given uri of the nodes in the route.
     * <p/>
     * Uses the {@link org.apache.camel.util.EndpointHelper#matchPattern(String, String)} matching algorithm.
     *
     * @param pattern the pattern
     * @return the builder
     * @see org.apache.camel.util.EndpointHelper#matchPattern(String, String)
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveByToUri(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, null, null, pattern, null);
    }

    /**
     * Weaves by matching type of the nodes in the route.
     *
     * @param type the processor type
     * @return the builder
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveByType(Class<T> type) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, null, null, null, type);
    }

    /**
     * Weaves by adding the nodes to the start of the route.
     *
     * @return the builder
     */
    public <T extends ProcessorDefinition<?>> ProcessorDefinition<?> weaveAddFirst() {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, "*", null, null, null).selectFirst().before();
    }

    /**
     * Weaves by adding the nodes to the end of the route.
     *
     * @return the builder
     */
    public <T extends ProcessorDefinition<?>> ProcessorDefinition<?> weaveAddLast() {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, "*", null, null, null).maxDeep(1).selectLast().after();
    }

}
