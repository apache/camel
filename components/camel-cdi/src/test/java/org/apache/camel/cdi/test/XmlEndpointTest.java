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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.Endpoint;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ImportResource;
import org.apache.camel.cdi.Uri;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.seda.SedaEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Arquillian.class)
@ImportResource("imported-context.xml")
public class XmlEndpointTest {

    @Inject
    @Uri("seda:inbound")
    private ProducerTemplate inbound;

    @Inject
    @Uri("mock:outbound")
    private MockEndpoint outbound;

    @Inject
    @Named("bar")
    private Endpoint endpoint;

    @Produces
    @Named("queue")
    private static <T> BlockingQueue<T> queue() {
        return new MyBlockingQueue<>();
    }

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test Camel XML
            .addAsResource(
                Paths.get("src/test/resources/camel-context-endpoint.xml").toFile(),
                "imported-context.xml")
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    @Ignore("@ApplicationScoped bean proxy cannot be casted to endpoint implementation")
    public void verifyXmlEndpoint() {
        assertThat("Endpoint type is incorrect!", endpoint, is(instanceOf(SedaEndpoint.class)));
        SedaEndpoint seda = (SedaEndpoint) endpoint;
        assertThat("Endpoint queue is incorrect!",
            seda.getQueue(), is(instanceOf(MyBlockingQueue.class)));
        assertThat("Endpoint concurrent consumers count is incorrect!",
            seda.getConcurrentConsumers(), is(equalTo(10)));
    }

    @Test
    public void sendMessageToInbound() throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.expectedBodiesReceived("message");

        inbound.sendBody("message");

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);
    }
}

class MyBlockingQueue<E> extends LinkedBlockingQueue<E> {
}
