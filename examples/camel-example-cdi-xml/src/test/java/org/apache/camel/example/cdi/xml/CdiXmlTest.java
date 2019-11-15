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
package org.apache.camel.example.cdi.xml;

import java.util.concurrent.TimeUnit;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.spi.CamelEvent.CamelContextStartingEvent;
import org.apache.camel.test.cdi.CamelCdiRunner;
import org.apache.camel.test.cdi.Order;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

@RunWith(CamelCdiRunner.class)
public class CdiXmlTest {

    @Named
    @Inject
    private Endpoint neo;

    @Inject
    private ProducerTemplate prompt;

    void pipeMatrixStream(@Observes CamelContextStartingEvent event,
                          ModelCamelContext context) throws Exception {
        RouteReifier
            .adviceWith(context.getRouteDefinition("matrix"), context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() {
                    weaveAddLast().to("mock:matrix");
                }
            });
    }

    static class RescueMission extends RouteBuilder {

        @Override
        public void configure() {
            from("stub:rescue").routeId("rescue mission").to("mock:zion");
        }
    }

    @Test
    @Order(1)
    public void takeTheBluePill(@Uri("mock:matrix") MockEndpoint matrix) throws InterruptedException {
        matrix.expectedMessageCount(1);
        matrix.expectedBodiesReceived("Matrix » Take the blue pill!");

        prompt.sendBody(neo, "Take the blue pill!");

        assertIsSatisfied(2L, TimeUnit.SECONDS, matrix);
    }

    @Test
    @Order(2)
    public void takeTheRedPill(@Uri("mock:zion") MockEndpoint zion) throws InterruptedException {
        zion.expectedMessageCount(1);
        zion.expectedHeaderReceived("location", "matrix");

        prompt.sendBody(neo, "Take the red pill!");

        assertIsSatisfied(2L, TimeUnit.SECONDS, zion);
    }

    @Test
    @Order(3)
    public void verifyRescue(CamelContext context) {
        Awaitility.await("Neo is still in the matrix!")
            .atMost(5, TimeUnit.SECONDS)
            .until(() -> ServiceStatus.Stopped.equals(context.getRouteController().getRouteStatus("terminal")));
    }
}
