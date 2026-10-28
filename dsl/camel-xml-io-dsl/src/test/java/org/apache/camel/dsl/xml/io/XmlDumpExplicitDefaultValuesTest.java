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
package org.apache.camel.dsl.xml.io;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.seda.SedaEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.RestConsumerFactory;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.xml.LwModelToXMLDumper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route that is dumped as XML and loaded again must behave as the route it was dumped from, also when an option is
 * set to its documented default value, as an option that is not set takes the value of a parent level instead.
 */
class XmlDumpExplicitDefaultValuesTest {

    /** A rest consumer that receives from seda:verb-path, so the test can call a rest verb without an HTTP server. */
    public static class SedaRestConsumerFactory implements RestConsumerFactory {
        @Override
        public org.apache.camel.Consumer createConsumer(
                CamelContext camelContext, Processor processor, String verb, String basePath, String uriTemplate,
                String consumes, String produces, RestConfiguration configuration, Map<String, Object> parameters)
                throws Exception {
            String path = basePath + (uriTemplate != null ? uriTemplate : "");
            SedaEndpoint seda = camelContext.getEndpoint("seda:" + verb + path.replace('/', '-'), SedaEndpoint.class);
            return seda.createConsumer(processor);
        }
    }

    @Test
    void verbClientRequestValidationOffUnderRestConfiguration() throws Exception {
        String xml = """
                <camel>
                    <rest path="/api">
                        <post path="/notes" consumes="application/json" clientRequestValidation="false">
                            <to uri="direct:notes"/>
                        </post>
                    </rest>
                    <route>
                        <from uri="direct:notes"/>
                        <setBody>
                            <constant>stored</constant>
                        </setBody>
                    </route>
                </camel>
                """;
        Consumer<CamelContext> setup = context -> {
            context.getRegistry().bind("seda-rest", new SedaRestConsumerFactory());
            context.getRestConfiguration().setComponent("seda-rest");
            context.getRestConfiguration().setInlineRoutes(false);
            // validation for all the rest services, the verb turns it off
            context.getRestConfiguration().setClientRequestValidation(true);
        };
        Function<CamelContext, String> call = context -> {
            Exchange reply = context.createProducerTemplate().request("seda:post-api-notes", e -> {
                e.getMessage().setHeader(Exchange.CONTENT_TYPE, "text/plain");
                e.getMessage().setBody("a note");
            });
            return reply.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, "200", String.class) + " "
                   + reply.getMessage().getBody(String.class);
        };

        assertEquals("200 stored", run(xml, setup, call));
        String dumped = dump(xml, setup);
        assertEquals("200 stored", run(dumped, setup, call), dumped);
        assertTrue(dumped.contains("clientRequestValidation=\"false\""), dumped);
    }

    @Test
    void onExceptionRedeliveryDelayUnderErrorHandler() throws Exception {
        // the onException redelivers after 1000 ms (the documented default), the error handler after 30 s: an option
        // of the onException that is not set takes the value of the error handler
        String xml = """
                <camel>
                    <route>
                        <errorHandler>
                            <defaultErrorHandler>
                                <redeliveryPolicy maximumRedeliveries="3" redeliveryDelay="30000"/>
                            </defaultErrorHandler>
                        </errorHandler>
                        <from uri="direct:start"/>
                        <onException>
                            <exception>java.lang.IllegalStateException</exception>
                            <redeliveryPolicy maximumRedeliveries="1" redeliveryDelay="1000"/>
                            <handled>
                                <constant>true</constant>
                            </handled>
                            <setBody>
                                <constant>handled</constant>
                            </setBody>
                        </onException>
                        <throwException exceptionType="java.lang.IllegalStateException" message="Forced"/>
                    </route>
                </camel>
                """;
        Consumer<CamelContext> setup = context -> context.getShutdownStrategy().setTimeout(1);
        Function<CamelContext, String> call = context -> {
            try {
                return context.createProducerTemplate().asyncRequestBody("direct:start", "x", String.class)
                        .get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                return "not redelivered within 10 seconds";
            } catch (Exception e) {
                return e.toString();
            }
        };

        assertEquals("handled", run(xml, setup, call));
        String dumped = dump(xml, setup);
        assertEquals("handled", run(dumped, setup, call), dumped);
    }

    private static String run(String xml, Consumer<CamelContext> setup, Function<CamelContext, String> call)
            throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            setup.accept(context);
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("routes.xml", xml));
            context.start();
            return call.apply(context);
        }
    }

    /** Loads the routes and rests, and dumps them back as one XML document. */
    private static String dump(String xml, Consumer<CamelContext> setup) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            setup.accept(context);
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("routes.xml", xml));
            ModelCamelContext model = (ModelCamelContext) context;
            StringBuilder sb = new StringBuilder("<camel>");
            if (!model.getRestDefinitions().isEmpty()) {
                RestsDefinition rests = new RestsDefinition();
                rests.getRests().addAll(model.getRestDefinitions());
                sb.append(body(new LwModelToXMLDumper().dumpModelAsXml(context, rests), "rests"));
            }
            RoutesDefinition routes = new RoutesDefinition();
            model.getRouteDefinitions().stream().filter(r -> !Boolean.TRUE.equals(r.isRest()))
                    .forEach(routes.getRoutes()::add);
            sb.append(body(new LwModelToXMLDumper().dumpModelAsXml(context, routes), "routes"));
            return sb.append("</camel>").toString();
        }
    }

    /** The children of the root element of a dump. */
    private static String body(String dump, String root) {
        int start = dump.indexOf('>', dump.indexOf("<" + root)) + 1;
        int end = dump.lastIndexOf("</" + root + ">");
        return dump.substring(start, end);
    }
}
