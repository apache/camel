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
package org.apache.camel.cdi.test;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.camel.CamelException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.cdi.CdiCamelExtension;
import org.apache.camel.cdi.ImportResource;
import org.apache.camel.cdi.Uri;
import org.apache.camel.cdi.rule.LogEventVerifier;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import static org.apache.camel.cdi.rule.LogEventMatcher.logEvent;
import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(Arquillian.class)
@ImportResource("imported-context.xml")
public class XmlErrorHandlerLoggingTest {

    @ClassRule
    public static TestRule verifier = new LogEventVerifier() {
        @Override
        protected void verify() {
            assertThat("Log messages not found!", getEvents(),
                containsInRelativeOrder(
                    logEvent()
                        .withLevel("INFO")
                        .withMessage(containsString("Camel CDI is starting Camel context [test]")),
                    logEvent()
                        .withLevel("WARN")
                        .withLogger("error")
                        .withMessage(containsString(
                            "Exhausted after delivery attempt: 1 "
                                + "caught: org.apache.camel.CamelException: failure message!")),
                    logEvent()
                        .withLevel("INFO")
                        .withMessage(containsString("Camel CDI is stopping Camel context [test]"))
                )
            );
        }
    };

    @Named
    @Produces
    private Exception failure = new CamelException("failure message!");

    @Inject
    @Uri("direct:inbound")
    private ProducerTemplate inbound;

    @Inject
    @Uri("mock:outbound")
    private MockEndpoint outbound;

    @Deployment
    public static Archive<?> deployment() {
        return ShrinkWrap.create(JavaArchive.class)
            // Camel CDI
            .addPackage(CdiCamelExtension.class.getPackage())
            // Test Camel XML
            .addAsResource(
                Paths.get("src/test/resources/camel-context-errorHandler-logging.xml").toFile(),
                "imported-context.xml")
            // Bean archive deployment descriptor
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }

    @Test
    public void sendMessageToInbound() throws InterruptedException {
        outbound.expectedMessageCount(1);
        outbound.expectedBodiesReceived("Response to message");

        inbound.sendBody("message");

        assertIsSatisfied(2L, TimeUnit.SECONDS, outbound);
    }

    @Test
    public void sendExceptionToInbound() {
        try {
            inbound.sendBody("exception");
        } catch (Exception exception) {
            assertThat("Exception is incorrect!",
                exception, is(instanceOf(CamelExecutionException.class)));
            assertThat("Exception cause is incorrect!",
                exception.getCause(), is(instanceOf(CamelException.class)));
            assertThat("Exception message is incorrect!",
                exception.getCause().getMessage(), is(equalTo("failure message!")));
            return;
        }
        fail("No exception thrown!");
    }
}
