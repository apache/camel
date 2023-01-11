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
package org.apache.camel.websocket.jsr356;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.Dependent;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.ServerEndpoint;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("TODO: Does not work with some deployment error in embedded JEE server")
@MeecrowaveConfig(scanningPackageIncludes = "org.apache.camel.websocket.jsr356.JSR356ProducerTest$")
public class JSR356ProducerTest extends CamelTestSupport {

    private static LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

    @ConfigurationInject
    protected Meecrowave.Builder configuration;

    protected String testMethodName;

    @Produce("direct:ensureServerModeSendsProperly")
    private ProducerTemplate serverProducer;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        super.beforeEach(context);
        testMethodName = context.getRequiredTestMethod().getName();
    }

    @Test
    public void ensureServerModeSendsProperly() throws Exception {
        final String body = getClass().getName() + "#" + testMethodName;
        serverProducer.sendBody(body);
        assertEquals(body, messages.poll(10, TimeUnit.SECONDS));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:ensureServerModeSendsProperly").id("camel_consumer_acts_as_client").convertBodyTo(String.class)
                        .to("websocket-jsr356://ws://localhost:" + configuration.getHttpPort()
                            + "/existingserver?sessionCount=5");
            }
        };
    }

    @Dependent
    @ServerEndpoint("/existingserver")
    public static class ExistingServerEndpoint {
        @OnMessage
        public void onMessage(final String message) {
            messages.add(message);
        }
    }
}
