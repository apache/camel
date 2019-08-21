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
package org.apache.camel.processor.onexception;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaEndpoint;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

public class ContextScopedOnExceptionLoadBalancerStopRouteTest extends ContextTestSupport {

    @Test
    public void testOk() throws Exception {
        getMockEndpoint("mock:error").expectedMessageCount(0);
        getMockEndpoint("mock:start").expectedBodiesReceived("World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:exception").expectedMessageCount(0);

        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testError() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:start").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:result").expectedMessageCount(0);
        getMockEndpoint("mock:exception").expectedBodiesReceived("Kaboom");

        template.sendBody("direct:start", "Kaboom");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testErrorOk() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:start").expectedBodiesReceived("Kaboom", "World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:exception").expectedBodiesReceived("Kaboom");

        template.sendBody("direct:start", "Kaboom");
        template.sendBody("direct:start", "World");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testErrorOkError() throws Exception {
        getMockEndpoint("mock:error").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:start").expectedBodiesReceived("Kaboom", "World", "Kaboom");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye World");
        getMockEndpoint("mock:exception").expectedBodiesReceived("Kaboom", "Kaboom");

        template.sendBody("direct:start", "Kaboom");
        template.sendBody("direct:start", "World");

        // give time for route to stop
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("errorRoute")));

        template.sendBody("direct:start", "Kaboom");

        assertMockEndpointsSatisfied();

        // should be 1 on the seda queue
        SedaEndpoint seda = getMandatoryEndpoint("seda:error", SedaEndpoint.class);
        SedaEndpoint seda2 = getMandatoryEndpoint("seda:error2", SedaEndpoint.class);
        int size = seda.getQueue().size();
        int size2 = seda2.getQueue().size();
        assertTrue("There should be 1 exchange on the seda or seda2 queue", size == 1 || size2 == 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).handled(true).loadBalance().roundRobin().to("seda:error", "seda:error2").end().to("mock:exception");

                from("direct:start").to("mock:start").choice().when(body().contains("Kaboom")).throwException(new IllegalArgumentException("Forced")).otherwise()
                    .transform(body().prepend("Bye ")).to("mock:result");

                from("seda:error").routeId("errorRoute").to("controlbus:route?action=stop&routeId=errorRoute&async=true").to("mock:error");
            }
        };
    }
}
