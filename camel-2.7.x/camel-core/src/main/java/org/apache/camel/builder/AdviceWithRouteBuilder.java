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

import org.apache.camel.impl.InterceptSendToMockEndpointStrategy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link RouteBuilder} which has extended capabilities when using
 * the <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
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
     * @param pattern the pattern.
     * @throws Exception can be thrown if error occurred
     * @see org.apache.camel.util.EndpointHelper#matchEndpoint(String, String)
     */
    public void mockEndpoints(String pattern) throws Exception {
        getContext().addRegisterEndpointCallback(new InterceptSendToMockEndpointStrategy(pattern));
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
    public AdviceWithBuilder weaveById(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder(this, pattern, null);
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
    public AdviceWithBuilder weaveByToString(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder(this, null, pattern);
    }

}
