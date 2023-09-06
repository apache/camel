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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.ThrowingConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use this for using the advice with feature.
 *
 * Allows you to advice or enhance an existing route using a RouteBuilder style. For example you can add interceptors to
 * intercept sending outgoing messages to assert those messages are as expected.
 */
public final class AdviceWith {

    private static final Logger LOG = LoggerFactory.getLogger(AdviceWith.class);

    private AdviceWith() {
    }

    /**
     * Advices this route with the route builder using a lambda expression. It can be used as following:
     *
     * <pre>
     * AdviceWith.adviceWith(context, "myRoute", a -> a.weaveAddLast().to("mock:result"));
     * </pre>
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is
     * involved.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured from the route
     * builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param  camelContext the camel context
     * @param  routeId      either the route id as a string value, or <tt>null</tt> to chose the 1st route, or you can
     *                      specify a number for the n'th route, or provide the route definition instance directly as
     *                      well.
     * @param  builder      the advice with route builder
     * @return              a new route which is this route merged with the route builder
     * @throws Exception    can be thrown from the route builder
     */
    public static RouteDefinition adviceWith(
            CamelContext camelContext, Object routeId, ThrowingConsumer<AdviceWithRouteBuilder, Exception> builder)
            throws Exception {
        RouteDefinition rd = findRouteDefinition(camelContext, routeId);
        return doAdviceWith(rd, camelContext, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                builder.accept(this);
            }
        });
    }

    /**
     * Advices this route with the route builder using a lambda expression. It can be used as following:
     *
     * <pre>
     * AdviceWith.adviceWith(context, "myRoute", false, a -> a.weaveAddLast().to("mock:result"));
     * </pre>
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is
     * involved.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured from the route
     * builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param  camelContext the camel context
     * @param  routeId      either the route id as a string value, or <tt>null</tt> to chose the 1st route, or you can
     *                      specify a number for the n'th route, or provide the route definition instance directly as
     *                      well.
     * @param  logXml       whether to log the before and after advices routes as XML to the log (this can be turned off
     *                      to perform faster)
     * @param  builder      the advice with route builder
     * @return              a new route which is this route merged with the route builder
     * @throws Exception    can be thrown from the route builder
     */
    public static RouteDefinition adviceWith(
            CamelContext camelContext, Object routeId, boolean logXml,
            ThrowingConsumer<AdviceWithRouteBuilder, Exception> builder)
            throws Exception {
        RouteDefinition rd = findRouteDefinition(camelContext, routeId);
        return adviceWith(rd, camelContext, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                setLogRouteAsXml(logXml);
                builder.accept(this);
            }
        });
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is
     * involved. The Camel team plan for Camel 3.0 to support this as internal refactorings in the routing engine is
     * needed to support this properly.
     * <p/>
     * You can use a regular {@link RouteBuilder} but the specialized {@link AdviceWithRouteBuilder} has additional
     * features when using the advice with feature. We therefore suggest you to use the {@link AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured from the route
     * builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param  routeId      either the route id as a string value, or <tt>null</tt> to chose the 1st route, or you can
     *                      specify a number for the n'th route, or provide the route definition instance directly as
     *                      well.
     * @param  camelContext the camel context
     * @param  builder      the route builder
     * @return              a new route which is this route merged with the route builder
     * @throws Exception    can be thrown from the route builder
     * @see                 AdviceWithRouteBuilder
     */
    public static RouteDefinition adviceWith(Object routeId, CamelContext camelContext, RouteBuilder builder)
            throws Exception {
        RouteDefinition rd = findRouteDefinition(camelContext, routeId);
        return adviceWith(rd, camelContext, builder);
    }

    /**
     * Advices this route with the route builder.
     * <p/>
     * <b>Important:</b> It is recommended to only advice a given route once (you can of course advice multiple routes).
     * If you do it multiple times, then it may not work as expected, especially when any kind of error handling is
     * involved. The Camel team plan for Camel 3.0 to support this as internal refactorings in the routing engine is
     * needed to support this properly.
     * <p/>
     * You can use a regular {@link RouteBuilder} but the specialized {@link AdviceWithRouteBuilder} has additional
     * features when using the advice with feature. We therefore suggest you to use the {@link AdviceWithRouteBuilder}.
     * <p/>
     * The advice process will add the interceptors, on exceptions, on completions etc. configured from the route
     * builder to this route.
     * <p/>
     * This is mostly used for testing purpose to add interceptors and the likes to an existing route.
     * <p/>
     * Will stop and remove the old route from camel context and add and start this new advised route.
     *
     * @param  definition   the model definition
     * @param  camelContext the camel context
     * @param  builder      the route builder
     * @return              a new route which is this route merged with the route builder
     * @throws Exception    can be thrown from the route builder
     * @see                 AdviceWithRouteBuilder
     */
    public static RouteDefinition adviceWith(RouteDefinition definition, CamelContext camelContext, RouteBuilder builder)
            throws Exception {
        ObjectHelper.notNull(definition, "RouteDefinition");
        ObjectHelper.notNull(camelContext, "CamelContext");
        ObjectHelper.notNull(builder, "RouteBuilder");

        if (definition.getInput() == null) {
            throw new IllegalArgumentException("RouteDefinition has no input");
        }
        return doAdviceWith(definition, camelContext, builder);
    }

    private static RouteDefinition doAdviceWith(RouteDefinition definition, CamelContext camelContext, RouteBuilder builder)
            throws Exception {
        ObjectHelper.notNull(builder, "RouteBuilder");

        LOG.debug("AdviceWith route before: {}", definition);
        ExtendedCamelContext ecc = camelContext.getCamelContextExtension();
        Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);

        // inject this route into the advice route builder so it can access this route
        // and offer features to manipulate the route directly
        if (builder instanceof AdviceWithRouteBuilder) {
            AdviceWithRouteBuilder arb = (AdviceWithRouteBuilder) builder;
            arb.setOriginalRoute(definition);
        }

        // configure and prepare the routes from the builder
        RoutesDefinition routes = builder.configureRoutes(camelContext);

        // was logging enabled or disabled
        boolean logRoutesAsXml = true;
        if (builder instanceof AdviceWithRouteBuilder) {
            AdviceWithRouteBuilder arb = (AdviceWithRouteBuilder) builder;
            logRoutesAsXml = arb.isLogRouteAsXml();
        }

        LOG.debug("AdviceWith routes: {}", routes);

        // we can only advice with a route builder without any routes
        if (!builder.getRouteCollection().getRoutes().isEmpty()) {
            throw new IllegalArgumentException(
                    "You can only advice from a RouteBuilder which has no existing routes. Remove all routes from the route builder.");
        }
        // we can not advice with error handlers (if you added a new error
        // handler in the route builder)
        // we must check the error handler on builder is not the same as on
        // camel context, as that would be the default
        // context scoped error handler, in case no error handlers was
        // configured
        if (builder.getRouteCollection().getErrorHandlerFactory() != null
                && ecc.getErrorHandlerFactory() != builder.getRouteCollection().getErrorHandlerFactory()) {
            throw new IllegalArgumentException(
                    "You can not advice with error handlers. Remove the error handlers from the route builder.");
        }

        String beforeAsXml = null;
        final ModelToXMLDumper modelToXMLDumper = PluginHelper.getModelToXMLDumper(ecc);
        if (logRoutesAsXml && LOG.isInfoEnabled()) {
            try {
                beforeAsXml = modelToXMLDumper.dumpModelAsXml(camelContext, definition);
            } catch (Exception e) {
                // ignore, it may be due jaxb is not on classpath etc
            }
        }

        // stop and remove this existing route
        model.removeRouteDefinition(definition);

        // any advice with tasks we should execute first?
        if (builder instanceof AdviceWithRouteBuilder) {
            List<AdviceWithTask> tasks = ((AdviceWithRouteBuilder) builder).getAdviceWithTasks();
            for (AdviceWithTask task : tasks) {
                task.task();
            }
        }

        // now merge which also ensures that interceptors and the likes get
        // mixed in correctly as well
        RouteDefinition merged = routes.route(definition);

        // must re-prepare the merged route before it can be used
        merged.markUnprepared();
        routes.prepareRoute(merged);

        // add the new merged route
        model.getRouteDefinitions().add(0, merged);

        // log the merged route at info level to make it easier to end users to
        // spot any mistakes they may have made
        if (LOG.isInfoEnabled()) {
            LOG.info("AdviceWith route after: {}", merged);
        }

        if (beforeAsXml != null && logRoutesAsXml && LOG.isInfoEnabled()) {
            try {
                String afterAsXml = modelToXMLDumper.dumpModelAsXml(camelContext, merged);
                LOG.info("Adviced route before/after as XML:\n{}\n\n{}", beforeAsXml, afterAsXml);
            } catch (Exception e) {
                // ignore, it may be due jaxb is not on classpath etc
            }
        }

        // If the camel context is started then we start the route
        if (camelContext.isStarted()) {
            model.addRouteDefinition(merged);
        }
        return merged;
    }

    private static RouteDefinition findRouteDefinition(CamelContext camelContext, Object routeId) {
        ModelCamelContext mcc = (ModelCamelContext) camelContext;
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

}
