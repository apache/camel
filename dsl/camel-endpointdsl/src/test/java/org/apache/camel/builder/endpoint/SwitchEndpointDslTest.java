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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SwitchDefinition;
import org.apache.camel.xml.jaxb.JaxbHelper;
import org.apache.camel.xml.jaxb.JaxbModelToXMLDumper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SwitchEndpointDslTest extends BaseEndpointDslTest {
    private final BlockingQueue<Exchange> billing = new LinkedBlockingQueue<>();
    private final BlockingQueue<Exchange> technical = new LinkedBlockingQueue<>();
    private final BlockingQueue<Exchange> review = new LinkedBlockingQueue<>();

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void buildersSurviveCopyAndJaxbDumpAndResolveObjectOptions() throws Exception {
        RouteDefinition route = context.getRouteDefinition("tickets");
        SwitchDefinition sw = (SwitchDefinition) route.getOutputs().get(0);
        SwitchDefinition copy = sw.copyDefinition();
        assertNotSame(sw.getCases().get(0), copy.getCases().get(0));
        assertNotSame(sw.getOtherwiseDefinition(), copy.getOtherwiseDefinition());
        assertSame(sw.getCases().get(0).getEndpointProducerBuilder(), copy.getCases().get(0).getEndpointProducerBuilder());
        assertSame(sw.getCases().get(1).getEndpointProducerBuilder(), copy.getCases().get(1).getEndpointProducerBuilder());
        assertSame(sw.getOtherwise().getEndpointProducerBuilder(), copy.getOtherwise().getEndpointProducerBuilder());
        copy.getCases().get(0).setUri("mock:copy");
        copy.getOtherwise().setUri("mock:copyFallback");
        assertNull(copy.getCases().get(0).getEndpointProducerBuilder());
        assertNull(copy.getOtherwise().getEndpointProducerBuilder());

        // Options added after the Switch was configured must also be visible to JAXB.
        for (var c : sw.getCases()) {
            c.getEndpointProducerBuilder().doSetProperty("blockWhenFull", true);
        }
        sw.getOtherwise().getEndpointProducerBuilder().doSetProperty("blockWhenFull", true);
        String xml = new JaxbModelToXMLDumper().dumpModelAsXml(context, route);
        try (var input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            RouteDefinition restored = JaxbHelper.loadRoutesDefinition(context, input).getRoutes().get(0);
            SwitchDefinition dumped = (SwitchDefinition) restored.getOutputs().get(0);
            assertTrue(dumped.getCases().get(0).getUri().contains("blockWhenFull=true"));
            assertTrue(dumped.getCases().get(1).getUri().contains("blockWhenFull=true"));
            assertTrue(dumped.getOtherwise().getUri().contains("blockWhenFull=true"));
        }
        assertEquals("seda://billing", sw.getCases().get(0).getUri().split("\\?")[0]);
        context.start();
        getMockEndpoint("mock:billing").expectedBodiesReceived("invoice");
        getMockEndpoint("mock:technical").expectedBodiesReceived("outage");
        getMockEndpoint("mock:review").expectedBodiesReceived("other", "missing");
        template.sendBodyAndHeader("direct:tickets", "invoice", "department", "BILLING");
        template.sendBodyAndHeader("direct:tickets", "outage", "department", "technical");
        template.sendBodyAndHeader("direct:tickets", "other", "department", "other");
        template.sendBody("direct:tickets", "missing");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new EndpointRouteBuilder() {
            @Override
            public void configure() {
                from(direct("tickets")).routeId("tickets")
                        .doSwitch(header("department"))
                            .doCase("billing", seda("billing").advanced().queue(billing))
                            .doCase("technical").to(seda("technical").advanced().queue(technical))
                            .otherwise(seda("review").advanced().queue(review))
                        .end();
                from(seda("billing").advanced().queue(billing)).to(mock("billing"));
                from(seda("technical").advanced().queue(technical)).to(mock("technical"));
                from(seda("review").advanced().queue(review)).to(mock("review"));
            }
        };
    }
}
