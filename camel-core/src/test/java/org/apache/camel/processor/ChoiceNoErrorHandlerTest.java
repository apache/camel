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
package org.apache.camel.processor;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.EventDrivenConsumerRoute;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.interceptor.StreamCachingInterceptor;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ProcessorDefinitionHelper;

/**
 * Unit test based on user forum problem - CAMEL-1463.
 *
 * @version $Revision$
 */
public class ChoiceNoErrorHandlerTest extends ContextTestSupport {

    private static boolean jmx = true;

    @Override
    protected void setUp() throws Exception {
        // we must enable/disable JMX in this setUp
        if (jmx) {
            enableJMX();
            jmx = false;
        } else {
            disableJMX();
        }
        super.setUp();
    }

    public void testChoiceNoErrorHandler() throws Exception {
        doTest();
    }

    public void testChoiceNoErrorHandlerJMXDisabled() throws Exception {
        doTest();
    }

    private void doTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "bar");

        assertMockEndpointsSatisfied();

        // there should be no error handlers and no stream cache
        for (RouteDefinition route : context.getRouteDefinitions()) {
            assertNull("StreamCache should be disabled", route.getStreamCaching());

            ErrorHandler error = ProcessorDefinitionHelper.findFirstTypeInOutputs(route.getOutputs(), DeadLetterChannel.class);
            assertNull("There should be no error handler", error);
        }

        // there should be no error handlers and no stream cache
        for (Route route : context.getRoutes()) {
            if (route instanceof EventDrivenConsumerRoute) {
                EventDrivenConsumerRoute consumer = (EventDrivenConsumerRoute) route;

                StreamCachingInterceptor cache = findProceesorInRoute(consumer.getProcessor(), StreamCachingInterceptor.class);
                assertNull("There should be no stream cache found: " + cache, cache);

                ErrorHandler error = findProceesorInRoute(consumer.getProcessor(), ErrorHandler.class);
                assertNull("There should be no error handler found: " + error, error);
            }
        }
    }

    private <T> T findProceesorInRoute(Processor route, Class<T> type) {
        if (route == null) {
            return null;
        }
        
        if (type.isInstance(route)) {
            return type.cast(route);
        }

        try {
            Method m = route.getClass().getMethod("getProcessor");

            Processor child = (Processor) ObjectHelper.invokeMethod(m, route);
            // look its children
            return findProceesorInRoute(child, type);
        } catch (NoSuchMethodException e) {
            // ignore
        }

        try {
            Method m = route.getClass().getMethod("getProcessors");

            // look its children
            Collection<Processor> children = (Collection<Processor>) ObjectHelper.invokeMethod(m, route);
            for (Processor child : children) {
                T out = findProceesorInRoute(child, type);
                if (out != null) {
                    return out;
                }
            }
        } catch (NoSuchMethodException e) {
            // ignore
        }

        try {
            Method m = route.getClass().getMethod("getFilters");

            // look its children
            List<FilterProcessor> children = (List<FilterProcessor>) ObjectHelper.invokeMethod(m, route);
            for (Processor child : children) {
                T out = findProceesorInRoute(child, type);
                if (out != null) {
                    return out;
                }
            }
        } catch (NoSuchMethodException e) {
            // ignore
        }

        try {
            Method m = route.getClass().getMethod("getOtherwise");

            Processor child = (Processor) ObjectHelper.invokeMethod(m, route);
            // look its children
            return findProceesorInRoute(child, type);
        } catch (NoSuchMethodException e) {
            // ignore
        }

        return null;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(noErrorHandler());

                from("direct:start")
                        .choice()
                        .when(header("foo").isEqualTo("bar")).to("direct:end")
                        .otherwise().end();

                from("direct:end")
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                assertEquals("Hello World", exchange.getIn().getBody(String.class));
                            }
                        }).to("mock:result");
            }
        };
    }
}
