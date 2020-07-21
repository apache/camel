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
package org.apache.camel.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.impl.engine.InterceptSendToMockEndpointStrategy;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.ThrowingConsumer;

/**
 * A {@link RouteBuilder} which has extended capabilities when using the
 * <a href="http://camel.apache.org/advicewith.html">advice with</a> feature.
 * <p/>
 * <b>Important:</b> It is recommended to only advice a given route once (you
 * can of course advice multiple routes). If you do it multiple times, then it
 * may not work as expected, especially when any kind of error handling is
 * involved.
 */
public abstract class AdviceWithRouteBuilder extends RouteBuilder {

    private RouteDefinition originalRoute;
    private final List<AdviceWithTask> adviceWithTasks = new ArrayList<>();
    private boolean logRouteAsXml = true;

    public AdviceWithRouteBuilder() {
    }

    public AdviceWithRouteBuilder(CamelContext context) {
        super(context);
    }

    /**
     * Advices this route with the route builder using a lambda expression. It
     * can be used as following:
     * 
     * <pre>
     * AdviceWithRouteBuilder.adviceWith(context, "myRoute", a ->
     *     a.weaveAddLast().to("mock:result");
     * </pre>
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once
     * (you can of course advice multiple routes). If you do it multiple times,
     * then it may not work as expected, especially when any kind of error
     * handling is involved. The Camel team plan for Camel 3.0 to support this
     * as internal refactorings in the routing engine is needed to support this
     * properly.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on
     * completions etc. configured from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes
     * to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start
     * this new advised route.
     *
     * @param camelContext the camel context
     * @param routeId either the route id as a string value, or <tt>null</tt> to
     *            chose the 1st route, or you can specify a number for the n'th
     *            route, or provide the route definition instance directly as well.
     * @param builder the advice with route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     */
    public static RouteDefinition adviceWith(CamelContext camelContext, Object routeId, ThrowingConsumer<AdviceWithRouteBuilder, Exception> builder) throws Exception {
        RouteDefinition rd = findRouteDefinition(camelContext, routeId);
        return camelContext.adapt(ModelCamelContext.class).adviceWith(rd, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                builder.accept(this);
            }
        });
    }

    /**
     * Advices this route with the route builder using a lambda expression. It
     * can be used as following:
     *
     * <pre>
     * AdviceWithRouteBuilder.adviceWith(context, "myRoute", false, a ->
     *     a.weaveAddLast().to("mock:result");
     * </pre>
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once
     * (you can of course advice multiple routes). If you do it multiple times,
     * then it may not work as expected, especially when any kind of error
     * handling is involved. The Camel team plan for Camel 3.0 to support this
     * as internal refactorings in the routing engine is needed to support this
     * properly.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on
     * completions etc. configured from the route builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes
     * to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start
     * this new advised route.
     *
     * @param camelContext the camel context
     * @param routeId either the route id as a string value, or <tt>null</tt> to
     *            chose the 1st route, or you can specify a number for the n'th
     *            route, or provide the route definition instance directly as well.
     * @param logXml whether to log the before and after advices routes as XML to the log (this can be turned off to perform faster)
     * @param builder the advice with route builder
     * @return a new route which is this route merged with the route builder
     * @throws Exception can be thrown from the route builder
     */
    public static RouteDefinition adviceWith(CamelContext camelContext, Object routeId, boolean logXml, ThrowingConsumer<AdviceWithRouteBuilder, Exception> builder) throws Exception {
        RouteDefinition rd = findRouteDefinition(camelContext, routeId);

        return RouteReifier.adviceWith(rd, camelContext, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                setLogRouteAsXml(logXml);
                builder.accept(this);
            }
        });
    }

    protected static RouteDefinition findRouteDefinition(CamelContext camelContext, Object routeId) {
        ModelCamelContext mcc = camelContext.adapt(ModelCamelContext.class);
        if (mcc.getRouteDefinitions().isEmpty()) {
            throw new IllegalArgumentException("Cannot advice route as there are no routes");
        }

        RouteDefinition rd;
        if (routeId instanceof RouteDefinition) {
            rd = (RouteDefinition) routeId;
        } else {
            String id = mcc.getTypeConverter().convertTo(String.class, routeId);
            if (id != null) {
                rd = mcc.getRouteDefinition(id);
                if (rd == null) {
                    // okay it may be a number
                    Integer num = mcc.getTypeConverter().tryConvertTo(Integer.class, routeId);
                    if (num != null) {
                        rd = mcc.getRouteDefinitions().get(num);
                    }
                }
                if (rd == null) {
                    throw new IllegalArgumentException("Cannot advice route as route with id: " + routeId + " does not exists");
                }
            } else {
                // grab first route
                rd = mcc.getRouteDefinitions().get(0);
            }
        }
        return rd;
    }

    /**
     * Sets the original route to be adviced.
     *
     * @param originalRoute the original route.
     */
    public void setOriginalRoute(RouteDefinition originalRoute) {
        this.originalRoute = originalRoute;
    }

    /**
     * Gets the original route to be adviced.
     *
     * @return the original route.
     */
    public RouteDefinition getOriginalRoute() {
        return originalRoute;
    }

    /**
     * Whether to log the adviced routes before/after as XML. This is usable to
     * know how the route was adviced and changed. However marshalling the route
     * model to XML costs CPU resources and you can then turn this off by not
     * logging. This is default enabled.
     */
    public boolean isLogRouteAsXml() {
        return logRouteAsXml;
    }

    /**
     * Sets whether to log the adviced routes before/after as XML. This is
     * usable to know how the route was adviced and changed. However marshalling
     * the route model to XML costs CPU resources and you can then turn this off
     * by not logging. This is default enabled.
     */
    public void setLogRouteAsXml(boolean logRouteAsXml) {
        this.logRouteAsXml = logRouteAsXml;
    }

    /**
     * Gets a list of additional tasks to execute after the {@link #configure()}
     * method has been executed during the advice process.
     *
     * @return a list of additional {@link AdviceWithTask} tasks to be executed
     *         during the advice process.
     */
    public List<AdviceWithTask> getAdviceWithTasks() {
        return adviceWithTasks;
    }

    /**
     * Mock all endpoints.
     *
     * @throws Exception can be thrown if error occurred
     */
    public void mockEndpoints() throws Exception {
        getContext().adapt(ExtendedCamelContext.class).registerEndpointCallback(new InterceptSendToMockEndpointStrategy(null));
    }

    /**
     * Mock all endpoints matching the given pattern.
     *
     * @param pattern the pattern(s).
     * @throws Exception can be thrown if error occurred
     * @see EndpointHelper#matchEndpoint(org.apache.camel.CamelContext, String,
     *      String)
     */
    public void mockEndpoints(String... pattern) throws Exception {
        for (String s : pattern) {
            getContext().adapt(ExtendedCamelContext.class).registerEndpointCallback(new InterceptSendToMockEndpointStrategy(s));
        }
    }

    /**
     * Mock all endpoints matching the given pattern, and <b>skips</b> sending
     * to the original endpoint (detour messages).
     *
     * @param pattern the pattern(s).
     * @throws Exception can be thrown if error occurred
     * @see EndpointHelper#matchEndpoint(org.apache.camel.CamelContext, String,
     *      String)
     */
    public void mockEndpointsAndSkip(String... pattern) throws Exception {
        for (String s : pattern) {
            getContext().adapt(ExtendedCamelContext.class).registerEndpointCallback(new InterceptSendToMockEndpointStrategy(s, true));
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
     * Weaves by matching id of the nodes in the route (incl onException etc).
     * <p/>
     * Uses the {@link PatternHelper#matchPattern(String, String)} matching
     * algorithm.
     *
     * @param pattern the pattern
     * @return the builder
     * @see PatternHelper#matchPattern(String, String)
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveById(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<>(this, pattern, null, null, null);
    }

    /**
     * Weaves by matching the to string representation of the nodes in the route
     * (incl onException etc).
     * <p/>
     * Uses the {@link PatternHelper#matchPattern(String, String)} matching
     * algorithm.
     *
     * @param pattern the pattern
     * @return the builder
     * @see PatternHelper#matchPattern(String, String)
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveByToString(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<>(this, null, pattern, null, null);
    }

    /**
     * Weaves by matching sending to endpoints with the given uri of the nodes
     * in the route (incl onException etc).
     * <p/>
     * Uses the {@link PatternHelper#matchPattern(String, String)} matching
     * algorithm.
     *
     * @param pattern the pattern
     * @return the builder
     * @see PatternHelper#matchPattern(String, String)
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveByToUri(String pattern) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<>(this, null, null, pattern, null);
    }

    /**
     * Weaves by matching type of the nodes in the route (incl onException etc).
     *
     * @param type the processor type
     * @return the builder
     */
    public <T extends ProcessorDefinition<?>> AdviceWithBuilder<T> weaveByType(Class<T> type) {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<>(this, null, null, null, type);
    }

    /**
     * Weaves by adding the nodes to the start of the route (excl onException
     * etc).
     *
     * @return the builder
     */
    public <T extends ProcessorDefinition<?>> ProcessorDefinition<?> weaveAddFirst() {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, "*", null, null, null).selectFirst().before();
    }

    /**
     * Weaves by adding the nodes to the end of the route (excl onException
     * etc).
     *
     * @return the builder
     */
    public <T extends ProcessorDefinition<?>> ProcessorDefinition<?> weaveAddLast() {
        ObjectHelper.notNull(originalRoute, "originalRoute", this);
        return new AdviceWithBuilder<T>(this, "*", null, null, null).maxDeep(1).selectLast().after();
    }

}
