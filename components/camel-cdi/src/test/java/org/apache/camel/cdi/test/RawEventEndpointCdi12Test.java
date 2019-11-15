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

import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.CdiEventEndpoint;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;

@RunWith(Arquillian.class)
public class RawEventEndpointCdi12Test {

    @Inject
    private MockEndpoint consumed;

    @Inject
    private MockEndpoint produced;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test classes
            .addClasses(RawEventRoute.class, RawEventObserver.class)
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Before
    public void resetMock() {
        consumed.reset();
    }

    @Test
    public void sendEventToConsumer(Event<Object> event) throws InterruptedException {
        consumed.expectedMessageCount(1);
        consumed.expectedBodiesReceived("test");

        event.select(String.class).fire("test");

        assertIsSatisfied(2L, TimeUnit.SECONDS, consumed);
    }

    @Test
    public void sendMessageToProducer(@Uri("direct:produce") ProducerTemplate producer) throws InterruptedException {
        long random =  Math.round(Math.random() * Long.MAX_VALUE);
        produced.expectedMessageCount(1);
        produced.expectedBodiesReceived(random);
        consumed.expectedMessageCount(1);
        consumed.expectedBodiesReceived(random);

        producer.sendBody(random);

        assertIsSatisfied(2L, TimeUnit.SECONDS, consumed, produced);
    }
}

class RawEventRoute extends RouteBuilder {

    @Inject
    private CdiEventEndpoint rawEventEndpoint;

    @Override
    public void configure() {
        from(rawEventEndpoint).to("mock:consumed");
        from("direct:produce").to(rawEventEndpoint);
    }
}

@ApplicationScoped
class RawEventObserver {

    void collectEvents(@Observes long event, @Uri("mock:produced") ProducerTemplate producer) {
        producer.sendBody(event);
    }
}
