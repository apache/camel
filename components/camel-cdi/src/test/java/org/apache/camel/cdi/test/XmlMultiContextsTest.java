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
package org.apache.camel.cdi.test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.cdi.ImportResource;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.bean.SecondNamedCamelContextRoute;
import org.apache.camel.component.mock.MockEndpoint;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.cdi.expression.ExchangeExpression.fromCamelContext;
import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
@ImportResource("imported-context.xml")
@Deprecated
public class XmlMultiContextsTest {

    @Inject
    @ContextName("first")
    private CamelContext firstCamelContext;

    @Inject
    @ContextName("first")
    @Uri("direct:inbound")
    private ProducerTemplate firstInbound;

    @Inject
    @ContextName("first")
    @Uri("mock:outbound")
    private MockEndpoint firstOutbound;

    @Inject
    @ContextName("second")
    private CamelContext secondCamelContext;

    @Inject
    @ContextName("second")
    @Uri("direct:in")
    private ProducerTemplate secondInbound;

    @Inject
    @ContextName("second")
    @Uri("mock:outbound")
    private MockEndpoint secondOutbound;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test Camel XML
            .addAsResource(
                Paths.get("src/test/resources/camel-context-multiples.xml").toFile(),
                "imported-context.xml")
            // Test class
            .addClass(SecondNamedCamelContextRoute.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void verifyCamelContexts() {
        assertThat("Camel context name is incorrect!",
            firstCamelContext.getName(), is(equalTo("first")));
        assertThat("Camel context name is incorrect!",
            secondCamelContext.getName(), is(equalTo("second")));

        assertThat("Producer template context is incorrect!",
            firstOutbound.getCamelContext().getName(), is(equalTo(firstCamelContext.getName())));
        assertThat("Producer template context is incorrect!",
            secondOutbound.getCamelContext().getName(), is(equalTo(secondCamelContext.getName())));
    }

    @Test
    public void sendMessageToFirstCamelContextInbound() throws InterruptedException {
        firstOutbound.expectedMessageCount(1);
        firstOutbound.expectedBodiesReceived("first-message");
        firstOutbound.expectedHeaderReceived("context", "first");
        firstOutbound.message(0).exchange().matches(fromCamelContext("first"));

        firstInbound.sendBody("first-message");

        assertIsSatisfied(2L, TimeUnit.SECONDS, firstOutbound);
    }

    @Test
    public void sendMessageToSecondCamelContextInbound() throws InterruptedException {
        secondOutbound.expectedMessageCount(1);
        secondOutbound.expectedBodiesReceived("second-message");
        firstOutbound.expectedHeaderReceived("context", "second");
        secondOutbound.message(0).exchange().matches(fromCamelContext("second"));

        secondInbound.sendBody("message");

        assertIsSatisfied(2L, TimeUnit.SECONDS, secondOutbound);
    }

    @ContextName("second")
    private static class TestRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("direct:out").setHeader("context").constant("second").to("mock:outbound");
        }
    }
}
