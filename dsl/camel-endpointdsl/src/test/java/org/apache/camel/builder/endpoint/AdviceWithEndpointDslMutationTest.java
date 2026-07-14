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
package org.apache.camel.builder.endpoint;

import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ModelToXMLDumper;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that calling {@code adviceWith()} with {@code logXml=true} (the default) does not permanently mutate the
 * live {@link RouteDefinition} by destroying the {@code EndpointConsumerBuilder}/{@code EndpointProducerBuilder}
 * references on {@link FromDefinition}/{@link SendDefinition}.
 *
 * <p>
 * Before the fix, {@code LwModelToXMLDumper.resolveEndpointDslUris()} called {@code from.setUri()} which internally
 * calls {@code clear()}, permanently nulling the {@code endpointConsumerBuilder} and replacing it with a plain URI
 * string that may contain only a {@code hash=XXXXXXXX} parameter for non-primitive endpoint properties. This caused
 * Camel to ignore pre-configured beans (e.g. AWS S3 clients) and fall back to default (unconfigured) clients.
 */
public class AdviceWithEndpointDslMutationTest extends BaseEndpointDslTest {

    private final ExceptionHandler myExceptionHandler = new ExceptionHandler() {
        @Override
        public void handleException(Throwable exception) {
        }

        @Override
        public void handleException(String message, Throwable exception) {
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
        }
    };

    @Test
    public void testDumpModelAsXmlDoesNotDestroyEndpointConsumerBuilder() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);
        FromDefinition from = route.getInput();

        // before dump: the builder must be set (not yet resolved to a plain URI)
        assertNotNull(from.getEndpointConsumerBuilder(),
                "endpointConsumerBuilder should be set before dumpModelAsXml");
        assertNull(from.getUri(),
                "uri should be null before dumpModelAsXml (endpoint is held as a builder)");

        // directly invoke the XML dumper — this is what adviceWith calls internally for logging
        ModelToXMLDumper dumper = PluginHelper.getModelToXMLDumper(context.getCamelContextExtension());
        assertNotNull(dumper, "LwModelToXMLDumper must be on the classpath");
        dumper.dumpModelAsXml(context, route);

        // after dump: the builder must still be intact — the dump must be non-destructive
        assertNotNull(from.getEndpointConsumerBuilder(),
                "endpointConsumerBuilder must survive dumpModelAsXml — it was permanently destroyed before the fix");
        assertNull(from.getUri(),
                "uri must remain null after dumpModelAsXml — it was set to a 'hash=XXXX' URI before the fix");
    }

    @Test
    public void testAdviceWithDoesNotDestroyEndpointConsumerBuilder() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);

        // before adviceWith: the builder must be set (not yet resolved to a plain URI)
        FromDefinition from = route.getInput();
        assertNotNull(from.getEndpointConsumerBuilder(),
                "endpointConsumerBuilder should be set before adviceWith");
        assertNull(from.getUri(),
                "uri should be null before adviceWith (endpoint is held as a builder)");

        // adviceWith with logXml=true (the default) — this triggers the XML dump that used to mutate the route
        AdviceWith.adviceWith(context, "test-route", a -> a.weaveAddLast().to("mock:advised"));

        // after adviceWith: the builder must still be intact
        assertNotNull(from.getEndpointConsumerBuilder(),
                "endpointConsumerBuilder must survive adviceWith — it was permanently destroyed before the fix");
        assertNull(from.getUri(),
                "uri must remain null after adviceWith — it was set to a 'hash=XXXX' URI before the fix");
    }

    @Test
    public void testAdviceWithDoesNotDestroyEndpointProducerBuilder() throws Exception {
        RouteDefinition route = context.getRouteDefinitions().get(0);

        // find the seda("sink") ToDefinition — built via EndpointDSL, so endpointProducerBuilder is set
        ToDefinition toDef = route.getOutputs().stream()
                .filter(ToDefinition.class::isInstance)
                .map(ToDefinition.class::cast)
                .filter(d -> d.getEndpointProducerBuilder() != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No ToDefinition with an endpointProducerBuilder found"));

        assertNotNull(toDef.getEndpointProducerBuilder(),
                "endpointProducerBuilder should be set before adviceWith");
        assertNull(toDef.getUri(),
                "uri should be null before adviceWith");

        AdviceWith.adviceWith(context, "test-route", a -> a.weaveAddLast().to("mock:advised2"));

        assertNotNull(toDef.getEndpointProducerBuilder(),
                "endpointProducerBuilder must survive adviceWith — it was permanently destroyed before the fix");
        assertNull(toDef.getUri(),
                "uri must remain null after adviceWith");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() {
                // Use a non-primitive ExceptionHandler property so that getRawUri() without a CamelContext
                // would produce "timer://tick?hash=XXXXXXXX" — reproducing the case where the URI carries
                // only a hash parameter and the original bean reference is lost after mutation.
                from(timer("tick")
                        .period(10_000)
                        .repeatCount(1)
                        .advanced().exceptionHandler(myExceptionHandler))
                        .routeId("test-route")
                        .autoStartup(false)
                        .to(seda("sink"))
                        .to("mock:result");
            }
        };
    }
}
